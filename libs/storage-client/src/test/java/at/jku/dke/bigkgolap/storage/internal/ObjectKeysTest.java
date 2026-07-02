package at.jku.dke.bigkgolap.storage.internal;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ObjectKeysTest {

  @Test
  void objectKey_composesSchemaIdAndStoredNameWithASlash() {
    Assertions.assertThat(ObjectKeys.objectKey("atm", "abc.xml")).isEqualTo("atm/abc.xml");
  }

  @Test
  void schemaPrefix_appendsATrailingSlash() {
    Assertions.assertThat(ObjectKeys.schemaPrefix("weather")).isEqualTo("weather/");
  }

  @Test
  void validate_rejectsIdsWithUppercaseChars() {
    Assertions.assertThatThrownBy(() -> ObjectKeys.validate("ATM"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void validate_rejectsIdsWithSlashes() {
    Assertions.assertThatThrownBy(() -> ObjectKeys.validate("a/b"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void validate_rejectsEmptyIds() {
    Assertions.assertThatThrownBy(() -> ObjectKeys.validate(""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void objectKey_rejectsStoredNameStartingWithSlash() {
    Assertions.assertThatThrownBy(() -> ObjectKeys.objectKey("atm", "/abc.xml"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
