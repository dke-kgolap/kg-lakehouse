package at.jku.dke.bigkgolap.engines.aixm.internal;

import java.util.Set;

final class AixmConstants {

  static final String AIXM_PREFIX = "aixm:";
  static final String EVENT_PREFIX = "event:";
  static final String EVENT_EVENT_TAG = "event:Event";
  static final String NOTAM_TOPIC = "Notam";
  static final String MESSAGE_HAS_MEMBER = "message:hasMember";
  static final String LOCATION_TAG = "event:location";
  static final String AFFECTED_FIR_TAG = "event:affectedFIR";
  // Standard AIXM airport ICAO designator. Present once per airport baseline
  // ({ICAO}/aixm/baseline.xml) on the AirportHeliportTimeSlice; absent from NOTAMs.
  static final String LOCATION_INDICATOR_ICAO_TAG = "aixm:locationIndicatorICAO";
  // A FIR baseline ({FIR}/aixm/baseline.xml) carries no ICAO; the FIR is represented by
  // an aixm:Airspace of type FIR/UIR designated by the FIR ICAO code (standard AIXM).
  static final String DESIGNATOR_TAG = "aixm:designator";
  static final String TYPE_TAG = "aixm:type";
  static final String AIRSPACE_TOPIC = "Airspace";
  static final Set<String> FIR_AIRSPACE_TYPES = Set.of("FIR", "UIR");
  static final String BEGIN_POSITION_TAG = "gml:beginPosition";
  static final String END_POSITION_TAG = "gml:endPosition";
  static final String GML_ID_ATT = "gml:id";

  static final Set<String> VALID_TIME_TAGS = Set.of("gml:validTime", "gml:TimePeriod");
  static final Set<String> SUPPORTED_CONCEPTS =
      Set.of("aixm:availability", "aixm:activation", "aixm:annotation");
  static final Set<String> SUPPORTED_EVENT_CONCEPTS = Set.of("event:annotation", "event:extension");

  static final String BASE_URI = "http://example.org/bigkgolap/atm/aixm#";
  static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

  // SAX parser uses setNamespaceAware(false) so the handler receives qName (e.g.
  // aixm:AircraftStand) rather than (uri, localName) pairs. This is intentional.
  static final boolean NAMESPACE_AWARE = false;

  private AixmConstants() {}
}
