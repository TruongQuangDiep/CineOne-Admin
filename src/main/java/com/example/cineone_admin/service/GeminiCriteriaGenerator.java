package com.example.cineone_admin.service;

import com.example.cineone_admin.exception.AiExtractionException;
import com.example.cineone_admin.config.GeminiProperties;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.function.Supplier;

public final class GeminiCriteriaGenerator implements CriteriaGenerator, AutoCloseable {
    private final GeminiProperties properties;
    private final Supplier<Client> clientFactory;
    private Client client;
    private boolean closed;

    public GeminiCriteriaGenerator(GeminiProperties properties) {
        this(properties, () -> Client.builder().apiKey(properties.getApiKey()).vertexAI(false)
                .httpOptions(HttpOptions.builder().apiVersion("v1beta")
                        .timeout(Math.toIntExact(properties.getRequestTimeout().toMillis()))
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build());
    }

    GeminiCriteriaGenerator(GeminiProperties properties, Supplier<Client> clientFactory) {
        this.properties = properties;
        this.clientFactory = clientFactory;
    }

    private synchronized Client getClient() {
        if (closed) {
            throw new AiExtractionException(AiExtractionException.Kind.CONFIGURATION);
        }
        if (client == null) {
            client = clientFactory.get();
        }
        return client;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (client != null) client.close();
    }

    @Override
    public String generate(String systemPrompt, String userContent, Map<String, Object> schema) {
        properties.validateForCall();
        try {
            return extractText(getClient().models.generateContent(properties.getModel(), userContent,
                    GenerateContentConfig.builder()
                            .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
                            .responseMimeType("application/json").responseJsonSchema(schema)
                            .candidateCount(1).maxOutputTokens(4096)
                            .store(false).build()));
        } catch (AiExtractionException e) {
            throw e;
        } catch (ApiException e) {
            throw new AiExtractionException(switch (e.code()) {
                case 408, 504 -> AiExtractionException.Kind.TIMEOUT;
                case 429 -> AiExtractionException.Kind.RATE_LIMITED;
                case 401, 403 -> AiExtractionException.Kind.CONFIGURATION;
                default -> AiExtractionException.Kind.PROVIDER_ERROR;
            });
        } catch (RuntimeException e) {
            boolean timeout = false;
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof InterruptedIOException || cause instanceof HttpTimeoutException) {
                    timeout = true;
                    break;
                }
            }
            throw new AiExtractionException(timeout ? AiExtractionException.Kind.TIMEOUT
                    : AiExtractionException.Kind.PROVIDER_ERROR);
        }
    }

    static String extractText(GenerateContentResponse response) {
        if (response == null || response.candidates().isEmpty() || response.candidates().get().size() != 1) {
            throw new AiExtractionException(AiExtractionException.Kind.BLOCKED);
        }
        var candidate = response.candidates().get().getFirst();
        if (candidate.finishReason().isEmpty() || !"STOP".equals(candidate.finishReason().get().toString())) {
            throw new AiExtractionException(AiExtractionException.Kind.BLOCKED);
        }
        String result = response.text();
        if (result == null || result.isBlank()) {
            throw new AiExtractionException(AiExtractionException.Kind.INVALID_RESPONSE);
        }
        return result;
    }
}
