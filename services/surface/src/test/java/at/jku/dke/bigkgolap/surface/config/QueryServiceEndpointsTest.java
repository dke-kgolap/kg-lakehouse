package at.jku.dke.bigkgolap.surface.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;

class QueryServiceEndpointsTest {

  @Test
  void ipv4EndpointHasHostAndPort() throws Exception {
    URI u = QueryServiceEndpoints.endpointUri(InetAddress.getByName("127.0.0.1"), 8081);
    assertThat(u.getHost()).isEqualTo("127.0.0.1");
    assertThat(u.getPort()).isEqualTo(8081);
  }

  @Test
  void ipv6EndpointIsBracketedSoHostIsNotNull() throws Exception {
    // An unbracketed IPv6 literal (http://0:0:0:0:0:0:0:1:8081) parses to a URI with a null host,
    // which the round-robin interceptor then rewrites to "http:/query" -> HTTP 400. Bracketing the
    // literal keeps the authority a valid host.
    URI u = QueryServiceEndpoints.endpointUri(InetAddress.getByName("::1"), 8081);
    assertThat(u.getHost()).isNotNull().isEqualTo("[0:0:0:0:0:0:0:1]");
    assertThat(u.getPort()).isEqualTo(8081);
  }
}
