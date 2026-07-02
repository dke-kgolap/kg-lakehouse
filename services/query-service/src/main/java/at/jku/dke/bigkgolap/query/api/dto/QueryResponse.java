package at.jku.dke.bigkgolap.query.api.dto;

public record QueryResponse(
    boolean success,
    int contextCount,
    int finalContextCount,
    long quadCount,
    String data,
    QueryTimings timings,
    String traceId) {

  /** Convenience constructor with null traceId. */
  public QueryResponse(
      boolean success,
      int contextCount,
      int finalContextCount,
      long quadCount,
      String data,
      QueryTimings timings) {
    this(success, contextCount, finalContextCount, quadCount, data, timings, null);
  }

  /** Return a copy with a different data and quadCount value. */
  public QueryResponse withData(String newData, long newQuadCount) {
    return new QueryResponse(
        success, contextCount, finalContextCount, newQuadCount, newData, timings, traceId);
  }
}
