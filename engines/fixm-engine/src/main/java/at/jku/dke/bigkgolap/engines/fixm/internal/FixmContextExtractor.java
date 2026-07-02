package at.jku.dke.bigkgolap.engines.fixm.internal;

import static at.jku.dke.bigkgolap.engines.fixm.internal.FixmConstants.*;

import at.jku.dke.bigkgolap.engines.fixm.FixmParseException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class FixmContextExtractor extends DefaultHandler {

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      new DateTimeFormatterBuilder()
          .appendOptional(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
          .appendOptional(DateTimeFormatter.ISO_DATE_TIME)
          .appendOptional(DateTimeFormatter.ISO_DATE)
          .toFormatter();

  private final SAXParser saxParser;

  private FixmFlight flight;

  private int departureDepth;
  private int departureAerodromeDepth;
  private int arrivalDepth;
  private int actualTimeOfArrivalDepth;

  private String currentTag = "";
  private String departureAerodrome;
  private LocalDate estimatedOffBlockTime;
  private LocalDate actualTimeOfArrival;

  public FixmContextExtractor() {
    try {
      SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setNamespaceAware(NAMESPACE_AWARE);
      this.saxParser = factory.newSAXParser();
    } catch (Exception e) {
      throw new FixmParseException("Could not create SAX parser", e);
    }
  }

  public FixmFlight flight() {
    return flight;
  }

  public void parse(InputStream input) {
    try {
      saxParser.parse(input, this);
    } catch (IOException e) {
      throw new FixmParseException("I/O error while parsing FIXM", e);
    } catch (SAXException e) {
      throw new FixmParseException("Malformed FIXM XML", e);
    }
    flight = new FixmFlight(departureAerodrome, estimatedOffBlockTime, actualTimeOfArrival);
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) {
    if (qName == null) return;
    currentTag = qName;
    switch (qName) {
      case FX_DEPARTURE_TAG -> departureDepth++;
      case FX_DEPARTURE_AERODROME_TAG -> {
        if (departureDepth > 0) departureAerodromeDepth++;
      }
      case FX_ARRIVAL_TAG -> arrivalDepth++;
      case FX_ACTUAL_TIME_OF_ARRIVAL_TAG -> {
        if (arrivalDepth > 0) actualTimeOfArrivalDepth++;
      }
    }
  }

  @Override
  public void characters(char[] ch, int start, int length) {
    String text = new String(ch, start, length).trim();
    if (text.isEmpty()) return;
    switch (currentTag) {
      case FX_LOCATION_INDICATOR_TAG -> {
        if (departureAerodromeDepth > 0 && departureAerodrome == null) departureAerodrome = text;
      }
      case FX_ESTIMATED_OFF_BLOCK_TIME_TAG -> {
        if (departureDepth > 0 && estimatedOffBlockTime == null)
          estimatedOffBlockTime = parseDate(text);
      }
      case FX_TIME_TAG -> {
        if (actualTimeOfArrivalDepth > 0 && actualTimeOfArrival == null)
          actualTimeOfArrival = parseDate(text);
      }
    }
  }

  @Override
  public void endElement(String uri, String localName, String qName) {
    if (qName == null) return;
    currentTag = "";
    switch (qName) {
      case FX_DEPARTURE_TAG -> departureDepth--;
      case FX_DEPARTURE_AERODROME_TAG -> departureAerodromeDepth--;
      case FX_ARRIVAL_TAG -> arrivalDepth--;
      case FX_ACTUAL_TIME_OF_ARRIVAL_TAG -> actualTimeOfArrivalDepth--;
    }
  }

  private LocalDate parseDate(String value) {
    try {
      return LocalDate.parse(value, DATE_TIME_FORMATTER);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
