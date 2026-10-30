package com.example.cineone_admin.dto.user;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserProfileRequest {
    private String fullName;
    private String phone;

    @JsonAlias({"address", "homeAddress"})
    private String homeAddress;
}