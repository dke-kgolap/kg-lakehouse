package at.jku.dke.bigkgolap.engines.iwxxm.internal;

import static at.jku.dke.bigkgolap.engines.iwxxm.internal.IwxxmConstants.*;

import at.jku.dke.bigkgolap.engines.iwxxm.IwxxmParseException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class IwxxmContextExtractor extends DefaultHandler {

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      new DateTimeFormatterBuilder()
          .appendOptional(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
          .appendOptional(DateTimeFormatter.ISO_DATE_TIME)
          .appendOptional(DateTimeFormatter.ISO_DATE)
          .toFormatter();

  private final SAXParser saxParser;

  private IwxxmReport report;
  private String rootName;

  private int aerodromeDepth;
  private int affectedFirDepth;
  private int issueTimeDepth;
  private int validPeriodDepth;

  private String currentTag = "";
  private String location;
  private LocalDate issueDate;
  private LocalDate validBegin;
  private LocalDate validEnd;

  public IwxxmContextExtractor() {
    try {
      SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setNamespaceAware(NAMESPACE_AWARE);
      this.saxParser = factory.newSAXParser();
    } catch (Exception e) {
      throw new IwxxmParseException("Could not create SAX parser", e);
    }
  }

  public IwxxmReport report() {
    return report;
  }

  public void parse(InputStream input) {
    try {
      saxParser.parse(input, this);
      // validPeriod (TAF/SIGMET) takes precedence over issueTime (METAR's only time signal).
      LocalDate from = validBegin != null ? validBegin : issueDate;
      LocalDate until = validEnd != null ? validEnd : issueDate;
      report = new IwxxmReport(rootName, location, from, until);
    } catch (IOException e) {
      throw new IwxxmParseException("I/O error while parsing IWXXM", e);
    } catch (SAXException e) {
      throw new IwxxmParseException("Malformed IWXXM XML", e);
    }
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) {
    if (qName == null) return;
    currentTag = qName;

    // First IWXXM-prefixed element latches as the root.
    if (rootName == null && qName.startsWith(IWXXM_PREFIX)) {
      rootName = qName.substring(IWXXM_PREFIX.length());
    }

    switch (qName) {
      case AERODROME_TAG -> {
        aerodromeDepth++;
        if (location == null && attributes != null) {
          String href = attributes.getValue(XLINK_HREF_ATT);
          if (href != null) location = extractIcao(href);
        }
      }
      case AFFECTED_FIR_TAG -> {
        affectedFirDepth++;
        if (location == null && attributes != null) {
          String href = attributes.getValue(XLINK_HREF_ATT);
          if (href != null) location = extractIcao(href);
        }
      }
      case ISSUING_ATS_REGION_TAG -> {
        if (location == null && attributes != null) {
          String href = attributes.getValue(XLINK_HREF_ATT);
          if (href != null) location = extractIcao(href);
        }
      }
      case ISSUE_TIME_TAG -> issueTimeDepth++;
      case VALID_PERIOD_TAG -> validPeriodDepth++;
    }
  }

  @Override
  public void characters(char[] ch, int start, int length) {
    String text = new String(ch, start, length).trim();
    if (text.isEmpty()) return;

    if (AIXM_DESIGNATOR_TAG.equals(currentTag) && (aerodromeDepth > 0 || affectedFirDepth > 0)) {
      if (location == null) location = text;
      return;
    }

    switch (currentTag) {
      case TIME_INSTANT_POSITION_TAG -> {
        if (issueTimeDepth > 0 && issueDate == null) issueDate = parseDate(text);
      }
      case BEGIN_POSITION_TAG -> {
        if (validPeriodDepth > 0 && validBegin == null) validBegin = parseDate(text);
      }
      case END_POSITION_TAG -> {
        if (validPeriodDepth > 0 && validEnd == null) validEnd = parseDate(text);
      }
    }
  }

  @Override
  public void endElement(String uri, String localName, String qName) {
    if (qName == null) return;
    currentTag = "";
    switch (qName) {
      case AERODROME_TAG -> aerodromeDepth--;
      case AFFECTED_FIR_TAG -> affectedFirDepth--;
      case ISSUE_TIME_TAG -> issueTimeDepth--;
      case VALID_PERIOD_TAG -> validPeriodDepth--;
    }
  }

  private String extractIcao(String href) {
    return Arrays.stream(href.split("\\."))
        .filter(s -> ICAO_IN_HREF.matcher(s).matches())
        .findFirst()
        .orElse(null);
  }

  private LocalDate parseDate(String value) {
    try {
      return LocalDate.parse(value, DATE_TIME_FORMATTER);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
