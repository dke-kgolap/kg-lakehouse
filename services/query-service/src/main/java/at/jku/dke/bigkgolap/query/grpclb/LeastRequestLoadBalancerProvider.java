package at.jku.dke.bigkgolap.query.grpclb;

import io.grpc.LoadBalancer;
import io.grpc.LoadBalancerProvider;

/**
 * Registers {@link LeastRequestLoadBalancer} under the policy name {@value #POLICY_NAME} so it can
 * be selected with {@code NettyChannelBuilder.defaultLoadBalancingPolicy(POLICY_NAME)}.
 *
 * <p>grpc-java only ships {@code pick_first}, {@code round_robin}, and {@code
 * outlier_detection_experimental} on the default classpath; {@code least_request} lives in the
 * heavy {@code grpc-xds} artifact. This in-process provider gives a load-aware policy with no new
 * dependency. Registered programmatically from {@code GrpcClientConfig} (robust inside the Spring
 * Boot fat jar, where {@code META-INF/services} discovery is unreliable).
 */
public final class LeastRequestLoadBalancerProvider extends LoadBalancerProvider {

  public static final String POLICY_NAME = "least_request_p2c";

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public int getPriority() {
    return 5;
  }

  @Override
  public String getPolicyName() {
    return POLICY_NAME;
  }

  @Override
  public LoadBalancer newLoadBalancer(LoadBalancer.Helper helper) {
    return new LeastRequestLoadBalancer(helper);
  }
}
