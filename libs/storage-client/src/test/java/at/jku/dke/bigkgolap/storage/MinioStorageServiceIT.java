package at.jku.dke.bigkgolap.storage;

import at.jku.dke.bigkgolap.storage.support.MinioTestBase;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class MinioStorageServiceIT extends MinioTestBase {

  @Test
  void storeAndLoad_roundTrip() throws Exception {
    var payload = "hello minio".getBytes(StandardCharsets.UTF_8);
    service.store("atm", "f1.xml", new ByteArrayInputStream(payload), payload.length);
    var read = service.load("atm", "f1.xml").readAllBytes();
    Assertions.assertThat(read).isEqualTo(payload);
  }

  @Test
  void exists_reflectsStoreAndDelete() {
    Assertions.assertThat(service.exists("atm", "x")).isFalse();
    service.store("atm", "x", new ByteArrayInputStream("y".getBytes()), 1);
    Assertions.assertThat(service.exists("atm", "x")).isTrue();
    service.delete("atm", "x");
    Assertions.assertThat(service.exists("atm", "x")).isFalse();
  }

  @Test
  void load_missingObjectThrowsStorageNotFoundException() {
    Assertions.assertThatThrownBy(() -> service.load("atm", "missing"))
        .isInstanceOf(StorageNotFoundException.class);
  }

  @Test
  void deleteAllForSchema_onlyRemovesThatSchemasPrefix() {
    service.store("atm", "a", new ByteArrayInputStream("a".getBytes()), 1);
    service.store("atm", "b", new ByteArrayInputStream("b".getBytes()), 1);
    service.store("weather", "c", new ByteArrayInputStream("c".getBytes()), 1);
    service.deleteAllForSchema("atm");
    Assertions.assertThat(service.exists("atm", "a")).isFalse();
    Assertions.assertThat(service.exists("atm", "b")).isFalse();
    Assertions.assertThat(service.exists("weather", "c")).isTrue();
  }

  @Test
  void ensureBucket_isIdempotentAcrossInstances() {
    var second = new MinioStorageService(client, bucket);
    second.store("atm", "f", new ByteArrayInputStream("x".getBytes()), 1);
    Assertions.assertThat(second.exists("atm", "f")).isTrue();
  }
}
