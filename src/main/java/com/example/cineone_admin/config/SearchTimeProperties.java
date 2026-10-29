package com.example.cineone_admin.config;

import com.example.cineone_admin.exception.AiExtractionException;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
@Getter
@Setter
@ConfigurationProperties("ai.search.time")
public class SearchTimeProperties {
    private int approximateMinutes = 30;
    private LocalTime morningStart = LocalTime.of(6, 0);
    private LocalTime morningEnd = LocalTime.NOON;
    private LocalTime afternoonStart = LocalTime.NOON;
    private LocalTime afternoonEnd = LocalTime.of(18, 0);
    private LocalTime eveningStart = LocalTime.of(18, 0);
    private LocalTime eveningEnd = LocalTime.MIDNIGHT;
    private LocalTime lateNightStart = LocalTime.MIDNIGHT;
    private LocalTime lateNightEnd = LocalTime.of(6, 0);

    public void validate() {
        if (approximateMinutes < 1 || approximateMinutes > 120
                || !valid(morningStart, morningEnd) || !valid(afternoonStart, afternoonEnd)
                || !valid(eveningStart, eveningEnd) || !valid(lateNightStart, lateNightEnd)) {
            throw new AiExtractionException(AiExtractionException.Kind.CONFIGURATION);
        }
    }

    private static boolean valid(LocalTime start, LocalTime end) {
        return start != null && end != null && !start.equals(end)
                && start.getSecond() == 0 && end.getSecond() == 0
                && start.getNano() == 0 && end.getNano() == 0;
    }
}
