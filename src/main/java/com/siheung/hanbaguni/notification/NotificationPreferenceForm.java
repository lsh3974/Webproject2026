package com.siheung.hanbaguni.notification;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class NotificationPreferenceForm {
    private boolean emailEnabled;
    private boolean smsEnabled;
    private boolean pushEnabled;
}
