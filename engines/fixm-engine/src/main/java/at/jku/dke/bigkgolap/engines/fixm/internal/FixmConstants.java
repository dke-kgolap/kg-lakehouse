package at.jku.dke.bigkgolap.engines.fixm.internal;

public final class FixmConstants {

  public static final String FX_PREFIX = "fx:";
  public static final String FB_PREFIX = "fb:";
  public static final String GML_ID_ATT = "gml:id";

  public static final String FX_DEPARTURE_TAG = "fx:departure";
  public static final String FX_DEPARTURE_AERODROME_TAG = "fx:departureAerodrome";
  public static final String FX_LOCATION_INDICATOR_TAG = "fb:locationIndicator";
  public static final String FX_ESTIMATED_OFF_BLOCK_TIME_TAG = "fx:estimatedOffBlockTime";
  public static final String FX_ARRIVAL_TAG = "fx:arrival";
  public static final String FX_ACTUAL_TIME_OF_ARRIVAL_TAG = "fx:actualTimeOfArrival";
  public static final String FX_TIME_TAG = "fx:time";

  public static final String FIXM_TOPIC = "Flight";

  // FIXM route trajectory elements are generic <fx:element> tags (no gml:id); they map to the
  // domain class RouteTrajectoryElement so the topic taxonomy (RouteTrajectoryElement ⊑ Trajectory
  // ⊑ Flights) can generalize them.
  public static final String FX_ELEMENT_TAG = "fx:element";
  public static final String ROUTE_TRAJECTORY_ELEMENT_TYPE = "RouteTrajectoryElement";
  public static final String ROUTE_TRAJECTORY_ELEMENT_PREDICATE = "routeTrajectoryElement";

  public static final String BASE_URI = "http://example.org/bigkgolap/atm/fixm#";
  public static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

  // SAX parser uses setNamespaceAware(false) so the handler receives qName (e.g.
  // fx:Flight) rather than (uri, localName) pairs. This is intentional.
  public static final boolean NAMESPACE_AWARE = false;

  private FixmConstants() {}
}
