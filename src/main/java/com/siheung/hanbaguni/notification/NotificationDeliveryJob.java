package com.siheung.hanbaguni.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hanbaguni.notifications.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationDeliveryJob {
    private final NotificationDeliveryTransactions transactions;
    private final List<NotificationChannelSender> senders;

    @Scheduled(fixedDelayString = "${hanbaguni.notifications.reconcile-delay-ms:60000}")
    public void reconcileUnknown() {
        Map<NotificationDelivery.Channel, NotificationChannelSender> byChannel = new EnumMap<>(NotificationDelivery.Channel.class);
        senders.forEach(sender -> byChannel.put(sender.channel(), sender));
        for (Long id : transactions.claimUnknownForReconciliation()) {
            try {
                NotificationDispatchData data = transactions.load(id);
                NotificationChannelSender sender = byChannel.get(data.channel());
                if (sender != null && sender.supportsReconciliation()) {
                    var accepted = sender.findAccepted(data);
                    if (accepted.isPresent()) {
                        transactions.markSent(id, accepted.get());
                        continue;
                    }
                }
                transactions.deferReconciliation(id,
                        "제공사 조회에서 접수 여부를 확인하지 못했습니다. 자동 재전송하지 않으며 제공사 발송 내역과 대조해야 합니다.");
            } catch (RuntimeException exception) {
                transactions.deferReconciliation(id,
                        "발송 결과 재확인 실패: " + exception.getClass().getSimpleName() + ". 운영자 대조가 필요합니다.");
                log.warn("알림 발송 결과 재확인 실패 deliveryId={}", id, exception);
            }
        }
    }

    @Scheduled(fixedDelayString = "${hanbaguni.notifications.delivery-delay-ms:5000}")
    public void dispatch() {
        transactions.markExpiredClaimsUnknown();
        Map<NotificationDelivery.Channel, NotificationChannelSender> byChannel = new EnumMap<>(NotificationDelivery.Channel.class);
        senders.forEach(sender -> byChannel.put(sender.channel(), sender));

        for (int i = 0; i < 25; i++) {
            var claimed = transactions.claimNext();
            if (claimed.isEmpty()) break;
            Long id = claimed.get();
            try {
                NotificationDispatchData data = transactions.load(id);
                if ((data.channel() == NotificationDelivery.Channel.EMAIL && (!data.emailEnabled() || !data.emailVerified()))
                        || (data.channel() == NotificationDelivery.Channel.SMS && (!data.smsEnabled() || !data.phoneVerified()))
                        || (data.channel() == NotificationDelivery.Channel.PUSH && (!data.pushEnabled() || !data.pushSubscriptionActive()))) {
                    transactions.markSkipped(id, "발송 전에 사용자가 채널을 끄거나 수신 자격/구독이 해제되었습니다.");
                    continue;
                }
                NotificationChannelSender sender = byChannel.get(data.channel());
                if (sender == null) throw new NotificationSendException("채널 발송기가 등록되지 않았습니다.", NotificationSendException.Outcome.PERMANENT);
                String providerId = sender.send(data);
                transactions.markSent(id, providerId);
            } catch (NotificationSendException exception) {
                NotificationChannelSender sender = null;
                try { sender = byChannel.get(transactions.load(id).channel()); } catch (RuntimeException ignored) { }
                if (exception.getOutcome() == NotificationSendException.Outcome.UNKNOWN
                        && sender != null && sender.supportsReconciliation()) {
                    try {
                        var accepted = sender.findAccepted(transactions.load(id));
                        if (accepted.isPresent()) {
                            transactions.markSent(id, accepted.get());
                            log.info("SOLAPI 발송 접수 결과를 조회로 확인했습니다. deliveryId={}", id);
                        } else {
                            transactions.markFailure(id, new NotificationSendException(
                                    "제공사 조회에서 접수를 확인하지 못했습니다. 중복 발송 방지를 위해 자동 재전송하지 않습니다. 발송 내역을 대조하세요.",
                                    NotificationSendException.Outcome.UNKNOWN));
                        }
                    } catch (RuntimeException reconcileFailure) {
                        transactions.markFailure(id, new NotificationSendException(
                                "제공사 조회로도 접수 여부를 확인하지 못했습니다. 운영자 대조가 필요합니다.",
                                NotificationSendException.Outcome.UNKNOWN, reconcileFailure));
                    }
                } else {
                    transactions.markFailure(id, exception);
                }
                log.warn("알림 채널 발송 실패 deliveryId={} outcome={} message={}", id, exception.getOutcome(), exception.getMessage());
            } catch (RuntimeException exception) {
                // 예상하지 못한 오류는 발송 여부를 확정할 수 없으므로 UNKNOWN으로 보존합니다.
                transactions.markFailure(id, new NotificationSendException(
                        "예상하지 못한 발송 오류: " + exception.getClass().getSimpleName(),
                        NotificationSendException.Outcome.UNKNOWN, exception));
                log.error("알림 발송 처리 중 예외 deliveryId={}", id, exception);
            }
        }
    }
}
