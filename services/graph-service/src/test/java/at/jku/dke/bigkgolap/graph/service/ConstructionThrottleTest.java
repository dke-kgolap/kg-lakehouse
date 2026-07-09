package at.jku.dke.bigkgolap.graph.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConstructionThrottleTest {

  @Test
  void permitsCountReflectsConfiguredSize() throws Exception {
    ConstructionThrottle throttle = new ConstructionThrottle(2);
    assertThat(throttle.availablePermits()).isEqualTo(2);
    throttle.acquire();
    assertThat(throttle.availablePermits()).isEqualTo(1);
    throttle.release();
    assertThat(throttle.availablePermits()).isEqualTo(2);
  }

  @Test
  void sizeIsFlooredAtOne() {
    assertThat(new ConstructionThrottle(0).availablePermits()).isEqualTo(1);
    assertThat(new ConstructionThrottle(-5).availablePermits()).isEqualTo(1);
  }
}
