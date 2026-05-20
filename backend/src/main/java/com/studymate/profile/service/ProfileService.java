package com.studymate.profile.service;

import com.studymate.auth.domain.User;
import com.studymate.auth.repository.RefreshTokenRepository;
import com.studymate.auth.repository.UserRepository;
import com.studymate.common.exception.ErrorCode;
import com.studymate.profile.dto.request.ProfileSaveRequest;
import com.studymate.profile.dto.response.ProfileResponse;
import com.studymate.profile.exception.ProfileException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(long userId) {
        return toResponse(getActiveUser(userId));
    }

    public ProfileResponse saveProfile(long userId, ProfileSaveRequest request) {
        User user = getActiveUser(userId);
        user.updateProfile(
                request.name().trim(),
                normalizeNullable(request.school()),
                normalizeNullable(request.major()),
                normalizeNullable(request.bio()),
                serializeTags(request.interestTags()),
                Instant.now(clock)
        );
        return toResponse(user);
    }

    public void deleteProfile(long userId) {
        Instant now = Instant.now(clock);
        User user = getActiveUser(userId);
        user.delete(now);
        refreshTokenRepository.revokeAllByUserId(userId, now);
    }

    private User getActiveUser(long userId) {
        return userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ProfileException(ErrorCode.UNAUTHORIZED));
    }

    private ProfileResponse toResponse(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getSchool(),
                user.getMajor(),
                user.getBio(),
                parseTags(user.getInterestTags()),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String serializeTags(List<String> tags) {
        if (tags == null) return "";
        return tags.stream()
                .map(this::normalizeNullable)
                .filter(tag -> tag != null)
                .map(tag -> tag.replace(",", ""))
                .filter(tag -> !tag.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .toList();
    }
}
