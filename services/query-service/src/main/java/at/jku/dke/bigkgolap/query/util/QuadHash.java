package at.jku.dke.bigkgolap.query.util;

import java.nio.charset.StandardCharsets;

/** 128-bit murmur3 hash of a serialized quad/element line, used for cross-context dedup. */
public record QuadHash(long hi, long lo) {
  public static QuadHash of(String quad) {
    long[] h = Murmur3_128.hash(quad.getBytes(StandardCharsets.UTF_8));
    return new QuadHash(h[0], h[1]);
  }
}
