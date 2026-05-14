package com.studymate.auth.fixture;

import com.studymate.auth.mail.EmailSender;

import java.util.ArrayList;
import java.util.List;

/**
 * 테스트용 EmailSender — 발송된 코드를 인메모리로 보관.
 */
public class FakeEmailSender implements EmailSender {

    public record SentMail(String to, String code) {}

    private final List<SentMail> sent = new ArrayList<>();

    @Override
    public void send(String to, String code) {
        sent.add(new SentMail(to, code));
    }

    public String lastCode() {
        if (sent.isEmpty()) throw new IllegalStateException("No mail sent");
        return sent.get(sent.size() - 1).code();
    }

    public String lastTo() {
        if (sent.isEmpty()) throw new IllegalStateException("No mail sent");
        return sent.get(sent.size() - 1).to();
    }

    public int sendCount() { return sent.size(); }

    public void clear() { sent.clear(); }
}
