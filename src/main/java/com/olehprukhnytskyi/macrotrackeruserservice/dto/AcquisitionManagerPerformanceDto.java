package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AcquisitionManagerPerformanceDto {
    private Long id;
    private String name;
    private String email;
    private BigDecimal commissionPercent;
    private boolean active;
    private long promoCodes;
    private long recruitedUsers;
    private long activeSubscribers;
}
