package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.exception.BadRequestException;
import com.olehprukhnytskyi.exception.error.AuthErrorCode;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
import com.olehprukhnytskyi.macrotrackeruserservice.service.strategy.SocialTokenVerifier;
import com.olehprukhnytskyi.util.AuthProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
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
        log.error("Unsupported social provider: {}", provider);
        throw new BadRequestException(AuthErrorCode.UNSUPPORTED_PROVIDER, "Unsupported provider");
    }
}
