package com.olehprukhnytskyi.macrotrackeruserservice.service.strategy;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
import com.olehprukhnytskyi.util.AuthProvider;

public interface SocialTokenVerifier {
    boolean supports(AuthProvider provider);

    SocialUserDetails verify(String token);
}
