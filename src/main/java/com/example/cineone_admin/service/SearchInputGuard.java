package com.example.cineone_admin.service;

import com.example.cineone_admin.exception.AiExtractionException;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
public class SearchInputGuard {
    private static final List<Pattern> PRIVATE_DATA = List.of(
            Pattern.compile("[\\p{L}\\p{N}._%+-]+\\s*@\\s*[\\p{L}\\p{N}.-]+", Pattern.UNICODE_CHARACTER_CLASS),
            Pattern.compile("(?<!\\d)(?:\\+?\\d[ .()-]*){9,}(?!\\d)"),
            Pattern.compile("(?i)\\b(?:eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+|AIza[A-Za-z0-9_-]{20,})"),
            Pattern.compile("(?i)\\b(?:otp|password|passwd|token|secret|api[ _-]?key|bearer|cvv|cccd|cmnd|iban)\\b"),
            Pattern.compile("\\b(?:mat khau|ma xac thuc|so tai khoan|so the|ho chieu|so dien thoai|email|e-mail|ho ten|toi ten|tui ten|ten toi|ten tui)\\b"),
            Pattern.compile("\\b(?:dia chi nha|nha toi o|nha tui o|toi song (?:o|tai)|tui song (?:o|tai)|toi o so|tui o so|(?:toi|tui|minh) o \\d+)\\b"),
            Pattern.compile("\\b\\d+[a-z]?(?:/\\d+[a-z]?)*\\s+(?:duong|hem|ngo|ngach|street|road)\\b"),
            Pattern.compile("(?<![\\d.])-?\\d{1,3}\\.\\d{3,}\\s*[,;]\\s*-?\\d{1,3}\\.\\d{3,}(?![\\d.])")
    );

    public String validate(String input) {
        if (input == null || input.isBlank() || input.length() > 1000
                || input.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t')) {
            throw new AiExtractionException(AiExtractionException.Kind.INVALID_INPUT);
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFC).strip();
        String folded = fold(normalized).replaceAll(
                "(?<!\\d)(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{4})(?!\\d)", "<date>");
        if (PRIVATE_DATA.stream().anyMatch(p -> p.matcher(folded).find())) {
            throw new AiExtractionException(AiExtractionException.Kind.PRIVACY_REJECTED);
        }
        return normalized;
    }

    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
    }
}
