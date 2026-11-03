package com.example.cineone_admin.dto.catalog;

import com.example.cineone_admin.entity.Cinema;
import com.example.cineone_admin.entity.CinemaBrand;
import com.example.cineone_admin.service.CatalogService;
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
public class CinemaResponse {
    private Long cinemaId;
    private Long brandId;
    private String brandName;
    private String brandLogoUrl;
    private String name;
    private String address;
    private String city;
    private String phone;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Long roomCount;
    private String status;

    public static CinemaResponse from(CatalogService.CinemaRow row) {
        Cinema c = row.cinema();
        CinemaBrand brand = c.getBrand();
        return CinemaResponse.builder()
                .cinemaId(c.getCinemaId())
                .brandId(brand != null ? brand.getBrandId() : null)
                .brandName(brand != null ? brand.getName() : null)
                .brandLogoUrl(brand != null ? brand.getLogoUrl() : null)
                .name(c.getName())
                .address(c.getAddress())
                .city(extractCity(c.getAddress()))
                .phone(c.getPhone())
                .latitude(c.getLatitude())
                .longitude(c.getLongitude())
                .roomCount(row.roomCount())
                .status(c.getStatus())
                .build();
    }

    public static String extractCity(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        int lastComma = address.lastIndexOf(',');
        return lastComma >= 0 ? address.substring(lastComma + 1).trim() : address.trim();
    }
}

