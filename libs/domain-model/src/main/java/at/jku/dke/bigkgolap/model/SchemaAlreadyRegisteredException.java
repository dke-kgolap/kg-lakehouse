package at.jku.dke.bigkgolap.model;

public final class SchemaAlreadyRegisteredException extends DomainModelException {

  public SchemaAlreadyRegisteredException(String id) {
    super("A different schema is already registered under id '%s'".formatted(id));
  }
}
