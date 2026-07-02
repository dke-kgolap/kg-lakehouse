package at.jku.dke.bigkgolap.model;

public sealed class DomainModelException extends RuntimeException
    permits InvalidCubeSchemaException,
        InvalidHierarchyException,
        InvalidContextException,
        HierarchyNotAvailableException,
        CannotRollUpAllLevelException,
        UnknownRollUpFunctionException,
        SchemaAlreadyRegisteredException,
        SchemaNotFoundException {

  protected DomainModelException(String message) {
    super(message);
  }

  protected DomainModelException(String message, Throwable cause) {
    super(message, cause);
  }
}
