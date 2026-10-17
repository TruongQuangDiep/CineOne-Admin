package com.example.cineone_admin.dto.showtime;

import com.example.cineone_admin.enums.ShowtimeStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.OptBoolean;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Create/full replacement; local cinema wall time, without offset conversion. */
@Getter
@Setter
public class ShowtimeRequest {
    @NotNull
    @Positive
    private Long movieId;

    @NotNull
    @Positive
    private Long roomId;

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM-dd'T'HH:mm:ss", lenient = OptBoolean.FALSE)
    private LocalDateTime startTime;

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM-dd'T'HH:mm:ss", lenient = OptBoolean.FALSE)
    private LocalDateTime endTime;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal basePrice;

    @NotNull
    private ShowtimeStatus status;

    @AssertTrue(message = "endTime must be after startTime")
    @JsonIgnore
    public boolean isTimeRangeValid() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }
}
