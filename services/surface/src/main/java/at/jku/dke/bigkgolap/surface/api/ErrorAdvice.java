package at.jku.dke.bigkgolap.surface.api;

import at.jku.dke.bigkgolap.model.InvalidCubeSchemaException;
import at.jku.dke.bigkgolap.model.SchemaAlreadyRegisteredException;
import at.jku.dke.bigkgolap.model.SchemaNotFoundException;
import at.jku.dke.bigkgolap.surface.api.Exceptions.BadRequestException;
import at.jku.dke.bigkgolap.surface.api.Exceptions.NotFoundException;
import at.jku.dke.bigkgolap.surface.api.Exceptions.UnsupportedMediaTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.yaml.snakeyaml.error.YAMLException;

@RestControllerAdvice
public class ErrorAdvice {

  private static final Logger log = LoggerFactory.getLogger(ErrorAdvice.class);

  @ExceptionHandler({NotFoundException.class, SchemaNotFoundException.class})
  public ProblemDetail notFound(RuntimeException e) {
    return problem(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler({
    InvalidCubeSchemaException.class,
    BadRequestException.class,
    IllegalArgumentException.class
  })
  public ProblemDetail badRequest(RuntimeException e) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(YAMLException.class)
  public ProblemDetail yamlError(YAMLException e) {
    return problem(HttpStatus.BAD_REQUEST, "Invalid YAML: " + e.getMessage());
  }

  @ExceptionHandler(SchemaAlreadyRegisteredException.class)
  public ProblemDetail conflict(RuntimeException e) {
    return problem(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(UnsupportedMediaTypeException.class)
  public ProblemDetail unsupportedMediaType(RuntimeException e) {
    return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  public ProblemDetail missingPart(MissingServletRequestPartException e) {
    return problem(HttpStatus.BAD_REQUEST, "Missing request part '" + e.getRequestPartName() + "'");
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail fallback(Exception e) {
    log.error("Unhandled exception", e);
    String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, detail);
  }

  private ProblemDetail problem(HttpStatus status, String detail) {
    String resolvedDetail = detail != null ? detail : status.getReasonPhrase();
    return ProblemDetail.forStatusAndDetail(status, resolvedDetail);
  }
}
