package at.jku.dke.bigkgolap.engines.iwxxm.internal;

import static at.jku.dke.bigkgolap.engines.iwxxm.internal.IwxxmConstants.*;

import at.jku.dke.bigkgolap.engine.GraphBuilder;
import at.jku.dke.bigkgolap.engines.iwxxm.IwxxmParseException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class IwxxmRdfWriter extends DefaultHandler {

  private record StackEntry(String subject, String typeLocal) {}

  private final GraphBuilder builder;
  private final SAXParser saxParser;
  private final Deque<StackEntry> resourceStack = new ArrayDeque<>();
  private String previousTag = "";

  public IwxxmRdfWriter(GraphBuilder builder) {
    this.builder = builder;
    try {
      SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setNamespaceAware(NAMESPACE_AWARE);
      this.saxParser = factory.newSAXParser();
    } catch (Exception e) {
      throw new IwxxmParseException("Could not create SAX parser", e);
    }
  }

  public void parse(InputStream input) {
    try {
      saxParser.parse(input, this);
    } catch (IOException e) {
      throw new IwxxmParseException("I/O error while parsing IWXXM", e);
    } catch (SAXException e) {
      throw new IwxxmParseException("Malformed IWXXM XML", e);
    }
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) {
    if (qName == null) return;

    String gmlId = attributes != null ? attributes.getValue(GML_ID_ATT) : null;
    boolean isLakehouseTag = qName.startsWith(IWXXM_PREFIX) || qName.startsWith(AIXM_PREFIX);

    if (isLakehouseTag && gmlId != null) {
      String subject = BASE_URI + gmlId;
      String typeLocal = localPart(qName);

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
    if (!previousTag.startsWith(IWXXM_PREFIX) && !previousTag.startsWith(AIXM_PREFIX)) return;
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
