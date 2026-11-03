package com.example.cineone_admin.dto.catalog;

import com.example.cineone_admin.entity.Cinema;
import com.example.cineone_admin.entity.CinemaBrand;
import com.example.cineone_admin.entity.FnbItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FnbResponse {
    private Long fnbId;
    private String name;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private Long cinemaId;
    private String cinemaName;
    private Long brandId;
    private String brandName;
    private String status;

    public static FnbResponse from(FnbItem item) {
        Cinema c = item.getCinema();
        CinemaBrand b = item.getBrand();
        return FnbResponse.builder()
                .fnbId(item.getFnbId())
                .name(item.getName())
                .description(item.getDescription())
                .price(item.getPrice())
                .imageUrl(item.getImageUrl())
                .cinemaId(c != null ? c.getCinemaId() : null)
                .cinemaName(c != null ? c.getName() : null)
                .brandId(b != null ? b.getBrandId() : null)
                .brandName(b != null ? b.getName() : null)
                .status(item.getStatus())
                .build();
    }
}

