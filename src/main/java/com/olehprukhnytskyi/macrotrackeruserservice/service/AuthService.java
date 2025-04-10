package com.olehprukhnytskyi.macrotrackeruserservice.service;

public interface AuthService {
    String authenticateWithSocial(String provider, String token);
}
