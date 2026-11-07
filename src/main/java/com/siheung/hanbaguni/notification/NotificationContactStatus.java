package com.siheung.hanbaguni.notification;

public record NotificationContactStatus(boolean emailVerified, String email, boolean phoneVerified, String maskedPhone) {}
