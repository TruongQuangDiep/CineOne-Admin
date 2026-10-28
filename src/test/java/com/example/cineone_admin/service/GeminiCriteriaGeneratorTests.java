package com.example.cineone_admin.service;

import com.example.cineone_admin.exception.AiExtractionException;
import com.example.cineone_admin.config.GeminiConfiguration;
import com.example.cineone_admin.config.GeminiProperties;
import com.example.cineone_admin.config.SearchTimeProperties;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
class GeminiCriteriaGeneratorTests {
    private HttpServer server;
    private ExecutorService executor;
    private GeminiProperties properties;
    private volatile int status = 200;
    private volatile String body;
    private volatile long delay;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private final AtomicReference<String> uri = new AtomicReference<>();
    private final AtomicReference<String> request = new AtomicReference<>();

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            apiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            uri.set(exchange.getRequestURI().toASCIIString());
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delay);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        properties = new GeminiProperties();
        properties.setApiKey("synthetic-test-key");
        properties.setRequestTimeout(Duration.ofSeconds(3));
        body = response(CriteriaParserTests.EMPTY, "STOP");
    }

    @AfterEach void cleanup() {
        server.stop(0);
        executor.shutdownNow();
    }

    private GeminiCriteriaGenerator generator() {
        return new GeminiCriteriaGenerator(properties, () -> Client.builder()
                .apiKey(properties.getApiKey()).vertexAI(false)
                .httpOptions(HttpOptions.builder()
                        .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                        .apiVersion("v1beta")
                        .timeout(Math.toIntExact(properties.getRequestTimeout().toMillis()))
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build());
    }

    private String generate() {
        var contract = new CriteriaContract();
        return generator().generate(contract.systemPrompt(), "phim ma ngày mai", contract.schema());
    }

    @Test void realSdkSendsSchemaSystemInstructionAndHeaderKeyAndParsesResult() {
        assertEquals(CriteriaParserTests.EMPTY, generate());
        assertEquals("synthetic-test-key", apiKey.get());
        assertEquals("/v1beta/models/gemini-3.5-flash-lite:generateContent", uri.get());
        assertFalse(uri.get().contains("synthetic-test-key"));
        JsonNode tree = new JsonMapper().readTree(request.get());
        assertEquals("application/json", tree.at("/generationConfig/responseMimeType").asString());
        assertEquals(5, tree.at("/generationConfig/responseJsonSchema/required").size());
        assertFalse(tree.at("/generationConfig/responseJsonSchema/additionalProperties").asBoolean());
        assertTrue(tree.at("/systemInstruction/parts/0/text").asString().contains("CineOne"));
        assertEquals("phim ma ngày mai", tree.at("/contents/0/parts/0/text").asString());
        assertFalse(tree.has("tools"));
        assertFalse(request.get().contains("synthetic-test-key"));
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @CsvSource({"400,PROVIDER_ERROR", "401,CONFIGURATION", "403,CONFIGURATION",
            "429,RATE_LIMITED", "500,PROVIDER_ERROR", "504,TIMEOUT"})
    void mapsProviderErrorsWithoutLeakingBodiesOrRetrying(int code, String expected) {
        status = code;
        body = "{\"error\":{\"code\":" + code + ",\"message\":\"sensitive-provider-body\"}}";
        var error = assertThrows(AiExtractionException.class, this::generate);
        assertEquals(AiExtractionException.Kind.valueOf(expected), error.getKind());
        assertFalse(error.toString().contains("sensitive-provider-body"));
        assertNull(error.getCause());
        assertEquals(1, calls.get());
    }

    @Test void timesOutWithoutRetryOrRawDiagnostic() {
        properties.setRequestTimeout(Duration.ofMillis(150));
        delay = 800;
        var error = assertThrows(AiExtractionException.class, this::generate);
        assertEquals(AiExtractionException.Kind.TIMEOUT, error.getKind());
        assertTrue(calls.get() <= 1);
        assertNull(error.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SAFETY", "MAX_TOKENS", "RECITATION", "OTHER"})
    void rejectsIncompleteOrBlockedCandidatesEvenIfTextContainsValidJson(String reason) {
        body = response(CriteriaParserTests.EMPTY, reason);
        assertEquals(AiExtractionException.Kind.BLOCKED,
                assertThrows(AiExtractionException.class, this::generate).getKind());
    }

    @Test void rejectsEmptyResponseAndMalformedProviderJson() {
        body = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";
        assertEquals(AiExtractionException.Kind.BLOCKED,
                assertThrows(AiExtractionException.class, this::generate).getKind());
        body = "not json";
        var error = assertThrows(AiExtractionException.class, this::generate);
        assertNull(error.getCause());
        assertFalse(error.getMessage().contains("not json"));
    }

    @Test void missingKeyDoesNotMakeNetworkCallAndOtherFeaturesCanStillBoot() {
        properties.setApiKey("");
        var service = new GeminiConfiguration().searchCriteriaExtractor(
                new GeminiCriteriaGenerator(properties), new SearchTimeProperties());
        assertNotNull(service);
        assertEquals(AiExtractionException.Kind.CONFIGURATION,
                assertThrows(AiExtractionException.class, () -> service.extract("phim ma")).getKind());
        assertEquals(0, calls.get());
    }

    @Test void springBindsConfigurationWithoutKeyAndDoesNotContactProvider() {
        new ApplicationContextRunner().withUserConfiguration(GeminiConfiguration.class)
                .withPropertyValues("gemini.api-key=", "gemini.model=gemini-3.5-flash-lite",
                        "gemini.request-timeout=7s", "ai.search.time.approximate-minutes=45",
                        "ai.search.time.evening-start=17:30")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(SearchCriteriaExtractor.class));
                    assertEquals(Duration.ofSeconds(7), context.getBean(GeminiProperties.class).getRequestTimeout());
                    assertEquals(45, context.getBean(SearchTimeProperties.class).getApproximateMinutes());
                    assertEquals(java.time.LocalTime.of(17, 30),
                            context.getBean(SearchTimeProperties.class).getEveningStart());
                });
        assertEquals(0, calls.get());
    }

    @Test void validatesModelAndTimeoutBeforeCallingSdk() {
        properties.setModel("https://untrusted.invalid/model");
        assertThrows(AiExtractionException.class, this::generate);
        properties.setModel("gemini-3.5-flash-lite");
        properties.setRequestTimeout(Duration.ZERO);
        assertThrows(AiExtractionException.class, this::generate);
        properties.setRequestTimeout(Duration.ofSeconds(61));
        assertThrows(AiExtractionException.class, this::generate);
        assertEquals(0, calls.get());
    }

    private static String response(String text, String finishReason) {
        return new JsonMapper().writeValueAsString(Map.of("candidates", java.util.List.of(Map.of(
                "finishReason", finishReason,
                "content", Map.of("role", "model", "parts", java.util.List.of(Map.of("text", text)))))));
    }
}
