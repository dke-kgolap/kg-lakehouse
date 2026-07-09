package at.jku.dke.bigkgolap.surface.config;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Resolves the query-service <em>headless</em> Service DNS name to its individual pod IPs and hands
 * them out round-robin, one per request.
 *
 * <p>Why this exists: surface reaches query-service over a JDK {@link java.net.http.HttpClient}
 * keepalive connection pool. A ClusterIP Service balances <em>connections</em>, not requests, so
 * the pooled connections pin to whichever pods they first landed on and never re-balance — under
 * load a subset of query pods saturates while the rest sit idle (measured query-tier load CoV ~0.57
 * at 4 replicas, with one pod fully idle). Pointing each request at a specific pod IP, rotating
 * through all resolved pods, spreads requests evenly (the JDK client pools per host:port). This
 * mirrors the graph/inference tiers' headless-Service + gRPC {@code round_robin} fix, applied to
 * the HTTP hop.
 *
 * <p>Requires the query-service Service to be headless ({@code clusterIP: None}) so the DNS name
 * returns every ready pod IP, and a short JVM DNS cache TTL ({@code -Dnetworkaddress.cache.ttl}) so
 * rescaled pods are picked up promptly.
 */
@Component
@Profile("!test")
public class QueryServiceEndpoints {

  /** Re-resolve the pod set at most this often; pods change only on scale/rollout. */
  private static final long TTL_MS = 3000;

  private final String host;
  private final int port;
  private final AtomicInteger cursor = new AtomicInteger();
  private volatile URI[] endpoints = new URI[0];
  private volatile long nextResolveAt = 0L;

  public QueryServiceEndpoints(LakehouseProperties props) {
    this.host = props.services().query().host();
    this.port = props.services().query().httpPort();
  }

  /**
   * The next pod endpoint ({@code http://<pod-ip>:<port>}) round-robin, or {@code null} when no pod
   * is resolved yet — the caller then falls back to the configured Service base URL.
   */
  public URI next() {
    if (System.currentTimeMillis() >= nextResolveAt) {
      resolve();
    }
    URI[] snapshot = endpoints;
    if (snapshot.length == 0) {
      return null;
    }
    return snapshot[Math.floorMod(cursor.getAndIncrement(), snapshot.length)];
  }

  private synchronized void resolve() {
    if (System.currentTimeMillis() < nextResolveAt) {
      return; // another thread refreshed while we waited on the lock
    }
    try {
      InetAddress[] addrs = InetAddress.getAllByName(host);
      URI[] resolved = new URI[addrs.length];
      for (int i = 0; i < addrs.length; i++) {
        resolved[i] = endpointUri(addrs[i], port);
      }
      endpoints = resolved;
    } catch (UnknownHostException e) {
      // Transient DNS failure: keep the previous set rather than dropping all endpoints.
    }
    nextResolveAt = System.currentTimeMillis() + TTL_MS;
  }

  /**
   * Builds an {@code http://host:port} endpoint URI, bracketing IPv6 literals (and stripping any
   * scope id) so the authority stays a valid URI host. An unbracketed IPv6 address parses to a URI
   * with a {@code null} host, which the round-robin interceptor then rewrites to a hostless {@code
   * http:/…}. Package-private for testing.
   */
  static URI endpointUri(InetAddress addr, int port) {
    String host = addr.getHostAddress();
    if (addr instanceof Inet6Address) {
      int scope = host.indexOf('%');
      if (scope >= 0) {
        host = host.substring(0, scope);
      }
      host = "[" + host + "]";
    }
    return URI.create("http://" + host + ":" + port);
  }
}
