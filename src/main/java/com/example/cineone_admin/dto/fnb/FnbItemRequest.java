package com.example.cineone_admin.dto.fnb;

import com.example.cineone_admin.dto.validation.Utf8Size;
import com.example.cineone_admin.enums.FnbStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Create/full replacement. Service verifies that cinemaId belongs to brandId. */
@Getter
@Setter
public class FnbItemRequest {
    @NotNull
    @Positive
    private Long brandId;

    @NotNull
    @Positive
    private Long cinemaId;

    @NotBlank
    @Size(max = 150)
    private String name;

    @Utf8Size
    private String description;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal price;

    @Size(max = 500)
    private String imageUrl;

    @NotNull
    private FnbStatus status;
}
