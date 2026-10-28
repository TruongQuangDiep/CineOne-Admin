package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.ai.SearchCriteria;
import com.example.cineone_admin.config.SearchTimeProperties;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;
public final class SearchCriteriaExtractor {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private final CriteriaGenerator generator;
    private final Clock clock;
    private final SearchTimeNormalizer timeNormalizer;
    private final CriteriaContract contract = new CriteriaContract();
    private final CriteriaParser parser = new CriteriaParser();
    private final SearchInputGuard guard = new SearchInputGuard();
    private final JsonMapper mapper = new JsonMapper();

    public SearchCriteriaExtractor(CriteriaGenerator generator, Clock clock) {
        this(generator, clock, new SearchTimeProperties());
    }

    public SearchCriteriaExtractor(CriteriaGenerator generator, Clock clock, SearchTimeProperties timeProperties) {
        this.generator = generator;
        this.clock = clock;
        this.timeNormalizer = new SearchTimeNormalizer(timeProperties);
    }

    public SearchCriteria extract(String request) {
        String safeInput = guard.validate(request);
        timeNormalizer.validateConfiguration();
        String content = mapper.writeValueAsString(Map.of(
                "reference_date", LocalDate.now(clock.withZone(VIETNAM)).toString(),
                "timezone", VIETNAM.getId(), "request", safeInput));
        var extracted = parser.parse(generator.generate(contract.systemPrompt(), content, contract.schema()));
        return timeNormalizer.normalize(extracted);
    }
}
