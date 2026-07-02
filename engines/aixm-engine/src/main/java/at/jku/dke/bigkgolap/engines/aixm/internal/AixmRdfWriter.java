package at.jku.dke.bigkgolap.engines.aixm.internal;

import static at.jku.dke.bigkgolap.engines.aixm.internal.AixmConstants.*;

import at.jku.dke.bigkgolap.engine.GraphBuilder;
import at.jku.dke.bigkgolap.engines.aixm.AixmParseException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class AixmRdfWriter extends DefaultHandler {

  private record StackEntry(String subject, String typeLocal) {}

  private final GraphBuilder builder;
  private final SAXParser saxParser;
  private final Deque<StackEntry> resourceStack = new ArrayDeque<>();

  private boolean inHasMember;
  private boolean rootElemFound;
  private int capturingModeCount;
  private String previousTag = "";

  public AixmRdfWriter(GraphBuilder builder) {
    this.builder = builder;
    try {
      SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setNamespaceAware(NAMESPACE_AWARE);
      this.saxParser = factory.newSAXParser();
    } catch (Exception e) {
      throw new AixmParseException("Could not create SAX parser", e);
    }
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

    if (MESSAGE_HAS_MEMBER.equals(qName)) {
      beginFeature();
      previousTag = qName;
      return;
    }

    if (!inHasMember) {
      previousTag = qName;
      return;
    }

    if (!qName.startsWith(AIXM_PREFIX) && !qName.startsWith(EVENT_PREFIX)) {
      previousTag = qName;
      return;
    }

    // First AIXM/event tag inside hasMember → root feature subject.
    if (!rootElemFound && MESSAGE_HAS_MEMBER.equals(previousTag)) {
      String gmlId = attributes != null ? attributes.getValue(GML_ID_ATT) : null;
      String subject = BASE_URI + (gmlId != null ? gmlId : "anon");
      String typeLocal = featureLocalName(qName);
      // rdf:type object is an IRI (BASE_URI + localName), not a literal, so RDFS subClassOf
      // reasoning can fire and the type aligns with the generated TBox class IRIs.
      builder.addTriple(subject, RDF_TYPE, BASE_URI + typeLocal);
      resourceStack.addLast(new StackEntry(subject, typeLocal));
      rootElemFound = true;
      previousTag = qName;
      return;
    }

    if (capturingModeCount > 0) {
      String gmlId = attributes != null ? attributes.getValue(GML_ID_ATT) : null;
      if (gmlId != null) {
        String subject = BASE_URI + gmlId;
        String typeLocal = featureLocalName(qName);
        StackEntry parent = resourceStack.peekLast();
        if (parent != null) {
          String predicate = BASE_URI + featureLocalName(previousTag);
          builder.addTriple(parent.subject(), predicate, subject, false);
        }
        builder.addTriple(subject, RDF_TYPE, BASE_URI + typeLocal);
        resourceStack.addLast(new StackEntry(subject, typeLocal));
      }
    }

    if (SUPPORTED_CONCEPTS.contains(qName) || SUPPORTED_EVENT_CONCEPTS.contains(qName)) {
      capturingModeCount++;
    }

    previousTag = qName;
  }

  @Override
  public void characters(char[] ch, int start, int length) {
    if (capturingModeCount == 0 || resourceStack.isEmpty()) return;
    String text = new String(ch, start, length);
    if (text.isBlank()) return;
    if (!previousTag.startsWith(AIXM_PREFIX) && !previousTag.startsWith(EVENT_PREFIX)) return;
    StackEntry parent = resourceStack.peekLast();
    String predicate = BASE_URI + featureLocalName(previousTag);
    builder.addTriple(parent.subject(), predicate, text, true);
  }

  @Override
  public void endElement(String uri, String localName, String qName) {
    if (qName == null) return;

    if (MESSAGE_HAS_MEMBER.equals(qName)) {
      endFeature();
      return;
    }

    if (!inHasMember) return;

    if (SUPPORTED_CONCEPTS.contains(qName) || SUPPORTED_EVENT_CONCEPTS.contains(qName)) {
      capturingModeCount--;
    }

    if ((qName.startsWith(AIXM_PREFIX) || qName.startsWith(EVENT_PREFIX))
        && !resourceStack.isEmpty()) {
      String typeLocal = featureLocalName(qName);
      StackEntry top = resourceStack.peekLast();
      if (top != null && typeLocal.equals(top.typeLocal())) {
        resourceStack.removeLast();
      }
    }
  }

  private void beginFeature() {
    inHasMember = true;
    rootElemFound = false;
    capturingModeCount = 0;
    resourceStack.clear();
  }

  private void endFeature() {
    inHasMember = false;
    rootElemFound = false;
    capturingModeCount = 0;
    resourceStack.clear();
  }

  private String featureLocalName(String qName) {
    if (qName.startsWith(AIXM_PREFIX)) return qName.substring(AIXM_PREFIX.length());
    if (qName.startsWith(EVENT_PREFIX)) return qName.substring(EVENT_PREFIX.length());
    return qName;
  }
}
