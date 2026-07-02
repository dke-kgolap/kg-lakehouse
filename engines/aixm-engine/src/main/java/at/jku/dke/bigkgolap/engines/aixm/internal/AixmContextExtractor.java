package at.jku.dke.bigkgolap.engines.aixm.internal;

import static at.jku.dke.bigkgolap.engines.aixm.internal.AixmConstants.*;

import at.jku.dke.bigkgolap.engines.aixm.AixmParseException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class AixmContextExtractor extends DefaultHandler {

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      new DateTimeFormatterBuilder()
          .appendOptional(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
          .appendOptional(DateTimeFormatter.ISO_DATE_TIME)
          .appendOptional(DateTimeFormatter.ISO_DATE)
          .toFormatter();

  private final SAXParser saxParser;
  private final List<AixmFeature> collected = new ArrayList<>();

  // File-scoped: an airport baseline names its ICAO once (on the AirportHeliport)
  // and every feature in the file belongs to that airport.
  private String documentLocationIndicator;
  // File-scoped: a FIR baseline names its FIR via the FIR/UIR Airspace designator.
  private String documentFirDesignator;
  // Per-feature scratch: the current feature's own designator and type, used to
  // recognise the FIR/UIR Airspace at feature end.
  private String currentFeatureDesignator;
  private String currentFeatureType;

  private boolean inHasMember;
  private boolean nextIsTopElementOfMember;
  private int aixmStateCount;
  private int validTimeStateCount;
  private String currentTag = "";

  private String currentTopic;
  private String currentLocation;
  private String currentAffectedFir;
  private LocalDate currentBegin;
  private LocalDate currentEnd;

  public AixmContextExtractor() {
    try {
      SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setNamespaceAware(NAMESPACE_AWARE);
      this.saxParser = factory.newSAXParser();
    } catch (Exception e) {
      throw new AixmParseException("Could not create SAX parser", e);
    }
  }

  public List<AixmFeature> features() {
    return Collections.unmodifiableList(collected);
  }

  /** The airport ICAO declared by this document, or {@code null} for non-airport files. */
  public String locationIndicator() {
    return documentLocationIndicator;
  }

  /** The FIR ICAO code from this document's FIR/UIR Airspace, or {@code null} if none. */
  public String firDesignator() {
    return documentFirDesignator;
  }

  public void parse(InputStream input) {
    try {
      saxParser.parse(input, this);
    } catch (IOException e) {
      throw new AixmParseException("I/O error while parsing AIXM", e);
    } catch (SAXException e) {
      throw new AixmParseException("Malformed AIXM XML", e);
    }
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) {
    if (qName == null) return;
    currentTag = qName;

    if (MESSAGE_HAS_MEMBER.equals(qName)) {
      beginFeature();
      return;
    }

    if (!inHasMember) return;

    if (qName.startsWith(AIXM_PREFIX)) {
      if (nextIsTopElementOfMember && currentTopic == null) {
        currentTopic = qName.substring(AIXM_PREFIX.length());
      }
      aixmStateCount++;
    } else if (EVENT_EVENT_TAG.equals(qName)) {
      // NOTAM documents use event:Event as the hasMember root.
      if (nextIsTopElementOfMember && currentTopic == null) {
        currentTopic = NOTAM_TOPIC;
      }
      aixmStateCount++;
    }
    if (VALID_TIME_TAGS.contains(qName)) {
      validTimeStateCount++;
    }
    nextIsTopElementOfMember = false;
  }

  @Override
  public void characters(char[] ch, int start, int length) {
    if (!inHasMember) return;
    switch (currentTag) {
      case LOCATION_TAG -> currentLocation = new String(ch, start, length);
      case AFFECTED_FIR_TAG -> currentAffectedFir = new String(ch, start, length);
      case LOCATION_INDICATOR_ICAO_TAG -> {
        if (documentLocationIndicator == null) {
          documentLocationIndicator = new String(ch, start, length);
        }
      }
      case DESIGNATOR_TAG -> {
        if (currentFeatureDesignator == null) {
          currentFeatureDesignator = new String(ch, start, length);
        }
      }
      case TYPE_TAG -> {
        if (currentFeatureType == null) {
          currentFeatureType = new String(ch, start, length);
        }
      }
      case BEGIN_POSITION_TAG -> {
        if (aixmStateCount > 0 && validTimeStateCount > 0) {
          currentBegin = parseDate(new String(ch, start, length));
        }
      }
      case END_POSITION_TAG -> {
        if (aixmStateCount > 0 && validTimeStateCount > 0) {
          currentEnd = parseDate(new String(ch, start, length));
        }
      }
    }
  }

  @Override
  public void endElement(String uri, String localName, String qName) {
    if (qName == null) return;
    currentTag = "";

    if (MESSAGE_HAS_MEMBER.equals(qName)) {
      endFeature();
      return;
    }

    if (!inHasMember) return;

    if (qName.startsWith(AIXM_PREFIX) || EVENT_EVENT_TAG.equals(qName)) {
      aixmStateCount--;
    }
    if (VALID_TIME_TAGS.contains(qName)) {
      validTimeStateCount--;
    }
  }

  private void beginFeature() {
    inHasMember = true;
    nextIsTopElementOfMember = true;
    aixmStateCount = 0;
    validTimeStateCount = 0;
    currentTopic = null;
    currentLocation = null;
    currentAffectedFir = null;
    currentBegin = null;
    currentEnd = null;
    currentFeatureDesignator = null;
    currentFeatureType = null;
  }

  private void endFeature() {
    collected.add(
        new AixmFeature(
            currentTopic, currentLocation, currentAffectedFir, currentBegin, currentEnd));
    // The FIR/UIR Airspace designates the FIR by its ICAO code; remember it for the
    // whole document (first one wins) so en-route features inherit the FIR location.
    if (documentFirDesignator == null
        && AIRSPACE_TOPIC.equals(currentTopic)
        && currentFeatureType != null
        && FIR_AIRSPACE_TYPES.contains(currentFeatureType)
        && currentFeatureDesignator != null
        && !currentFeatureDesignator.isEmpty()) {
      documentFirDesignator = currentFeatureDesignator;
    }
    inHasMember = false;
    nextIsTopElementOfMember = false;
  }

  private LocalDate parseDate(String value) {
    try {
      return LocalDate.parse(value, DATE_TIME_FORMATTER);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
