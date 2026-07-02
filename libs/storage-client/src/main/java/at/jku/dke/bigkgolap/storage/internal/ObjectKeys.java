package at.jku.dke.bigkgolap.storage.internal;

import java.util.regex.Pattern;

public final class ObjectKeys {

  public static final Pattern SCHEMA_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_-]*$");

  private ObjectKeys() {
    // utility class
  }

  public static String objectKey(String schemaId, String storedName) {
    validate(schemaId);
    if (storedName == null || storedName.isBlank()) {
      throw new IllegalArgumentException("storedName must not be blank");
    }
    if (storedName.startsWith("/")) {
      throw new IllegalArgumentException("storedName must not start with '/'");
    }
    return schemaId + "/" + storedName;
  }

  public static String schemaPrefix(String schemaId) {
    validate(schemaId);
    return schemaId + "/";
  }

  public static void validate(String schemaId) {
    if (schemaId == null || !SCHEMA_ID_PATTERN.matcher(schemaId).matches()) {
      throw new IllegalArgumentException(
          "Invalid schema id '%s'; must match %s".formatted(schemaId, SCHEMA_ID_PATTERN.pattern()));
    }
  }
}
