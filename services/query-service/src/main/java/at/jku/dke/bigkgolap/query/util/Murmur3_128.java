package at.jku.dke.bigkgolap.query.util;

/**
 * MurmurHash3 x64 128-bit, seed 0 (public-domain algorithm by Austin Appleby). Non-cryptographic;
 * used only to dedup emitted quads with negligible collision probability.
 */
public final class Murmur3_128 {

  private static final long C1 = 0x87c37b91114253d5L;
  private static final long C2 = 0x4cf5ad432745937fL;

  private Murmur3_128() {}

  /** Returns the 128-bit hash as {@code {h1, h2}}. */
  public static long[] hash(byte[] data) {
    int len = data.length;
    int nblocks = len >> 4;
    long h1 = 0L;
    long h2 = 0L;

    for (int i = 0; i < nblocks; i++) {
      int base = i << 4;
      long k1 = getLong(data, base);
      long k2 = getLong(data, base + 8);
      k1 *= C1;
      k1 = Long.rotateLeft(k1, 31);
      k1 *= C2;
      h1 ^= k1;
      h1 = Long.rotateLeft(h1, 27);
      h1 += h2;
      h1 = h1 * 5 + 0x52dce729L;
      k2 *= C2;
      k2 = Long.rotateLeft(k2, 33);
      k2 *= C1;
      h2 ^= k2;
      h2 = Long.rotateLeft(h2, 31);
      h2 += h1;
      h2 = h2 * 5 + 0x38495ab5L;
    }

    long k1 = 0L;
    long k2 = 0L;
    int tail = nblocks << 4;
    switch (len & 15) {
      case 15:
        k2 ^= (long) (data[tail + 14] & 0xff) << 48;
        // fall through
      case 14:
        k2 ^= (long) (data[tail + 13] & 0xff) << 40;
        // fall through
      case 13:
        k2 ^= (long) (data[tail + 12] & 0xff) << 32;
        // fall through
      case 12:
        k2 ^= (long) (data[tail + 11] & 0xff) << 24;
        // fall through
      case 11:
        k2 ^= (long) (data[tail + 10] & 0xff) << 16;
        // fall through
      case 10:
        k2 ^= (long) (data[tail + 9] & 0xff) << 8;
        // fall through
      case 9:
        k2 ^= (long) (data[tail + 8] & 0xff);
        k2 *= C2;
        k2 = Long.rotateLeft(k2, 33);
        k2 *= C1;
        h2 ^= k2;
        // fall through
      case 8:
        k1 ^= (long) (data[tail + 7] & 0xff) << 56;
        // fall through
      case 7:
        k1 ^= (long) (data[tail + 6] & 0xff) << 48;
        // fall through
      case 6:
        k1 ^= (long) (data[tail + 5] & 0xff) << 40;
        // fall through
      case 5:
        k1 ^= (long) (data[tail + 4] & 0xff) << 32;
        // fall through
      case 4:
        k1 ^= (long) (data[tail + 3] & 0xff) << 24;
        // fall through
      case 3:
        k1 ^= (long) (data[tail + 2] & 0xff) << 16;
        // fall through
      case 2:
        k1 ^= (long) (data[tail + 1] & 0xff) << 8;
        // fall through
      case 1:
        k1 ^= (long) (data[tail] & 0xff);
        k1 *= C1;
        k1 = Long.rotateLeft(k1, 31);
        k1 *= C2;
        h1 ^= k1;
        // fall through
      default:
        break;
    }

    h1 ^= len;
    h2 ^= len;
    h1 += h2;
    h2 += h1;
    h1 = fmix64(h1);
    h2 = fmix64(h2);
    h1 += h2;
    h2 += h1;
    return new long[] {h1, h2};
  }

  private static long getLong(byte[] d, int i) {
    return (d[i] & 0xffL)
        | (d[i + 1] & 0xffL) << 8
        | (d[i + 2] & 0xffL) << 16
        | (d[i + 3] & 0xffL) << 24
        | (d[i + 4] & 0xffL) << 32
        | (d[i + 5] & 0xffL) << 40
        | (d[i + 6] & 0xffL) << 48
        | (d[i + 7] & 0xffL) << 56;
  }

  private static long fmix64(long k) {
    k ^= k >>> 33;
    k *= 0xff51afd7ed558ccdL;
    k ^= k >>> 33;
    k *= 0xc4ceb9fe1a85ec53L;
    k ^= k >>> 33;
    return k;
  }
}
