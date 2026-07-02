package at.jku.dke.bigkgolap.model;

public final class UnknownRollUpFunctionException extends DomainModelException {

  public UnknownRollUpFunctionException(String name) {
    super("No rollup function registered under '%s'".formatted(name));
  }
}
