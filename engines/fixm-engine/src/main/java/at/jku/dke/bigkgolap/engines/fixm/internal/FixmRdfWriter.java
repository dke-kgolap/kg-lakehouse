package at.jku.dke.bigkgolap.engines.fixm.internal;

import static at.jku.dke.bigkgolap.engines.fixm.internal.FixmConstants.*;

import at.jku.dke.bigkgolap.engine.GraphBuilder;
import at.jku.dke.bigkgolap.engines.fixm.FixmParseException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class FixmRdfWriter extends DefaultHandler {

  private record StackEntry(String subject, String typeLocal) {}

  private final GraphBuilder builder;
  private final SAXParser saxParser;
  private final Deque<StackEntry> resourceStack = new ArrayDeque<>();
  private String previousTag = "";
  private boolean rootFound;

  public FixmRdfWriter(GraphBuilder builder) {
    this.builder = builder;
    try {
      SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setNamespaceAware(NAMESPACE_AWARE);
      this.saxParser = factory.newSAXParser();
    } catch (Exception e) {
      throw new FixmParseException("Could not create SAX parser", e);
    }
  }

  public void parse(InputStream input) {
    try {
      saxParser.parse(input, this);
    } catch (IOException e) {
      throw new FixmParseException("I/O error while parsing FIXM", e);
    } catch (SAXException e) {
      throw new FixmParseException("Malformed FIXM XML", e);
    }
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) {
    if (qName == null) return;
    boolean isFixmTag = qName.startsWith(FX_PREFIX) || qName.startsWith(FB_PREFIX);
    if (!isFixmTag) {
      previousTag = qName;
      return;
    }
    String gmlId = attributes != null ? attributes.getValue(GML_ID_ATT) : null;
    String typeLocal = localPart(qName);

    // The root feature (e.g. fx:Flight) is always typed, even without a gml:id — FIXM messages
    // carry no gml:id, so without this the whole graph (incl. the rdf:type the TBox generalizes)
    // would be empty. A generated subject keeps distinct features distinct.
    if (!rootFound) {
      String subject = BASE_URI + (gmlId != null ? gmlId : typeLocal + "-" + UUID.randomUUID());
      builder.addTriple(subject, RDF_TYPE, BASE_URI + typeLocal);
      resourceStack.addLast(new StackEntry(subject, typeLocal));
      rootFound = true;
      previousTag = qName;
      return;
    }

    // Route trajectory elements (no gml:id) → typed as the domain class RouteTrajectoryElement and
    // linked to the enclosing feature, so the TBox generalizes them to Trajectory ⊑ Flights.
    if (FX_ELEMENT_TAG.equals(qName)) {
      String subject = BASE_URI + ROUTE_TRAJECTORY_ELEMENT_TYPE + "-" + UUID.randomUUID();
      StackEntry parent = resourceStack.peekLast();
      if (parent != null) {
        builder.addTriple(
            parent.subject(), BASE_URI + ROUTE_TRAJECTORY_ELEMENT_PREDICATE, subject, false);
      }
      builder.addTriple(subject, RDF_TYPE, BASE_URI + ROUTE_TRAJECTORY_ELEMENT_TYPE);
      resourceStack.addLast(new StackEntry(subject, typeLocal)); // typeLocal "element" for pop
      previousTag = qName;
      return;
    }

    if (gmlId != null) {
      String subject = BASE_URI + gmlId;
      StackEntry parent = resourceStack.peekLast();
      if (parent != null) {
        String predicate = BASE_URI + localPart(previousTag.isEmpty() ? qName : previousTag);
        builder.addTriple(parent.subject(), predicate, subject, false);
      }
      // rdf:type as an IRI (BASE_URI + localName), not a literal, so RDFS subClassOf reasoning
      // can fire and the type aligns with the generated TBox class IRIs.
      builder.addTriple(subject, RDF_TYPE, BASE_URI + typeLocal);
      resourceStack.addLast(new StackEntry(subject, typeLocal));
    }

    previousTag = qName;
  }

  @Override
  public void characters(char[] ch, int start, int length) {
    if (resourceStack.isEmpty()) return;
    String text = new String(ch, start, length).trim();
    if (text.isEmpty()) return;
    if (!previousTag.startsWith(FX_PREFIX) && !previousTag.startsWith(FB_PREFIX)) return;
    StackEntry parent = resourceStack.peekLast();
    String predicate = BASE_URI + localPart(previousTag);
    builder.addTriple(parent.subject(), predicate, text, true);
  }

  @Override
  public void endElement(String uri, String localName, String qName) {
    if (qName == null || resourceStack.isEmpty()) return;
    String typeLocal = localPart(qName);
    StackEntry top = resourceStack.peekLast();
    if (top != null && typeLocal.equals(top.typeLocal())) {
      resourceStack.removeLast();
    }
  }

  private String localPart(String qName) {
    int colon = qName.indexOf(':');
    return colon >= 0 ? qName.substring(colon + 1) : qName;
  }
}
