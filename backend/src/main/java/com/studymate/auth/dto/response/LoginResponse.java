package com.studymate.auth.dto.response;

public record LoginResponse(String accessToken, String refreshToken, long userId) {}
