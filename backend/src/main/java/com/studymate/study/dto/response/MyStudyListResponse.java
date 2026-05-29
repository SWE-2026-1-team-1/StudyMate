package com.studymate.study.dto.response;

import java.util.List;

public record MyStudyListResponse(
        List<MyStudyItem> studies
) {}
