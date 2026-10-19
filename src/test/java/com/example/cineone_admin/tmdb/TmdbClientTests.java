package com.example.cineone_admin.tmdb;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class TmdbClientTests {
    private HttpServer server;
    private ExecutorService executor;
    private TmdbProperties properties;
    private TmdbClient client;
    private volatile int status = 200;
    private volatile String body = "{}";
    private volatile long delay;
    private volatile long bodyDelay;
    private final AtomicReference<String> uri = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/3/", exchange -> {
            uri.set(exchange.getRequestURI().toASCIIString());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try {
                Thread.sleep(delay);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                Thread.sleep(bodyDelay);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally { exchange.close(); }
        });
        server.start();
        properties = new TmdbProperties();
        properties.setAccessToken("test-token");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/3");
        properties.setRequestTimeout(Duration.ofSeconds(3));
        client = new TmdbConfiguration().tmdbClient(properties);
    }

    @AfterEach void cleanup() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test void detailsRequestIncludesCreditsVideosLanguageAndBearer() {
        body = """
                {"id":42,"title":"Phim","runtime":120,"genres":[{"id":28,"name":"Hành động"}],
                 "credits":{"crew":[{"job":"Director","name":"Director"}],"cast":[]},
                 "videos":{"results":[]},"unknown_field":"ignored"}
                """;
        var result = client.details(42);
        assertEquals("Hành động", result.genres().getFirst().name());
        assertEquals("Director", result.credits().crew().getFirst().name());
        assertTrue(uri.get().startsWith("/3/movie/42?"));
        assertTrue(uri.get().contains("append_to_response=credits%2Cvideos"));
        assertTrue(uri.get().contains("language=vi-VN"));
        assertEquals("Bearer test-token", authorization.get());
        assertFalse(uri.get().contains("test-token"));
    }

    @Test void allListsAndSearchRetainPaginationAndEncodeQuery() {
        body = "{\"page\":2,\"total_pages\":3,\"total_results\":41,\"results\":[{\"id\":42}]}";
        for (var list : TmdbClient.MovieList.values()) {
            var result = client.movies(list, 2);
            assertTrue(result.hasNext());
            assertEquals(41L, result.totalResults());
            assertTrue(uri.get().contains("/movie/" + list.name().toLowerCase()));
            assertTrue(uri.get().contains("page=2"));
        }
        client.search("A&B + {phim}", 2);
        assertTrue(uri.get().contains("query=A%26B%20%2B%20%7Bphim%7D"));
    }

    @Test void lastAndEmptyPagesStopAndApiLimitIsHonored() {
        body = "{\"page\":1,\"total_pages\":0,\"total_results\":0,\"results\":[]}";
        assertFalse(client.search("empty", 1).hasNext());
        body = "{\"page\":500,\"total_pages\":900,\"total_results\":18000,\"results\":[]}";
        assertFalse(client.movies(TmdbClient.MovieList.POPULAR, 500).hasNext());
        assertThrows(IllegalArgumentException.class, () -> client.search("x", 0));
        assertThrows(IllegalArgumentException.class, () -> client.search("x", 501));
        assertThrows(IllegalArgumentException.class, () -> client.search(" ", 1));
    }

    @ParameterizedTest @ValueSource(ints = {401, 404, 429, 500})
    void httpFailuresAreTypedAndDoNotExposeResponseBody(int code) {
        status = code;
        body = "test-token-sensitive-response";
        var ex = assertThrows(TmdbException.class, () -> client.details(42));
        assertEquals(TmdbException.Kind.HTTP, ex.getKind());
        assertEquals(code, ex.getStatusCode());
        assertFalse(ex.getMessage().contains("test-token"));
        assertNull(ex.getCause());
    }

    @Test void timeoutIncludesWaitingForHeaders() {
        properties.setRequestTimeout(Duration.ofMillis(100));
        delay = 500;
        assertEquals(TmdbException.Kind.TIMEOUT, assertThrows(TmdbException.class, () -> client.details(42)).getKind());
    }

    @Test void timeoutIncludesSlowBody() {
        properties.setRequestTimeout(Duration.ofMillis(200));
        body = "{\"id\":42}";
        bodyDelay = 800;
        assertEquals(TmdbException.Kind.TIMEOUT, assertThrows(TmdbException.class, () -> client.details(42)).getKind());
    }

    @Test void malformedEmptyAndWrongIdentityResponsesFail() {
        for (String bad : new String[]{"not-json", "", "null", "{\"id\":43}"}) {
            body = bad;
            assertEquals(TmdbException.Kind.INVALID_RESPONSE,
                    assertThrows(TmdbException.class, () -> client.details(42)).getKind());
        }
        body = "{\"page\":2,\"total_pages\":3,\"total_results\":40,\"results\":[]}";
        assertThrows(TmdbException.class, () -> client.search("x", 1));
    }

    @Test void genresAndMissingToken() {
        body = "{\"genres\":[{\"id\":28,\"name\":\"Action\"}]}";
        assertEquals(28L, client.genres().genres().getFirst().id());
        properties.setAccessToken("");
        assertEquals(TmdbException.Kind.CONFIGURATION,
                assertThrows(TmdbException.class, () -> client.details(42)).getKind());
    }
}
