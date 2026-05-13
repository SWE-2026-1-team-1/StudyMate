package com.studymate.auth.dto.response;

public record TokenRefreshResponse(String accessToken, String refreshToken) {}
