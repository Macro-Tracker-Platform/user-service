package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
import com.olehprukhnytskyi.macrotrackeruserservice.service.strategy.SocialTokenVerifier;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SocialTokenVerificationService {
    private final List<SocialTokenVerifier> verifiers;

    public SocialUserDetails verifyToken(String token, String provider) {
        for (SocialTokenVerifier verifier : verifiers) {
            if (verifier.supports(provider)) {
                return verifier.verify(token);
            }
        }
        throw new IllegalArgumentException("Unsupported provider: " + provider);
    }
}
