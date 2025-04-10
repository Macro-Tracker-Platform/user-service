package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SocialTokenRequest {
    private String token;
    private String provider;
}
