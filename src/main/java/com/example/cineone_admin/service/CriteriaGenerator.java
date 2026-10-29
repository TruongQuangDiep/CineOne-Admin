package com.example.cineone_admin.service;

import java.util.Map;
@FunctionalInterface
public interface CriteriaGenerator {
    String generate(String systemPrompt, String userContent, Map<String, Object> schema);
}
