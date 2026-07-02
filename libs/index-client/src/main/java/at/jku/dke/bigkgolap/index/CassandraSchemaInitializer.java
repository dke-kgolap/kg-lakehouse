package at.jku.dke.bigkgolap.index;

import com.datastax.oss.driver.api.core.CqlSession;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CassandraSchemaInitializer {

  private static final String SCHEMA_RESOURCE = "/cassandra/schema.cql";
  private static final String DEFAULT_KEYSPACE = "lakehouse";

  private final CqlSession session;
  private final String keyspace;

  public CassandraSchemaInitializer(CqlSession session) {
    this(session, DEFAULT_KEYSPACE);
  }

  public CassandraSchemaInitializer(CqlSession session, String keyspace) {
    this.session = session;
    this.keyspace = keyspace;
  }

  public void initialize() {
    InputStream resource = getClass().getResourceAsStream(SCHEMA_RESOURCE);
    if (resource == null) {
      throw new IndexInitializationException(
          "Schema resource '" + SCHEMA_RESOURCE + "' not found on classpath");
    }
    String cql;
    try (resource) {
      cql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IndexInitializationException("Failed to read schema resource", e);
    }

    List<String> statements = parseStatements(cql);
    for (var stmt : statements) {
      try {
        session.execute(stmt);
      } catch (RuntimeException e) {
        String firstLine = stmt.lines().findFirst().orElse(stmt);
        throw new IndexInitializationException("Failed to execute DDL: " + firstLine, e);
      }
    }
  }

  private List<String> parseStatements(String cql) {
    var result = new ArrayList<String>();
    for (var raw : cql.split(";")) {
      String trimmed = raw.trim();
      if (trimmed.isEmpty()) continue;
      // Skip statement if every line is a comment
      boolean allComments = trimmed.lines().allMatch(l -> l.trim().startsWith("--"));
      if (allComments) continue;
      String stripped = stripComments(trimmed).replace(DEFAULT_KEYSPACE, keyspace).trim();
      if (!stripped.isEmpty()) {
        result.add(stripped);
      }
    }
    return result;
  }

  private static String stripComments(String stmt) {
    var sb = new StringBuilder();
    stmt.lines().filter(l -> !l.trim().startsWith("--")).forEach(l -> sb.append(l).append('\n'));
    return sb.toString().trim();
  }
}
