package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FacebookUserResponse {
    private String id;
    private String email;
    private String name;
}
