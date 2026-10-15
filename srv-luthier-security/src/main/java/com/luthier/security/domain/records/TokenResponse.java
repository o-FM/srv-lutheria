package com.luthier.security.domain.records;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

}
