package com.studymate.auth.mail;

public interface EmailSender {
    void send(String to, String code);
}
