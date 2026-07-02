package at.jku.dke.bigkgolap.model;

public final class SchemaNotFoundException extends DomainModelException {

  public SchemaNotFoundException(String id) {
    super("No schema registered under id '%s'".formatted(id));
  }
}
