package com.medidrop.notification;

public interface SmsSender {

    void send(String phone, String message);
}
