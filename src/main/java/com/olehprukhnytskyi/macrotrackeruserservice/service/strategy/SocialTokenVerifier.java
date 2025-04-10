package com.olehprukhnytskyi.macrotrackeruserservice.service.strategy;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserPayload;

public interface SocialTokenVerifier {
    boolean supports(String provider);

    SocialUserPayload verify(String token);
}
