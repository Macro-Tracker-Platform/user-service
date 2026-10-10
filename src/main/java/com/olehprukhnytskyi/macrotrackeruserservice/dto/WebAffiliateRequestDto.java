package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WebAffiliateRequestDto(@NotBlank @Size(max = 255) String name,
                                     @Email @Size(max = 320) String email, Long referrerId) {
}
