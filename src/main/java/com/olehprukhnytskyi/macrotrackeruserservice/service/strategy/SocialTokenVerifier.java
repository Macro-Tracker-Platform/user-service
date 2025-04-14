package com.olehprukhnytskyi.macrotrackeruserservice.service.strategy;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;

public interface SocialTokenVerifier {
    boolean supports(String provider);

    SocialUserDetails verify(String token);
}
