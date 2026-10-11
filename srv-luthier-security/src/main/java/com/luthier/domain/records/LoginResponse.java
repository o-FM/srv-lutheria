package com.luthier.domain.records;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {

}
