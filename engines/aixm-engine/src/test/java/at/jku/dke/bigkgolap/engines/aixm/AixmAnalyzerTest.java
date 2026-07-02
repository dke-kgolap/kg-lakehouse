package at.jku.dke.bigkgolap.engines.aixm;

import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.Hierarchy;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class AixmAnalyzerTest {

  private final CubeSchema atm = Fixtures.atmSchema();
  private final AixmAnalyzer analyzer = new AixmAnalyzer();

  @Test
  void multiFeatureFileProducesUnionedHierarchiesForTimeLocationAndTopic() throws Exception {
    var result = analyzer.analyze(Fixtures.aixmMultiFeature(), atm);

    var topics = result.hierarchies().stream().filter(h -> "topic".equals(h.dimension())).toList();
    Assertions.assertThat(topics).hasSize(2);
    Assertions.assertThat(topics.stream().map(h -> h.leafMember().value()).toList())
        .containsExactlyInAnyOrder("AircraftStand", "ApronElement");

    var locations =
        result.hierarchies().stream().filter(h -> "location".equals(h.dimension())).toList();
    Assertions.assertThat(locations).hasSize(2);
    Assertions.assertThat(locations.stream().map(h -> h.leafMember().value()).toList())
        .containsExactlyInAnyOrder("LOWW", "LOWS");

    var times = result.hierarchies().stream().filter(h -> "time".equals(h.dimension())).toList();
    Assertions.assertThat(times).hasSize(3);
    Assertions.assertThat(times.stream().map(h -> h.leafMember().value()).toList())
        .containsExactlyInAnyOrder("2025-01-15", "2025-01-16", "2025-01-17");
  }

  @Test
  void metadataCapturesDistinctFactsAcrossFeatures() throws Exception {
    var result = analyzer.analyze(Fixtures.aixmMultiFeature(), atm);
    Assertions.assertThat(result.metadata()).containsEntry("aixm.featureCount", "2");
    Assertions.assertThat(result.metadata().get("aixm.topics"))
        .contains("AircraftStand")
        .contains("ApronElement");
    Assertions.assertThat(result.metadata().get("aixm.locations"))
        .contains("LOWW")
        .contains("LOWS");
    Assertions.assertThat(result.metadata().get("aixm.affectedFirs")).contains("LOVV");
  }

  @Test
  void fileWithNoTimeDataEmitsTimeAllHierarchy() {
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <message:AIXMBasicMessage
            xmlns:message="http://www.aixm.aero/schema/5.1/message"
            xmlns:gml="http://www.opengis.net/gml/3.2"
            xmlns:aixm="http://www.aixm.aero/schema/5.1"
            xmlns:event="http://www.aixm.aero/schema/5.1/event"
            gml:id="MSG-X">
          <message:hasMember>
            <aixm:AircraftStand gml:id="STAND-X">
              <aixm:availability>
                <aixm:AircraftStandAvailability gml:id="AVAIL-X">
                  <event:location>LOWW</event:location>
                  <event:affectedFIR>LOVV</event:affectedFIR>
                </aixm:AircraftStandAvailability>
              </aixm:availability>
            </aixm:AircraftStand>
          </message:hasMember>
        </message:AIXMBasicMessage>
        """;
    var result =
        analyzer.analyze(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), atm);
    var times = result.hierarchies().stream().filter(h -> "time".equals(h.dimension())).toList();
    Assertions.assertThat(times).containsExactly(Hierarchy.all("time"));
  }

  @Test
  void fileWithOnlyAffectedFirEmitsFirLevelLocationHierarchy() {
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <message:AIXMBasicMessage
            xmlns:message="http://www.aixm.aero/schema/5.1/message"
            xmlns:gml="http://www.opengis.net/gml/3.2"
            xmlns:aixm="http://www.aixm.aero/schema/5.1"
            xmlns:event="http://www.aixm.aero/schema/5.1/event">
          <message:hasMember>
            <aixm:AircraftStand gml:id="STAND-Y">
              <aixm:availability>
                <aixm:AircraftStandAvailability gml:id="AVAIL-Y">
                  <event:affectedFIR>LOVV</event:affectedFIR>
                </aixm:AircraftStandAvailability>
              </aixm:availability>
            </aixm:AircraftStand>
          </message:hasMember>
        </message:AIXMBasicMessage>
        """;
    var result =
        analyzer.analyze(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), atm);
    var locations =
        result.hierarchies().stream().filter(h -> "location".equals(h.dimension())).toList();
    Assertions.assertThat(locations).hasSize(1);
    Assertions.assertThat(locations.get(0).leafMember().value()).isEqualTo("LOVV");
  }

  @Test
  void analyzerReturnsNoHierarchiesWhenSchemaHasNoAixmShapedDimensions() throws Exception {
    var weather = Fixtures.weatherSchema();
    var result = analyzer.analyze(Fixtures.aixmMultiFeature(), weather);
    Assertions.assertThat(result.hierarchies()).isEmpty();
    Assertions.assertThat(result.metadata().get("aixm.featureCount")).isEqualTo("2");
  }

  @Test
  void notamEventEventFeatureResolvesTopicToNotamAndTimeTo20250105() throws Exception {
    var result = analyzer.analyze(Fixtures.aixmNotam(), atm);

    var topics = result.hierarchies().stream().filter(h -> "topic".equals(h.dimension())).toList();
    Assertions.assertThat(topics).hasSize(1);
    Assertions.assertThat(topics.get(0).leafMember().value()).isEqualTo("Notam");

    var times = result.hierarchies().stream().filter(h -> "time".equals(h.dimension())).toList();
    Assertions.assertThat(times).hasSize(1);
    Assertions.assertThat(times.get(0).leafMember().value()).isEqualTo("2025-01-05");

    var locations =
        result.hierarchies().stream().filter(h -> "location".equals(h.dimension())).toList();
    Assertions.assertThat(locations).hasSize(1);
    Assertions.assertThat(locations.get(0).leafMember().value()).isEqualTo("LOWS");
  }

  @Test
  void airportBaselineDerivesLocationFromLocationIndicatorIcao() {
    // Mirrors a real {ICAO}/aixm/baseline.xml: a single AirportHeliport carries the
    // ICAO in aixm:locationIndicatorICAO, and the other features (Runway, …) carry no
    // event:location. Every feature in the file belongs to that airport, so all
    // contexts must resolve to the location level (LOWW).
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <message:AIXMBasicMessage
            xmlns:message="http://www.aixm.aero/schema/5.1/message"
            xmlns:gml="http://www.opengis.net/gml/3.2"
            xmlns:aixm="http://www.aixm.aero/schema/5.1"
            xmlns:event="http://www.aixm.aero/schema/5.1/event">
          <message:hasMember>
            <aixm:AirportHeliport gml:id="AH-1">
              <aixm:timeSlice>
                <aixm:AirportHeliportTimeSlice gml:id="AH-1-TS">
                  <aixm:designator>LOWW</aixm:designator>
                  <aixm:name>VIENNA INTERNATIONAL</aixm:name>
                  <aixm:locationIndicatorICAO>LOWW</aixm:locationIndicatorICAO>
                  <aixm:type>AD</aixm:type>
                </aixm:AirportHeliportTimeSlice>
              </aixm:timeSlice>
            </aixm:AirportHeliport>
          </message:hasMember>
          <message:hasMember>
            <aixm:Runway gml:id="RWY-1">
              <aixm:timeSlice>
                <aixm:RunwayTimeSlice gml:id="RWY-1-TS">
                  <aixm:designator>16/34</aixm:designator>
                </aixm:RunwayTimeSlice>
              </aixm:timeSlice>
            </aixm:Runway>
          </message:hasMember>
        </message:AIXMBasicMessage>
        """;
    var result =
        analyzer.analyze(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), atm);

    var locations =
        result.hierarchies().stream().filter(h -> "location".equals(h.dimension())).toList();
    Assertions.assertThat(locations).isNotEmpty();
    Assertions.assertThat(locations)
        .as("every feature in an airport baseline is located at the file's ICAO")
        .allSatisfy(h -> Assertions.assertThat(h.leafMember().value()).isEqualTo("LOWW"));
  }

  @Test
  void firBaselineDerivesFirFromFirAirspaceDesignator() {
    // Mirrors a real {FIR}/aixm/baseline.xml: no AirportHeliport/ICAO, but the FIR is
    // represented by an aixm:Airspace of type FIR designated by the FIR ICAO code.
    // The other en-route features (RouteSegment, …) carry no event:location, so every
    // context must resolve to the fir level (LOVV).
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <message:AIXMBasicMessage
            xmlns:message="http://www.aixm.aero/schema/5.1/message"
            xmlns:gml="http://www.opengis.net/gml/3.2"
            xmlns:aixm="http://www.aixm.aero/schema/5.1"
            xmlns:event="http://www.aixm.aero/schema/5.1/event">
          <message:hasMember>
            <aixm:Airspace gml:id="AS-FIR">
              <aixm:timeSlice>
                <aixm:AirspaceTimeSlice gml:id="AS-FIR-TS">
                  <aixm:type>FIR</aixm:type>
                  <aixm:designator>LOVV</aixm:designator>
                  <aixm:name>WIEN FIR</aixm:name>
                </aixm:AirspaceTimeSlice>
              </aixm:timeSlice>
            </aixm:Airspace>
          </message:hasMember>
          <message:hasMember>
            <aixm:RouteSegment gml:id="RS-1">
              <aixm:timeSlice>
                <aixm:RouteSegmentTimeSlice gml:id="RS-1-TS">
                  <aixm:designator>UL856</aixm:designator>
                </aixm:RouteSegmentTimeSlice>
              </aixm:timeSlice>
            </aixm:RouteSegment>
          </message:hasMember>
        </message:AIXMBasicMessage>
        """;
    var result =
        analyzer.analyze(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), atm);

    var locations =
        result.hierarchies().stream().filter(h -> "location".equals(h.dimension())).toList();
    Assertions.assertThat(locations).isNotEmpty();
    Assertions.assertThat(locations)
        .as("every feature in a FIR baseline resolves to the FIR designator")
        .allSatisfy(h -> Assertions.assertThat(h.leafMember().value()).isEqualTo("LOVV"));
  }

  @Test
  void firScopedNotamWithFirCodeInEventLocationResolvesToFirLevel() {
    // Regression: atm-gen FIR-scoped NOTAMs (file at {date}/{FIR}/aixm/notams.xml)
    // put the FIR designator inside event:location and omit event:affectedFIR.
    // The analyzer must classify the value by schema membership: LOVV is a known FIR
    // member in atm.yaml, so the context resolves at the fir level (LOVV) — not at
    // the location-leaf level, which would fail the hierarchy lookup and poison the
    // Kafka consumer.
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <message:AIXMBasicMessage
            xmlns:message="http://www.aixm.aero/schema/5.1/message"
            xmlns:gml="http://www.opengis.net/gml/3.2"
            xmlns:aixm="http://www.aixm.aero/schema/5.1"
            xmlns:event="http://www.aixm.aero/schema/5.1/event">
          <message:hasMember>
            <event:Event gml:id="NOTAM-FIR-1">
              <gml:validTime>
                <gml:TimePeriod gml:id="TP-NOTAM-FIR-1">
                  <gml:beginPosition>2025-01-05T00:00:00Z</gml:beginPosition>
                  <gml:endPosition>2025-01-05T23:59:59Z</gml:endPosition>
                </gml:TimePeriod>
              </gml:validTime>
              <event:annotation>
                <event:NOTAM gml:id="NOTAMPROPS-FIR-1">
                  <event:scope>E</event:scope>
                  <event:location>LOVV</event:location>
                </event:NOTAM>
              </event:annotation>
            </event:Event>
          </message:hasMember>
        </message:AIXMBasicMessage>
        """;
    var result =
        analyzer.analyze(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), atm);
    var locations =
        result.hierarchies().stream().filter(h -> "location".equals(h.dimension())).toList();
    Assertions.assertThat(locations).hasSize(1);
    Assertions.assertThat(locations.get(0).leafMember().value()).isEqualTo("LOVV");
  }

  @Test
  void notamWithUnknownLocationCodeDegradesToWildcardWithoutFailing() {
    // Defensive: a NOTAM whose event:location is neither a known aerodrome nor a
    // known FIR must degrade to the unlocated wildcard rather than crash. Mirrors
    // the firAirspaceWithUnknownDesignatorDegradesToWildcardWithoutFailing case
    // for the event-tag input path.
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <message:AIXMBasicMessage
            xmlns:message="http://www.aixm.aero/schema/5.1/message"
            xmlns:gml="http://www.opengis.net/gml/3.2"
            xmlns:aixm="http://www.aixm.aero/schema/5.1"
            xmlns:event="http://www.aixm.aero/schema/5.1/event">
          <message:hasMember>
            <event:Event gml:id="NOTAM-UNK-1">
              <gml:validTime>
                <gml:TimePeriod gml:id="TP-NOTAM-UNK-1">
                  <gml:beginPosition>2025-01-05T00:00:00Z</gml:beginPosition>
                  <gml:endPosition>2025-01-05T23:59:59Z</gml:endPosition>
                </gml:TimePeriod>
              </gml:validTime>
              <event:annotation>
                <event:NOTAM gml:id="NOTAMPROPS-UNK-1">
                  <event:location>XYZW</event:location>
                </event:NOTAM>
              </event:annotation>
            </event:Event>
          </message:hasMember>
        </message:AIXMBasicMessage>
        """;
    var result =
        analyzer.analyze(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), atm);
    var locations =
        result.hierarchies().stream().filter(h -> "location".equals(h.dimension())).toList();
    Assertions.assertThat(locations).containsExactly(Hierarchy.all("location"));
  }

  @Test
  void firAirspaceWithUnknownDesignatorDegradesToWildcardWithoutFailing() {
    // Defensive: a non-conformant generator may emit an arbitrary FIR designator that
    // the schema doesn't know. The file must still ingest (no HierarchyNotAvailable
    // crash), with location left as the unlocated wildcard rather than a bad rollup.
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <message:AIXMBasicMessage
            xmlns:message="http://www.aixm.aero/schema/5.1/message"
            xmlns:gml="http://www.opengis.net/gml/3.2"
            xmlns:aixm="http://www.aixm.aero/schema/5.1"
            xmlns:event="http://www.aixm.aero/schema/5.1/event">
          <message:hasMember>
            <aixm:Airspace gml:id="AS-FIR">
              <aixm:timeSlice>
                <aixm:AirspaceTimeSlice gml:id="AS-FIR-TS">
                  <aixm:type>FIR</aixm:type>
                  <aixm:designator>EAAD</aixm:designator>
                </aixm:AirspaceTimeSlice>
              </aixm:timeSlice>
            </aixm:Airspace>
          </message:hasMember>
        </message:AIXMBasicMessage>
        """;
    var result =
        analyzer.analyze(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), atm);
    var locations =
        result.hierarchies().stream().filter(h -> "location".equals(h.dimension())).toList();
    Assertions.assertThat(locations).containsExactly(Hierarchy.all("location"));
  }

  @Test
  void extendedTopicFeaturesResolveToNonNullHierarchies() {
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <message:AIXMBasicMessage
            xmlns:message="http://www.aixm.aero/schema/5.1/message"
            xmlns:gml="http://www.opengis.net/gml/3.2"
            xmlns:aixm="http://www.aixm.aero/schema/5.1"
            xmlns:event="http://www.aixm.aero/schema/5.1/event">
          <message:hasMember>
            <aixm:Runway gml:id="RWY-1">
              <aixm:availability>
                <aixm:RunwayAvailability gml:id="RWYAVAIL-1">
                  <event:location>LOWW</event:location>
                </aixm:RunwayAvailability>
              </aixm:availability>
            </aixm:Runway>
          </message:hasMember>
          <message:hasMember>
            <aixm:VOR gml:id="VOR-1">
              <aixm:availability>
                <aixm:NavaidAvailability gml:id="VORAVAIL-1">
                  <event:affectedFIR>LOVV</event:affectedFIR>
                </aixm:NavaidAvailability>
              </aixm:availability>
            </aixm:VOR>
          </message:hasMember>
          <message:hasMember>
            <aixm:Airspace gml:id="AS-1">
              <aixm:availability>
                <aixm:AirspaceAvailability gml:id="ASAVAIL-1">
                  <event:affectedFIR>LOVV</event:affectedFIR>
                </aixm:AirspaceAvailability>
              </aixm:availability>
            </aixm:Airspace>
          </message:hasMember>
        </message:AIXMBasicMessage>
        """;
    var result =
        analyzer.analyze(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), atm);
    var topicValues =
        result.hierarchies().stream()
            .filter(h -> "topic".equals(h.dimension()))
            .map(h -> h.leafMember().value())
            .toList();
    Assertions.assertThat(topicValues).containsExactlyInAnyOrder("Runway", "VOR", "Airspace");
  }
}
