package com.studymate.config;

import com.studymate.auth.fixture.FakeEmailSender;
import com.studymate.auth.mail.EmailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 통합 테스트용 빈 교체.
 * @SpringBootTest 클래스에 @Import(TestConfig.class) 로 포함.
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public EmailSender fakeEmailSender() {
        return new FakeEmailSender();
    }
}
