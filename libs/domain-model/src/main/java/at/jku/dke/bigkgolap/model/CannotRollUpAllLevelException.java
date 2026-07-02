package at.jku.dke.bigkgolap.model;

public final class CannotRollUpAllLevelException extends DomainModelException {

  public CannotRollUpAllLevelException(String dimension) {
    super("Cannot roll up ALL-level hierarchy of dimension '%s'".formatted(dimension));
  }
}
