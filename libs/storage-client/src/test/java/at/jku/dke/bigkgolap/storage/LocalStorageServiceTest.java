package at.jku.dke.bigkgolap.storage;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalStorageServiceTest {

  private LocalStorageService service(Path tmp) {
    return new LocalStorageService(tmp.resolve("storage"));
  }

  @Test
  void storeAndLoad_roundTripPreservesBytes(@TempDir Path tmp) throws Exception {
    var svc = service(tmp);
    var payload = "hello world".getBytes(StandardCharsets.UTF_8);
    svc.store("atm", "f1.xml", new ByteArrayInputStream(payload), payload.length);
    var read = svc.load("atm", "f1.xml").readAllBytes();
    Assertions.assertThat(read).isEqualTo(payload);
  }

  @Test
  void exists_reflectsStoreAndDelete(@TempDir Path tmp) {
    var svc = service(tmp);
    Assertions.assertThat(svc.exists("atm", "f1.xml")).isFalse();
    svc.store("atm", "f1.xml", new ByteArrayInputStream("x".getBytes()), 1);
    Assertions.assertThat(svc.exists("atm", "f1.xml")).isTrue();
    svc.delete("atm", "f1.xml");
    Assertions.assertThat(svc.exists("atm", "f1.xml")).isFalse();
  }

  @Test
  void load_throwsStorageNotFoundExceptionForMissingObject(@TempDir Path tmp) {
    var svc = service(tmp);
    Assertions.assertThatThrownBy(() -> svc.load("atm", "missing.xml"))
        .isInstanceOf(StorageNotFoundException.class);
  }

  @Test
  void schemas_areIsolatedByDirectory(@TempDir Path tmp) throws Exception {
    var svc = service(tmp);
    svc.store(
        "atm", "f.xml", new ByteArrayInputStream("atm-bytes".getBytes(StandardCharsets.UTF_8)), 9);
    svc.store(
        "weather",
        "f.xml",
        new ByteArrayInputStream("wx-bytes".getBytes(StandardCharsets.UTF_8)),
        8);
    var atmRead = new String(svc.load("atm", "f.xml").readAllBytes());
    var wxRead = new String(svc.load("weather", "f.xml").readAllBytes());
    Assertions.assertThat(atmRead).isEqualTo("atm-bytes");
    Assertions.assertThat(wxRead).isEqualTo("wx-bytes");
  }

  @Test
  void deleteAllForSchema_removesOnlyThatSchemasFiles(@TempDir Path tmp) {
    var svc = service(tmp);
    svc.store("atm", "a.xml", new ByteArrayInputStream("a".getBytes()), 1);
    svc.store("atm", "b.xml", new ByteArrayInputStream("b".getBytes()), 1);
    svc.store("weather", "c.xml", new ByteArrayInputStream("c".getBytes()), 1);
    svc.deleteAllForSchema("atm");
    Assertions.assertThat(svc.exists("atm", "a.xml")).isFalse();
    Assertions.assertThat(svc.exists("atm", "b.xml")).isFalse();
    Assertions.assertThat(svc.exists("weather", "c.xml")).isTrue();
  }

  @Test
  void clearAll_wipesEverything(@TempDir Path tmp) {
    var svc = service(tmp);
    svc.store("atm", "a.xml", new ByteArrayInputStream("a".getBytes()), 1);
    svc.store("weather", "c.xml", new ByteArrayInputStream("c".getBytes()), 1);
    svc.clearAll();
    Assertions.assertThat(svc.exists("atm", "a.xml")).isFalse();
    Assertions.assertThat(svc.exists("weather", "c.xml")).isFalse();
  }

  @Test
  void deleteAllForSchema_onEmptySchemaIsANoOp(@TempDir Path tmp) {
    var svc = service(tmp);
    svc.deleteAllForSchema("ghost");
    Assertions.assertThat(Files.exists(tmp.resolve("storage").resolve("ghost"))).isFalse();
  }

  @Test
  void invalidSchemaId_isRejected(@TempDir Path tmp) {
    var svc = service(tmp);
    Assertions.assertThatThrownBy(
            () -> svc.store("Bad/Id", "f", new ByteArrayInputStream("x".getBytes()), 1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
