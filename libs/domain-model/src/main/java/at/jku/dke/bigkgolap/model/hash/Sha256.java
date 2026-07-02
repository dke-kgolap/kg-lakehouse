package at.jku.dke.bigkgolap.model.hash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Sha256 {

  private static final int BYTE_MASK = 0xff;
  private static final int NIBBLE_MASK = 0x0f;
  private static final int NIBBLE_BITS = 4;
  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private Sha256() {}

  public static String hex(String input) {
    byte[] bytes;
    try {
      bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
    var sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      int v = b & BYTE_MASK;
      sb.append(HEX[v >>> NIBBLE_BITS]);
      sb.append(HEX[v & NIBBLE_MASK]);
    }
    return sb.toString();
  }
}
