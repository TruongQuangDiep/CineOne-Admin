package com.example.cineone_admin.dto.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record FaqAnswerResponse(
        String question,
        boolean answered,
        String answer,
        List<SourceEntry> sources) {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SourceEntry(
            @JsonProperty("knowledge_id") Long knowledgeId,
            String title,
            String category) {}
}

