package at.jku.dke.bigkgolap.query.api;

import at.jku.dke.bigkgolap.model.SchemaNotFoundException;
import at.jku.dke.bigkgolap.query.exception.GraphNotAvailableException;
import at.jku.dke.bigkgolap.query.exception.InvalidQueryException;
import at.jku.dke.bigkgolap.query.exception.QueryTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErrorAdvice {

  private static final Logger log = LoggerFactory.getLogger(ErrorAdvice.class);

  @ExceptionHandler(SchemaNotFoundException.class)
  public ProblemDetail notFound(SchemaNotFoundException e) {
    return problem(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler({InvalidQueryException.class, IllegalArgumentException.class})
  public ProblemDetail badRequest(RuntimeException e) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(GraphNotAvailableException.class)
  public ProblemDetail graphUnavailable(GraphNotAvailableException e) {
    return problem(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
  }

  @ExceptionHandler(QueryTimeoutException.class)
  public ProblemDetail timeout(QueryTimeoutException e) {
    return problem(HttpStatus.GATEWAY_TIMEOUT, e.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail fallback(Exception e) {
    log.error("Unhandled exception", e);
    String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, detail);
  }

  private ProblemDetail problem(HttpStatus status, String detail) {
    String effectiveDetail = detail != null ? detail : status.getReasonPhrase();
    return ProblemDetail.forStatusAndDetail(status, effectiveDetail);
  }
}
