package com.example.cineone_admin.dto.cinema;

import com.example.cineone_admin.enums.CinemaStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Cinema location, not CinemaBrand. Shared create/full-replacement payload. */
@Getter
@Setter
public class CinemaRequest {
    @NotNull
    @Positive
    private Long brandId;

    @NotBlank
    @Size(max = 150)
    private String name;

    @NotBlank
    @Size(max = 255)
    private String address;

    // Follow SCRUM-5 BigDecimal mapping; it declares no precision/scale.
    @DecimalMin("-90")
    @DecimalMax("90")
    private BigDecimal latitude;

    @DecimalMin("-180")
    @DecimalMax("180")
    private BigDecimal longitude;

    @Size(max = 20)
    private String phone;

    @NotNull
    private CinemaStatus status;
}
