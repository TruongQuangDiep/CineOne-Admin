package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.ai.FaqAnswerResponse;
import com.example.cineone_admin.entity.KnowledgeBaseEntry;
import com.example.cineone_admin.exception.AiExtractionException;
import com.example.cineone_admin.repository.KnowledgeBaseEntryRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly = true)
public class FaqRagService {
    public static final int DEFAULT_MAX_CONTEXT_ENTRIES = 8;
    public static final String NO_KNOWLEDGE_MESSAGE =
            "Hiện chưa có thông tin phù hợp trong Knowledge Base để giải đáp câu hỏi này.";

    private static final Set<String> STOP_WORDS = Set.of(
            "la", "co", "va", "cua", "cho", "khi", "thi", "nao", "sao", "gi",
            "de", "duoc", "khong", "nhu", "the", "tai", "trong", "voi", "ve",
            "mot", "nhung", "cac", "nay", "do", "neu", "hay", "hoac", "lam"
    );

    private static final List<Pattern> FAQ_PRIVATE_DATA = List.of(
            Pattern.compile("[\\p{L}\\p{N}._%+-]+\\s*@\\s*[\\p{L}\\p{N}.-]+", Pattern.UNICODE_CHARACTER_CLASS),
            Pattern.compile("(?<!\\d)(?:\\+?\\d[ .()-]*){9,}(?!\\d)"),
            Pattern.compile("(?i)\\b(?:eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+|AIza[A-Za-z0-9_-]{20,})"),
            Pattern.compile("(?i)\\b(?:bearer\\s+[A-Za-z0-9._-]+|api[ _-]?key\\s*[:=]\\s*\\S+)"),
            Pattern.compile("(?i)\\b(?:otp|cvv|cccd|cmnd|iban|pin|ma xac thuc)\\s*(?:la|is|:|=)?\\s*\\d{3,}"),
            Pattern.compile("(?i)\\b(?:password|passwd|mat khau|secret|token|so tai khoan|so the|ho chieu)\\s*(?:cua\\s+(?:toi|tui|minh)\\s*)?(?:la|is|:|=)\\s*\\S+"),
            Pattern.compile("\\b(?:ho ten|toi ten|tui ten|ten toi|ten tui)\\b"),
            Pattern.compile("\\b(?:dia chi nha|nha toi o|nha tui o|toi song (?:o|tai)|tui song (?:o|tai)|toi o so|tui o so|(?:toi|tui|minh) o \\d+)\\b"),
            Pattern.compile("\\b\\d+[a-z]?(?:/\\d+[a-z]?)*\\s+(?:duong|hem|ngo|ngach|street|road)\\b"),
            Pattern.compile("(?<![\\d.])-?\\d{1,3}\\.\\d{3,}\\s*[,;]\\s*-?\\d{1,3}\\.\\d{3,}(?![\\d.])")
    );

    private final CriteriaGenerator generator;
    private final KnowledgeBaseEntryRepository knowledgeBaseRepository;
    private final int maxContextEntries;
    private final String systemPrompt;
    private final Map<String, Object> schema;
    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    @Autowired
    public FaqRagService(CriteriaGenerator generator,
                         KnowledgeBaseEntryRepository knowledgeBaseRepository) {
        this(generator, knowledgeBaseRepository, DEFAULT_MAX_CONTEXT_ENTRIES);
    }

    @SuppressWarnings("unchecked")
    public FaqRagService(CriteriaGenerator generator,
                         KnowledgeBaseEntryRepository knowledgeBaseRepository,
                         int maxContextEntries) {
        if (maxContextEntries < 1 || maxContextEntries > 50) {
            throw new IllegalArgumentException("maxContextEntries phải từ 1 đến 50.");
        }
        this.generator = generator;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.maxContextEntries = maxContextEntries;
        try {
            this.systemPrompt = readResource("ai/faq-rag-system-prompt.txt");
            this.schema = mapper.readValue(readResource("ai/faq-rag-response.schema.json"), Map.class);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Không thể đọc hợp đồng RAG FAQ AI.");
        }
    }

    public FaqAnswerResponse answer(String question) {
        return answer(question, null);
    }

    public FaqAnswerResponse answer(String question, String category) {
        String safeQuestion = validateQuestion(question);
        String safeCategory = validateCategory(category);

        List<KnowledgeBaseEntry> activeEntries = safeCategory == null
                ? knowledgeBaseRepository.findByStatusOrderByKnowledgeIdAsc("ACTIVE")
                : knowledgeBaseRepository.findByCategoryIgnoreCaseAndStatusOrderByKnowledgeIdAsc(safeCategory, "ACTIVE");

        List<KnowledgeBaseEntry> selectedEntries = selectRelevantEntries(safeQuestion, activeEntries);
        if (selectedEntries.isEmpty()) {
            return new FaqAnswerResponse(safeQuestion, false, NO_KNOWLEDGE_MESSAGE, List.of());
        }

        Map<Long, KnowledgeBaseEntry> entryMap = new LinkedHashMap<>();
        List<Map<String, Object>> contextItems = new ArrayList<>(selectedEntries.size());
        for (KnowledgeBaseEntry entry : selectedEntries) {
            entryMap.put(entry.getKnowledgeId(), entry);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("knowledge_id", entry.getKnowledgeId());
            item.put("category", entry.getCategory() == null ? "FAQ" : entry.getCategory().strip());
            item.put("title", sanitizeContextText(entry.getTitle(), 255));
            item.put("content", sanitizeContextText(entry.getContent(), 2000));
            contextItems.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", safeQuestion);
        payload.put("knowledge_entries", contextItems);

        String rawResponse = generator.generate(systemPrompt, mapper.writeValueAsString(payload), schema);
        return parseAndVerify(safeQuestion, rawResponse, entryMap);
    }

    List<KnowledgeBaseEntry> selectRelevantEntries(String question, List<KnowledgeBaseEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        String foldedQuestion = fold(question);
        Set<String> queryTokens = extractTokens(foldedQuestion);

        record ScoredEntry(KnowledgeBaseEntry entry, int score, int order) {}
        List<ScoredEntry> scored = new ArrayList<>();
        int index = 0;
        for (KnowledgeBaseEntry entry : entries) {
            if (entry == null || entry.getKnowledgeId() == null
                    || entry.getTitle() == null || entry.getTitle().isBlank()
                    || entry.getContent() == null || entry.getContent().isBlank()
                    || (entry.getStatus() != null && !"ACTIVE".equalsIgnoreCase(entry.getStatus().strip()))) {
                continue;
            }
            String foldedTitle = fold(entry.getTitle());
            String foldedContent = fold(entry.getContent());
            Set<String> titleTokens = extractTokens(foldedTitle);
            Set<String> contentTokens = extractTokens(foldedContent);

            int score = 0;
            if (!foldedQuestion.isEmpty()) {
                if (foldedTitle.contains(foldedQuestion) || foldedQuestion.contains(foldedTitle)) {
                    score += 10;
                }
                if (foldedContent.contains(foldedQuestion)) {
                    score += 5;
                }
            }
            for (String token : queryTokens) {
                if (titleTokens.contains(token)) {
                    score += 3;
                } else if (foldedTitle.contains(token)) {
                    score += 2;
                }
                if (contentTokens.contains(token)) {
                    score += 1;
                }
            }
            scored.add(new ScoredEntry(entry, score, index++));
        }

        scored.sort(Comparator.comparingInt(ScoredEntry::score).reversed()
                .thenComparingLong(s -> s.entry().getKnowledgeId())
                .thenComparingInt(ScoredEntry::order));

        return scored.stream()
                .limit(maxContextEntries)
                .map(ScoredEntry::entry)
                .toList();
    }

    private FaqAnswerResponse parseAndVerify(String question,
                                             String json,
                                             Map<Long, KnowledgeBaseEntry> allowedEntries) {
        try {
            if (json == null || json.isBlank() || json.length() > 16_384) {
                throw invalidResponse();
            }
            JsonNode root = mapper.readTree(json);
            requireExactKeys(root, "answered", "answer", "source_ids");

            JsonNode answeredNode = root.get("answered");
            if (answeredNode == null || !answeredNode.isBoolean()) {
                throw invalidResponse();
            }
            boolean answered = answeredNode.asBoolean();

            JsonNode answerNode = root.get("answer");
            if (answerNode == null || !answerNode.isString()) {
                throw invalidResponse();
            }
            String answerText = validateOutputText(answerNode.asString(), 2000);

            JsonNode sourcesNode = root.get("source_ids");
            if (sourcesNode == null || !sourcesNode.isArray() || sourcesNode.size() > allowedEntries.size()) {
                throw invalidResponse();
            }

            Set<Long> uniqueIds = new LinkedHashSet<>();
            for (JsonNode idNode : sourcesNode) {
                if (idNode == null || !idNode.isIntegralNumber() || !idNode.canConvertToLong()) {
                    throw invalidResponse();
                }
                long id = idNode.asLong();
                if (!allowedEntries.containsKey(id)) {
                    throw invalidResponse();
                }
                uniqueIds.add(id);
            }

            if (answered && uniqueIds.isEmpty()) {
                throw invalidResponse();
            }
            if (!answered && !uniqueIds.isEmpty()) {
                throw invalidResponse();
            }

            List<FaqAnswerResponse.SourceEntry> sources = uniqueIds.stream()
                    .map(allowedEntries::get)
                    .map(e -> new FaqAnswerResponse.SourceEntry(
                            e.getKnowledgeId(),
                            e.getTitle().strip(),
                            e.getCategory() == null ? "FAQ" : e.getCategory().strip()))
                    .toList();

            return new FaqAnswerResponse(question, answered, answerText, sources);
        } catch (AiExtractionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalidResponse();
        }
    }

    public String validateQuestion(String input) {
        if (input == null || input.isBlank() || input.length() > 1000
                || input.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t')) {
            throw new AiExtractionException(AiExtractionException.Kind.INVALID_INPUT);
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFC).strip();
        String folded = fold(normalized).replaceAll(
                "(?<!\\d)(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{4})(?!\\d)", "<date>");
        if (FAQ_PRIVATE_DATA.stream().anyMatch(p -> p.matcher(folded).find())) {
            throw new AiExtractionException(AiExtractionException.Kind.PRIVACY_REJECTED);
        }
        return normalized;
    }

    private static String validateCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String trimmed = Normalizer.normalize(category, Normalizer.Form.NFC).strip();
        if (trimmed.length() > 50 || trimmed.codePoints().anyMatch(Character::isISOControl)) {
            throw new AiExtractionException(AiExtractionException.Kind.INVALID_INPUT);
        }
        return trimmed;
    }

    private String validateOutputText(String text, int maxLength) {
        if (text == null || text.isBlank() || text.length() > maxLength
                || text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t')) {
            throw invalidResponse();
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).strip();
        String folded = fold(normalized).replaceAll(
                "(?<!\\d)(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{4})(?!\\d)", "<date>");
        if (FAQ_PRIVATE_DATA.stream().anyMatch(p -> p.matcher(folded).find())) {
            throw invalidResponse();
        }
        return normalized;
    }

    static String sanitizeContextText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
                .replaceAll("[\\p{Cntrl}&&[^\n\t]]", " ")
                .strip();
        String redacted = normalized
                .replaceAll("[\\p{L}\\p{N}._%+-]+\\s*@\\s*[\\p{L}\\p{N}.-]+", "[ẩn-email]")
                .replaceAll("(?<!\\d)(?:\\+?\\d[ .()-]*){9,}(?!\\d)", "[ẩn-số]")
                .replaceAll("(?i)\\b(?:eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+|AIza[A-Za-z0-9_-]{20,})", "[ẩn-khóa]");
        if (redacted.length() > maxLength) {
            return redacted.substring(0, maxLength).strip();
        }
        return redacted;
    }

    private static Set<String> extractTokens(String foldedText) {
        String[] parts = foldedText.split("[^a-z0-9]+");
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : parts) {
            if (part.length() >= 2 && !STOP_WORDS.contains(part)) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
    }

    private static void requireExactKeys(JsonNode node, String... expected) {
        if (node == null || !node.isObject()) {
            throw invalidResponse();
        }
        Set<String> actual = new LinkedHashSet<>();
        node.propertyNames().forEach(actual::add);
        if (!actual.equals(Set.of(expected))) {
            throw invalidResponse();
        }
    }

    private static String readResource(String path) throws IOException {
        try (var stream = new ClassPathResource(path).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static AiExtractionException invalidResponse() {
        return new AiExtractionException(AiExtractionException.Kind.INVALID_RESPONSE);
    }
}

