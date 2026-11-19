package com.medidrop.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "medidrop.sms.provider", havingValue = "console", matchIfMissing = true)
@Slf4j
public class ConsoleSmsSender implements SmsSender {

    @Override
    public void send(String phone, String message) {
        // Local development transport only. No SMS is actually delivered.
        log.info("[dev-sms] to={} message={}", phone, message);
    }
}
