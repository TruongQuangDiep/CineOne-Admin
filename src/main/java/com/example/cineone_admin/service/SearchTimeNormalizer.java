package com.example.cineone_admin.service;

import com.example.cineone_admin.config.SearchTimeProperties;
import com.example.cineone_admin.dto.ai.SearchCriteria;
import com.example.cineone_admin.dto.ai.SearchCriteria.TimeRange;
import java.text.Normalizer;
import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Pattern;
public final class SearchTimeNormalizer {
    private static final Pattern TIME = Pattern.compile(
            "^(\\d{1,2})\\s*(gio|h|:)(?:\\s*(\\d{1,2}|ruoi)(?:\\s*(?:phut|p))?)?"
                    + "\\s*(?:(?:buoi\\s+)?(sang|trua|chieu|toi|khuya|dem))?$");
    private static final Pattern RANGE = Pattern.compile(
            "^(?:tu\\s+)?(.+?)\\s*(?:den|toi|[-–])\\s*(.+?)(?:\\s+(?:ngay\\s+)?hom sau)?$");
    private static final Pattern PERIOD = Pattern.compile("^(?:vao\\s+)?(?:buoi\\s+)?(sang|chieu|toi|khuya|dem)$");
    private static final Pattern CHOICES = Pattern.compile("\\b(?:hoac|hay|va)\\b");
    private final SearchTimeProperties properties;

    public SearchTimeNormalizer(SearchTimeProperties properties) {
        this.properties = properties;
    }

    public void validateConfiguration() { properties.validate(); }

    public SearchCriteria normalize(SearchCriteria criteria) {
        validateConfiguration();
        var range = criteria.timeRange();
        if (range == null) return criteria;
        return new SearchCriteria(criteria.movieTitle(), criteria.genre(), criteria.date(),
                normalize(range), criteria.location());
    }

    private TimeRange normalize(TimeRange raw) {
        String expression = fold(raw.expression());
        if (CHOICES.matcher(expression).find()) return unresolved(raw);
        String clockExpression = expression.replaceFirst("\\s+(?:hom nay|ngay mai|ngay kia|nay|mai)$", "");
        var period = PERIOD.matcher(clockExpression);
        if (period.matches()) {
            return switch (period.group(1)) {
                case "sang" -> bounds(raw, properties.getMorningStart(), properties.getMorningEnd(), true, false);
                case "chieu" -> bounds(raw, properties.getAfternoonStart(), properties.getAfternoonEnd(), true, false);
                case "toi" -> bounds(raw, properties.getEveningStart(), properties.getEveningEnd(), true, false);
                default -> bounds(raw, properties.getLateNightStart(), properties.getLateNightEnd(), true, false);
            };
        }

        String approximate = clockExpression.replaceFirst("^(?:tam khoang|khoang|tam|do|gan)\\s+", "");
        if (!approximate.equals(clockExpression)) {
            Point point = point(approximate);
            if (point == null || point.time() == null) return unresolved(raw);
            int center = point.time().getHour() * 60 + point.time().getMinute();
            int start = center - properties.getApproximateMinutes();
            int end = center + properties.getApproximateMinutes();
            return new TimeRange(raw.expression(), minute(start), minute(end),
                    Math.floorDiv(start, 1440), Math.floorDiv(end, 1440), true, true, false);
        }

        String open = clockExpression.replaceFirst("^(?:sau|tu|truoc|den)\\s+", "");
        if (!open.equals(clockExpression)) {
            Point point = point(open);
            if (point != null) {
                if (point.time() == null) return unresolved(raw);
                if (expression.startsWith("sau ") || expression.startsWith("tu ")) {
                    return new TimeRange(raw.expression(), point.time(), null, 0, null,
                            expression.startsWith("tu "), null, false);
                }
                return new TimeRange(raw.expression(), null, point.time(), null, 0,
                        null, expression.startsWith("den "), false);
            }
        }

        var explicitRange = RANGE.matcher(expression);
        if (explicitRange.matches()) {
            Point start = point(explicitRange.group(1));
            Point end = point(explicitRange.group(2));
            if (start != null && end != null) {
                if (start.time() == null && end.period() != null) {
                    start = point(explicitRange.group(1) + " " + end.period());
                }
                if (end.time() == null && start.period() != null) {
                    end = point(explicitRange.group(2) + " " + start.period());
                }
                if (start.time() == null || end.time() == null) return unresolved(raw);
                var result = bounds(raw, start.time(), end.time(), true, false);
                if (expression.endsWith("hom sau")) {
                    return new TimeRange(raw.expression(), start.time(), end.time(), 0, 1, true, false, false);
                }
                return result;
            }
        }

        Point exact = point(clockExpression.replaceFirst("^(?:luc|vao luc|vao)\\s+", ""));
        if (exact != null) {
            return exact.time() == null ? unresolved(raw) : bounds(raw, exact.time(), exact.time(), true, true);
        }
        Boolean startInclusive = raw.start() == null ? null : !expression.startsWith("sau ");
        Boolean endInclusive = raw.end() == null ? null
                : !expression.startsWith("truoc ") && (raw.start() == null || raw.start().equals(raw.end()));
        return new TimeRange(raw.expression(), raw.start(), raw.end(), raw.startDayOffset(), raw.endDayOffset(),
                startInclusive, endInclusive, raw.start() == null && raw.end() == null);
    }

    private static Point point(String expression) {
        var match = TIME.matcher(expression.strip());
        if (!match.matches()) return null;
        int hour = Integer.parseInt(match.group(1));
        int minute = match.group(3) == null ? 0 : match.group(3).equals("ruoi") ? 30
                : Integer.parseInt(match.group(3));
        String period = match.group(4);
        if (hour > 23 || minute > 59) return new Point(null, period);
        if (period != null) {
            hour = switch (period) {
                case "sang" -> hour < 12 ? hour : -1;
                case "trua" -> hour == 1 ? 13 : hour >= 11 && hour <= 13 ? hour : -1;
                case "chieu" -> hour >= 1 && hour <= 6 ? hour + 12 : hour >= 12 && hour <= 18 ? hour : -1;
                case "toi" -> hour == 12 || hour == 0 ? 0 : hour >= 6 && hour <= 11 ? hour + 12
                        : hour >= 18 ? hour : -1;
                default -> hour == 12 ? 0 : hour <= 5 ? hour : hour <= 11 ? hour + 12
                        : hour >= 18 ? hour : -1;
            };
            if (hour < 0) return new Point(null, period);
        } else if (hour > 0 && hour < 12 && !match.group(1).startsWith("0")) {
            return new Point(null, null);
        }
        return new Point(LocalTime.of(hour, minute), period);
    }

    private static TimeRange bounds(TimeRange raw, LocalTime start, LocalTime end,
                                    boolean startInclusive, boolean endInclusive) {
        return new TimeRange(raw.expression(), start, end, 0, end.isBefore(start) ? 1 : 0,
                startInclusive, endInclusive, false);
    }

    private static TimeRange unresolved(TimeRange raw) {
        return new TimeRange(raw.expression(), null, null, null, null, null, null, true);
    }

    private static LocalTime minute(int minute) {
        return LocalTime.of(Math.floorMod(minute, 1440) / 60, Math.floorMod(minute, 60));
    }

    private static String fold(String expression) {
        return Normalizer.normalize(expression, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
    }

    private record Point(LocalTime time, String period) {}
}
