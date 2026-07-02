package at.jku.dke.bigkgolap.query.grpclb;

import io.grpc.ClientStreamTracer;
import io.grpc.ConnectivityState;
import io.grpc.ConnectivityStateInfo;
import io.grpc.EquivalentAddressGroup;
import io.grpc.LoadBalancer;
import io.grpc.Metadata;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Power-of-two-choices (P2C) least-request load balancer.
 *
 * <p>Why this exists: against a headless Service with many backend pods, gRPC's built-in {@code
 * round_robin} distributes <em>picks</em> uniformly but, with query-service's wide blocking fan-out
 * (one RPC per context, 32 threads sharing one channel), a couple of backends were found to take
 * ~3x the load and saturate at their CPU limit while the rest sat idle — and the skew was
 * structural (the same pods stayed hot across full channel rebuilds), so round_robin could not be
 * coaxed into balancing. The {@code CountDownLatch} fan-out barrier then makes the hottest pod gate
 * every query.
 *
 * <p>This balancer routes by <em>load</em> instead of position: it samples two ready backends at
 * random and sends the request to whichever currently has fewer outstanding streams (tracked via a
 * {@link ClientStreamTracer} per backend). Because the fan-out uses blocking stubs,
 * outstanding-stream count is a faithful proxy for per-backend load, so a saturating pod is shed
 * automatically.
 *
 * <p>Structurally mirrors gRPC's own {@code RoundRobinLoadBalancer} (subchannel-per-EAG lifecycle,
 * state aggregation) but swaps the round-robin picker for a P2C least-request picker. All
 * subchannel bookkeeping runs in the channel's synchronization context; only the {@link
 * AtomicInteger} counters are touched from RPC threads.
 */
final class LeastRequestLoadBalancer extends LoadBalancer {

  private final Helper helper;
  private final Map<EquivalentAddressGroup, Backend> backends = new HashMap<>();
  private ConnectivityState currentState;

  LeastRequestLoadBalancer(Helper helper) {
    this.helper = helper;
  }

  @Override
  public Status acceptResolvedAddresses(ResolvedAddresses resolvedAddresses) {
    List<EquivalentAddressGroup> servers = resolvedAddresses.getAddresses();
    if (servers.isEmpty()) {
      Status error =
          Status.UNAVAILABLE.withDescription(
              "least_request_p2c: name resolution returned no servers");
      handleNameResolutionError(error);
      return error;
    }
    Set<EquivalentAddressGroup> latest = new HashSet<>();
    for (EquivalentAddressGroup eag : servers) {
      EquivalentAddressGroup key = stripAttrs(eag);
      latest.add(key);
      if (!backends.containsKey(key)) {
        createBackend(key);
      }
    }
    List<EquivalentAddressGroup> stale = new ArrayList<>();
    for (EquivalentAddressGroup key : backends.keySet()) {
      if (!latest.contains(key)) {
        stale.add(key);
      }
    }
    for (EquivalentAddressGroup key : stale) {
      backends.remove(key).subchannel.shutdown();
    }
    updateBalancingState();
    return Status.OK;
  }

  private void createBackend(EquivalentAddressGroup eag) {
    Subchannel subchannel =
        helper.createSubchannel(CreateSubchannelArgs.newBuilder().setAddresses(eag).build());
    Backend backend = new Backend(subchannel);
    backends.put(eag, backend);
    subchannel.start(stateInfo -> onSubchannelState(backend, stateInfo));
    subchannel.requestConnection();
  }

  private void onSubchannelState(Backend backend, ConnectivityStateInfo stateInfo) {
    if (backends.get(stripAttrs(backend.subchannel.getAddresses())) != backend) {
      return; // subchannel already removed; ignore late callback
    }
    ConnectivityState state = stateInfo.getState();
    if (state == ConnectivityState.IDLE) {
      backend.subchannel.requestConnection();
    }
    backend.state = state;
    updateBalancingState();
  }

  private void updateBalancingState() {
    List<Backend> ready = new ArrayList<>();
    boolean anyPending = false;
    for (Backend b : backends.values()) {
      if (b.state == ConnectivityState.READY) {
        ready.add(b);
      } else if (b.state == ConnectivityState.CONNECTING || b.state == ConnectivityState.IDLE) {
        anyPending = true;
      }
    }
    if (!ready.isEmpty()) {
      currentState = ConnectivityState.READY;
      helper.updateBalancingState(
          ConnectivityState.READY, new LeastRequestPicker(Collections.unmodifiableList(ready)));
    } else if (anyPending) {
      currentState = ConnectivityState.CONNECTING;
      helper.updateBalancingState(
          ConnectivityState.CONNECTING, new FixedPicker(PickResult.withNoResult()));
    } else {
      currentState = ConnectivityState.TRANSIENT_FAILURE;
      helper.updateBalancingState(
          ConnectivityState.TRANSIENT_FAILURE,
          new FixedPicker(
              PickResult.withError(
                  Status.UNAVAILABLE.withDescription(
                      "least_request_p2c: all subchannels unavailable"))));
    }
  }

  @Override
  public void handleNameResolutionError(Status error) {
    if (currentState != ConnectivityState.READY) {
      helper.updateBalancingState(
          ConnectivityState.TRANSIENT_FAILURE, new FixedPicker(PickResult.withError(error)));
    }
  }

  @Override
  public void shutdown() {
    for (Backend b : backends.values()) {
      b.subchannel.shutdown();
    }
    backends.clear();
  }

  private static EquivalentAddressGroup stripAttrs(EquivalentAddressGroup eag) {
    return new EquivalentAddressGroup(eag.getAddresses());
  }

  /** Per-backend state: subchannel, live outstanding-stream counter, and its tracer factory. */
  private static final class Backend {
    final Subchannel subchannel;
    final AtomicInteger active = new AtomicInteger();
    final ClientStreamTracer.Factory tracerFactory;
    volatile ConnectivityState state = ConnectivityState.IDLE;

    Backend(Subchannel subchannel) {
      this.subchannel = subchannel;
      this.tracerFactory =
          new ClientStreamTracer.Factory() {
            @Override
            public ClientStreamTracer newClientStreamTracer(
                ClientStreamTracer.StreamInfo info, Metadata headers) {
              active.incrementAndGet();
              return new ClientStreamTracer() {
                @Override
                public void streamClosed(Status status) {
                  active.decrementAndGet();
                }
              };
            }
          };
    }
  }

  /** Samples two ready backends and routes to the one with fewer outstanding streams. */
  private static final class LeastRequestPicker extends SubchannelPicker {
    private final List<Backend> ready;

    LeastRequestPicker(List<Backend> ready) {
      this.ready = ready;
    }

    @Override
    public PickResult pickSubchannel(PickSubchannelArgs args) {
      int size = ready.size();
      Backend chosen;
      if (size == 1) {
        chosen = ready.get(0);
      } else {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int i = rnd.nextInt(size);
        int j = rnd.nextInt(size - 1);
        if (j >= i) {
          j++;
        }
        Backend a = ready.get(i);
        Backend b = ready.get(j);
        chosen = a.active.get() <= b.active.get() ? a : b;
      }
      return PickResult.withSubchannel(chosen.subchannel, chosen.tracerFactory);
    }
  }

  /**
   * Trivial picker that always returns a fixed result (CONNECTING buffer / TRANSIENT_FAILURE
   * error).
   */
  private static final class FixedPicker extends SubchannelPicker {
    private final PickResult result;

    FixedPicker(PickResult result) {
      this.result = result;
    }

    @Override
    public PickResult pickSubchannel(PickSubchannelArgs args) {
      return result;
    }
  }
}
