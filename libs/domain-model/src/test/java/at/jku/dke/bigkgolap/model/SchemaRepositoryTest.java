package at.jku.dke.bigkgolap.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.jku.dke.bigkgolap.model.repository.FileSystemSchemaRepository;
import at.jku.dke.bigkgolap.model.repository.InMemorySchemaRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaRepositoryTest {

  @Test
  void inMemoryRegisterAndLookupRoundTrip() {
    var repo = new InMemorySchemaRepository();
    var schema = Fixtures.atmSchema();
    repo.register(schema);
    assertThat(repo.get("atm")).isSameAs(schema);
    assertThat(repo.list()).containsExactly("atm");
  }

  @Test
  void inMemoryRegisterYamlParsesAndCaches() {
    var repo = new InMemorySchemaRepository();
    var parsed = repo.registerYaml(new ByteArrayInputStream(Fixtures.atmYamlBytes()));
    assertThat(parsed.id()).isEqualTo("atm");
    assertThat(repo.get("atm")).isEqualTo(parsed);
  }

  @Test
  void inMemoryRejectsADifferentSchemaUnderAnExistingId() {
    var repo = new InMemorySchemaRepository();
    repo.registerYaml(new ByteArrayInputStream(Fixtures.atmYamlBytes()));
    String mutated =
        """
                schema:
                  id: atm
                  dimensions:
                    time:
                      levels:
                        - { name: year, depth: 0, rollup_to: null, rollup_function: null }
                """;
    assertThatThrownBy(() -> repo.registerYaml(new ByteArrayInputStream(mutated.getBytes())))
        .isInstanceOf(SchemaAlreadyRegisteredException.class);
  }

  @Test
  void inMemoryAcceptsTheSameSchemaBeingRegisteredTwice() {
    var repo = new InMemorySchemaRepository();
    var first = repo.registerYaml(new ByteArrayInputStream(Fixtures.atmYamlBytes()));
    var second = repo.registerYaml(new ByteArrayInputStream(Fixtures.atmYamlBytes()));
    assertThat(first).isEqualTo(second);
    assertThat(repo.list()).containsExactly("atm");
  }

  @Test
  void inMemoryHoldsTwoSchemasIndependently() {
    var repo = new InMemorySchemaRepository();
    repo.registerYaml(new ByteArrayInputStream(Fixtures.atmYamlBytes()));
    repo.registerYaml(new ByteArrayInputStream(Fixtures.weatherYamlBytes()));
    assertThat(repo.list()).containsExactly("atm", "weather");
    var atm = repo.get("atm");
    var weather = repo.get("weather");
    assertThat(atm.dimensionNames()).contains("topic");
    assertThat(weather.dimensionNames()).contains("station");
    assertThat(atm).isNotEqualTo(weather);
  }

  @Test
  void filesystemReportsPreExistingYamlFilesInList(@TempDir Path tmp) throws IOException {
    Files.write(tmp.resolve("atm.yaml"), Fixtures.atmYamlBytes());
    Files.write(tmp.resolve("weather.yaml"), Fixtures.weatherYamlBytes());
    var repo = new FileSystemSchemaRepository(tmp);
    assertThat(repo.list()).containsExactly("atm", "weather");
  }

  @Test
  void filesystemLazilyLoadsOnGet(@TempDir Path tmp) throws IOException {
    Files.write(tmp.resolve("atm.yaml"), Fixtures.atmYamlBytes());
    var repo = new FileSystemSchemaRepository(tmp);
    var schema = repo.get("atm");
    assertThat(schema.id()).isEqualTo("atm");
    assertThat(repo.get("atm")).isSameAs(schema);
  }

  @Test
  void filesystemRegisterYamlWritesBytesVerbatimAndUpdatesList(@TempDir Path tmp)
      throws IOException {
    var repo = new FileSystemSchemaRepository(tmp);
    byte[] bytes = Fixtures.weatherYamlBytes();
    var parsed = repo.registerYaml(new ByteArrayInputStream(bytes));
    assertThat(parsed.id()).isEqualTo("weather");
    byte[] onDisk = Files.readAllBytes(tmp.resolve("weather.yaml"));
    assertThat(onDisk).isEqualTo(bytes);
    assertThat(repo.list()).containsExactly("weather");
  }

  @Test
  void filesystemRejectsMismatchedFilenameVersusDeclaredId(@TempDir Path tmp) throws IOException {
    Files.write(tmp.resolve("foo.yaml"), Fixtures.atmYamlBytes());
    var repo = new FileSystemSchemaRepository(tmp);
    assertThatThrownBy(() -> repo.get("foo"))
        .isInstanceOf(InvalidCubeSchemaException.class)
        .hasMessageContaining("foo");
  }

  @Test
  void filesystemGetReturnsNullForMissingId(@TempDir Path tmp) {
    var repo = new FileSystemSchemaRepository(tmp);
    assertThat(repo.get("nope")).isNull();
  }

  @Test
  void filesystemRegisterWithoutYamlIsUnsupported(@TempDir Path tmp) {
    var repo = new FileSystemSchemaRepository(tmp);
    var schema = Fixtures.atmSchema();
    assertThatThrownBy(() -> repo.register(schema))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
