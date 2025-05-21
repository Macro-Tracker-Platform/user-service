package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
import com.olehprukhnytskyi.macrotrackeruserservice.service.strategy.SocialTokenVerifier;
import com.olehprukhnytskyi.macrotrackeruserservice.util.AuthProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SocialTokenVerificationService {
    private final List<SocialTokenVerifier> verifiers;

    public SocialUserDetails verifyToken(String token, AuthProvider provider) {
        for (SocialTokenVerifier verifier : verifiers) {
            if (verifier.supports(provider)) {
                return verifier.verify(token);
            }
        }
        throw new IllegalArgumentException("Unsupported provider: "
                + provider.name().toLowerCase());
    }
}
