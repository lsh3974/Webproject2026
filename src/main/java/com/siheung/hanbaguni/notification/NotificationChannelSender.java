package com.siheung.hanbaguni.notification;

import java.util.Optional;

public interface NotificationChannelSender {
    NotificationDelivery.Channel channel();
    /** 정상 접수된 제공사 메시지 식별자. 이메일은 null일 수 있습니다. */
    String send(NotificationDispatchData data);

    /** 전송 응답이 유실된 경우 제공사에서 deliveryId가 접수되었는지 조회합니다. */
    default boolean supportsReconciliation() { return false; }
    default Optional<String> findAccepted(NotificationDispatchData data) { return Optional.empty(); }
}
