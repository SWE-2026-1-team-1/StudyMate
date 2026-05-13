package com.studymate.auth.fixture;

import com.studymate.auth.domain.User;
import com.studymate.auth.repository.UserRepository;

public class UserFixture {

    private UserFixture() {}

    /**
     * BCrypt 해시 없이 단순 문자열 해시로 저장하는 테스트용 빌더.
     * 비밀번호 검증 테스트는 AuthService 단위 테스트에서 mock 처리.
     */
    public static User create(String email, String rawPasswordHash, String name) {
        return User.create(email, rawPasswordHash, name);
    }

    public static User save(UserRepository repo, String email, String passwordHash, String name) {
        return repo.save(create(email, passwordHash, name));
    }
}
