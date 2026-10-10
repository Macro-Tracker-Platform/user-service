package com.olehprukhnytskyi.macrotrackeruserservice.dto;

public record WebAffiliateResponseDto(Long id, String name, String email,
                                      Long referrerId, boolean active) {
}
