package at.jku.dke.bigkgolap.observability;

public final class LakehouseTags {

  public static final String SCHEMA = "schema";
  public static final String ENGINE = "engine";
  public static final String RESULT = "result";
  public static final String STATUS = "status";
  public static final String REPRESENTATION = "representation";
  public static final String SERVICE = "service";

  public static final String STATUS_SUCCESS = "success";
  public static final String STATUS_CLIENT_ERROR = "client-error";
  public static final String STATUS_SERVER_ERROR = "server-error";

  private static final int FIRST_CLIENT_ERROR_STATUS = 400;
  private static final int FIRST_SERVER_ERROR_STATUS = 500;

  public static String statusBucket(int httpStatus) {
    if (httpStatus < FIRST_CLIENT_ERROR_STATUS) {
      return STATUS_SUCCESS;
    } else if (httpStatus < FIRST_SERVER_ERROR_STATUS) {
      return STATUS_CLIENT_ERROR;
    } else {
      return STATUS_SERVER_ERROR;
    }
  }

  private LakehouseTags() {}
}
