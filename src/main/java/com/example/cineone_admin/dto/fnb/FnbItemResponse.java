package com.example.cineone_admin.dto.fnb;

import com.example.cineone_admin.enums.FnbStatus;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FnbItemResponse {
    private Long fnbId;
    private Long brandId;
    private String brandName;
    private Long cinemaId;
    private String cinemaName;
    private String name;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private FnbStatus status;
}
