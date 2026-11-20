package com.medidrop.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "medidrop.mail.provider", havingValue = "console", matchIfMissing = true)
@Slf4j
public class ConsoleEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String body) {
        // Console transport is for local development only; never print bodies or reset links.
        log.info("[dev-email] to={} subject={} bodyLength={}", to, subject, body == null ? 0 : body.length());
    }
}
