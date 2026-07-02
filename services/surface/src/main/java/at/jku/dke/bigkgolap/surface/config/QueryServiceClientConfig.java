package at.jku.dke.bigkgolap.surface.config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
@Profile("!test")
public class QueryServiceClientConfig {

  @Bean
  public RestClient queryServiceRestClient(
      LakehouseProperties props, QueryServiceEndpoints endpoints) {
    var readTimeout = Duration.ofSeconds(props.services().query().readTimeoutSeconds());
    var httpClient = HttpClient.newBuilder().connectTimeout(readTimeout).build();
    var factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(readTimeout);
    return RestClient.builder()
        // Base URL is the (now headless) Service name; it serves as the fallback target before any
        // pod IP is resolved. The interceptor rewrites the host to a specific pod per request.
        .baseUrl(
            "http://" + props.services().query().host() + ":" + props.services().query().httpPort())
        .requestFactory(factory)
        .requestInterceptor(new RoundRobinEndpointInterceptor(endpoints))
        .build();
  }

  /**
   * Rewrites each outbound request's host:port to the next query-service pod (round-robin across
   * the headless Service's resolved pod IPs), so requests fan out across all replicas instead of
   * pinning to one keepalive connection. Path, query, headers and body are left untouched.
   */
  private static final class RoundRobinEndpointInterceptor implements ClientHttpRequestInterceptor {

    private final QueryServiceEndpoints endpoints;

    RoundRobinEndpointInterceptor(QueryServiceEndpoints endpoints) {
      this.endpoints = endpoints;
    }

    @Override
    public ClientHttpResponse intercept(
        HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
      URI target = endpoints.next();
      if (target == null) {
        return execution.execute(request, body); // no pod resolved yet — use the base URL
      }
      URI rewritten =
          UriComponentsBuilder.fromUri(request.getURI())
              .scheme(target.getScheme())
              .host(target.getHost())
              .port(target.getPort())
              .build(true)
              .toUri();
      return execution.execute(
          new HttpRequestWrapper(request) {
            @Override
            public URI getURI() {
              return rewritten;
            }
          },
          body);
    }
  }
}
