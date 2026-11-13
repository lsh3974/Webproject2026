package com.siheung.hanbaguni.notification;

import com.siheung.hanbaguni.repository.NotificationEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hanbaguni.notifications.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationEventProcessor {
    private final NotificationEventRepository events;
    private final NotificationEventWorker worker;

    /** 이벤트 ID만 조회하고 각 이벤트는 별도 트랜잭션에서 처리한다. */
    @Scheduled(fixedDelayString = "${hanbaguni.notifications.outbox-delay-ms:3000}")
    @Transactional(readOnly = true)
    public void processPendingEvents() {
        var dueIds = events.findDueIds(LocalDateTime.now(), PageRequest.of(0, 50));
        for (Long eventId : dueIds) {
            try {
                worker.process(eventId);
            } catch (RuntimeException exception) {
                // DB 연결 장애 등 트랜잭션 자체가 시작/커밋되지 못한 경우 다음 주기에 다시 시도한다.
                log.error("알림 Outbox 이벤트 처리 트랜잭션 실패 eventId={}", eventId, exception);
                try {
                    worker.recordFailure(eventId, exception);
                } catch (RuntimeException failureException) {
                    log.error("알림 Outbox 실패 상태 기록도 실패 eventId={}", eventId, failureException);
                }
            }
        }
    }
}
