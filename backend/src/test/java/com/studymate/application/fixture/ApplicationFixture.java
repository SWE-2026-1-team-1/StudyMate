package com.studymate.application.fixture;

import com.studymate.application.domain.Application;
import com.studymate.application.domain.ApplicationStatus;
import com.studymate.application.repository.ApplicationRepository;
import org.springframework.jdbc.core.JdbcTemplate;

public class ApplicationFixture {

    private ApplicationFixture() {}

    public static long pending(long studyId, long applicantId, String message,
                               ApplicationRepository repo) {
        Application a = repo.save(Application.createPending(studyId, applicantId, message));
        return a.getId();
    }

    public static long accepted(long studyId, long applicantId, long processedByMemberId,
                                ApplicationRepository repo, JdbcTemplate jdbc) {
        long id = pending(studyId, applicantId, null, repo);
        jdbc.update("UPDATE application SET status='ACCEPTED', processed_at=CURRENT_TIMESTAMP, processed_by_member_id=? WHERE id=?",
                processedByMemberId, id);
        return id;
    }

    public static long rejected(long studyId, long applicantId, long processedByMemberId,
                                ApplicationRepository repo, JdbcTemplate jdbc) {
        long id = pending(studyId, applicantId, null, repo);
        jdbc.update("UPDATE application SET status='REJECTED', processed_at=CURRENT_TIMESTAMP, processed_by_member_id=? WHERE id=?",
                processedByMemberId, id);
        return id;
    }
}
