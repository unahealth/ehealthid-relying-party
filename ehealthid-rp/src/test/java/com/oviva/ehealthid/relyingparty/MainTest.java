package com.oviva.ehealthid.relyingparty;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.oviva.ehealthid.relyingparty.cfg.ConfigProvider;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MainTest {

  private static final String DISCOVERY_PATH = "/.well-known/openid-configuration";
  private static final String FEDERATION_CONFIG_PATH = "/.well-known/openid-federation";
  private static final String JWKS_PATH = "/jwks.json";
  private static final String HEALTH_PATH = "/health";
  private static final String METRICS_PATH = "/metrics";

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static Main application;

  @BeforeAll
  static void beforeAll() throws ExecutionException, InterruptedException {

    var discoveryUri = URI.create(wm.baseUrl()).resolve(DISCOVERY_PATH);

    var config =
        configFromProperties(
            """
    federation_enc_jwks_path=src/test/resources/fixtures/example_enc_jwks.json
    federation_sig_jwks_path=src/test/resources/fixtures/example_sig_jwks.json
    base_uri=%s
    idp_discovery_uri=%s
    app_name=Awesome DiGA
    port=0
    """
                .formatted(wm.baseUrl(), discoveryUri));

    application = new Main(config);

    // when
    application.start();
  }

  @AfterAll
  static void afterAll() throws Exception {
    application.close();
  }

  private static ConfigProvider configFromProperties(String s) {
    var props = new Properties();
    try {
      props.load(new StringReader(s));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return new StaticConfig(props);
  }

  @Test
  void run_smokeTest() throws Exception {

    var baseUri = application.baseUri();

    // then
    tryGet(baseUri.resolve(DISCOVERY_PATH));
    tryGet(baseUri.resolve(JWKS_PATH));
    tryGet(baseUri.resolve(FEDERATION_CONFIG_PATH));
    tryGet(baseUri.resolve(HEALTH_PATH));
    tryGet(baseUri.resolve(METRICS_PATH));
  }

  @Test
  void run_metrics() throws Exception {

    var baseUri = application.baseUri();

    // when
    var body = tryGet(baseUri.resolve(METRICS_PATH));

    // then
    var metrics = new String(body, StandardCharsets.UTF_8);
    assertTrue(metrics.contains("cache_gets_total{cache=\"sessionCache\""));
    assertTrue(metrics.contains("cache_gets_total{cache=\"codeCache\""));
    assertTrue(metrics.contains("jvm_memory_used_bytes{area=\"heap\""));
    assertTrue(metrics.contains("jvm_gc_memory_allocated_bytes_total "));
  }

  private byte[] tryGet(URI uri) throws IOException, InterruptedException {

    var client = HttpClient.newHttpClient();
    for (int i = 0; i < 100; i++) {
      var req = HttpRequest.newBuilder(uri).GET().build();

      var res = client.send(req, BodyHandlers.ofByteArray());
      if (res.statusCode() == 200) {
        return res.body();
      }
      Thread.sleep(Duration.ofMillis(500).toMillis());
    }
    fail();
    return null;
  }

  record StaticConfig(Map<Object, Object> values) implements ConfigProvider {

    @Override
    public Optional<String> get(String name) {
      return Optional.ofNullable(values.get(name)).map(Object::toString);
    }
  }
}
