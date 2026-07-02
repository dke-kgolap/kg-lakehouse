package at.jku.dke.bigkgolap.graph.service;

public class NoFilesForContextException extends RuntimeException {

  public NoFilesForContextException(String schemaId, String contextId) {
    super("No files for context %s/%s".formatted(schemaId, contextId));
  }
}
