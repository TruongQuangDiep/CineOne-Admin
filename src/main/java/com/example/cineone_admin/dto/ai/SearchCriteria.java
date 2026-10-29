package com.example.cineone_admin.dto.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalTime;
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SearchCriteria(
        @JsonProperty("movie_title") String movieTitle,
        String genre,
        LocalDate date,
        @JsonProperty("time_range") TimeRange timeRange,
        Location location) {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TimeRange(String expression,
                            @JsonFormat(pattern = "HH:mm") LocalTime start,
                            @JsonFormat(pattern = "HH:mm") LocalTime end,
                            @JsonProperty("start_day_offset") Integer startDayOffset,
                            @JsonProperty("end_day_offset") Integer endDayOffset,
                            @JsonProperty("start_inclusive") Boolean startInclusive,
                            @JsonProperty("end_inclusive") Boolean endInclusive,
                            @JsonProperty("requires_clarification") boolean requiresClarification) {
        public TimeRange(String expression, LocalTime start, LocalTime end) {
            this(expression, start, end, start == null ? null : 0,
                    end == null ? null : start != null && end.isBefore(start) ? 1 : 0,
                    start == null ? null : true, end == null ? null : true,
                    start == null && end == null);
        }
    }

    public enum LocationType { AREA, CINEMA, BRAND, NEARBY, HOME }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Location(LocationType type, String text) {}
}
