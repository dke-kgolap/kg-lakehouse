package at.jku.dke.bigkgolap.engines.iwxxm.internal;

import java.util.Set;
import java.util.regex.Pattern;

public final class IwxxmConstants {

  public static final String IWXXM_PREFIX = "iwxxm:";
  public static final String AIXM_PREFIX = "aixm:";
  public static final String GML_ID_ATT = "gml:id";
  public static final String XLINK_HREF_ATT = "xlink:href";

  public static final String ROOT_METAR = "METAR";
  public static final String ROOT_TAF = "TAF";
  public static final String ROOT_SIGMET = "SIGMET";
  public static final Set<String> MVP_ROOTS = Set.of(ROOT_METAR, ROOT_TAF, ROOT_SIGMET);

  public static final String ISSUE_TIME_TAG = "iwxxm:issueTime";
  public static final String VALID_PERIOD_TAG = "iwxxm:validPeriod";
  public static final String TIME_INSTANT_POSITION_TAG = "gml:timePosition";
  public static final String BEGIN_POSITION_TAG = "gml:beginPosition";
  public static final String END_POSITION_TAG = "gml:endPosition";

  public static final String AERODROME_TAG = "iwxxm:aerodrome";
  public static final String AFFECTED_FIR_TAG = "iwxxm:affectedFIR";
  public static final String ISSUING_ATS_REGION_TAG = "iwxxm:issuingAirTrafficServicesRegion";
  public static final String AIXM_DESIGNATOR_TAG = "aixm:designator";

  // Matches a standalone ICAO aerodrome/FIR designator segment (3–5 uppercase letters).
  public static final Pattern ICAO_IN_HREF = Pattern.compile("[A-Z]{3,5}");

  public static final String BASE_URI = "http://example.org/bigkgolap/atm/iwxxm#";
  public static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

  // SAX parser uses setNamespaceAware(false) so the handler receives qName (e.g.
  // iwxxm:METAR) rather than (uri, localName) pairs. This is intentional.
  public static final boolean NAMESPACE_AWARE = false;

  private IwxxmConstants() {}
}
