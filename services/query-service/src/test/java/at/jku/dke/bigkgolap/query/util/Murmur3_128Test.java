package at.jku.dke.bigkgolap.query.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Murmur3_128Test {

  private static long[] h(String s) {
    return Murmur3_128.hash(s.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void emptyInputIsZeroZero() {
    // MurmurHash3 x64 128, seed 0, empty input = {0, 0} (a known reference value).
    assertThat(Murmur3_128.hash(new byte[0])).containsExactly(0L, 0L);
  }

  @Test
  void matchesKnownVectorForHello() {
    // Canonical MurmurHash3 x64 128 (seed 0) value for "hello", locking the algorithm against
    // edits.
    assertThat(h("hello")).containsExactly(-3758069500696749310L, 6565844092913065241L);
  }

  @Test
  void isDeterministic() {
    assertThat(h("<a> <b> <c> <g> .")).isEqualTo(h("<a> <b> <c> <g> ."));
  }

  @Test
  void distinctInputsGiveDistinctHashes() {
    // 1000 distinct strings must all hash distinctly (no collision at this scale).
    var seen = new java.util.HashSet<java.util.List<Long>>();
    for (int i = 0; i < 1000; i++) {
      long[] x = h("<urn:s" + i + "> <urn:p> <urn:o> <urn:g> .");
      seen.add(java.util.List.of(x[0], x[1]));
    }
    assertThat(seen).hasSize(1000);
  }
}
