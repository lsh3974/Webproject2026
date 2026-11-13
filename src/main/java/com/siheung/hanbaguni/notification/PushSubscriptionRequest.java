package com.siheung.hanbaguni.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushSubscriptionRequest(
        @NotBlank @Size(max = 1200) String endpoint,
        @Valid Keys keys
) {
    public record Keys(@NotBlank @Size(max = 200) String p256dh,
                       @NotBlank @Size(max = 100) String auth) {}
}
