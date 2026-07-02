package at.jku.dke.bigkgolap.graph;

import org.apache.jena.atlas.io.IndentedLineBuffer;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.out.NodeFormatterNT;

public final class NQuadWriter {

  private static final NodeFormatterNT FORMATTER = new NodeFormatterNT();

  private NQuadWriter() {}

  public static String write(Triple triple, String graphUri) {
    return write(triple, NodeFactory.createURI(graphUri));
  }

  public static String write(Triple triple, Node graph) {
    return composeQuad(writeBody(triple), graph);
  }

  /** Renders the {@code S P O} prefix (no graph, no trailing " ."). */
  public static String writeBody(Triple triple) {
    var buf = new IndentedLineBuffer();
    FORMATTER.format(buf, triple.getSubject());
    buf.print(' ');
    FORMATTER.format(buf, triple.getPredicate());
    buf.print(' ');
    FORMATTER.format(buf, triple.getObject());
    return buf.asString();
  }

  /** Appends the graph term to a pre-rendered body to form a full N-Quad. */
  public static String composeQuad(String body, Node graph) {
    var buf = new IndentedLineBuffer();
    buf.print(body);
    buf.print(' ');
    FORMATTER.format(buf, graph);
    buf.print(" .");
    return buf.asString();
  }
}
