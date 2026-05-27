package com.studymate.application.dto.request;

import jakarta.validation.constraints.Size;

public record ApplicationRejectRequest(
        @Size(max = 500) String rejectReason
) {}
