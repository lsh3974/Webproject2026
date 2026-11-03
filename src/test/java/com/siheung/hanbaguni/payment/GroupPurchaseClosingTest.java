package com.siheung.hanbaguni.payment;

import com.siheung.hanbaguni.entity.GroupPurchase;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class GroupPurchaseClosingTest {

    private static final LocalDateTime DEADLINE =
            LocalDateTime.of(2026, 11, 3, 18, 0);

    @Test
    void 마감_직전에는_목표_미달이어도_모집을_유지한다() {
        GroupPurchase group = openGroup();

        assertThat(group.failIfDeadlineReached(
                DEADLINE.minusSeconds(1), false)).isFalse();

        assertThat(group.getStatus()).isEqualTo(GroupPurchase.Status.OPEN);
    }

    @Test
    void 마감_시각에_목표_미달이면_한번만_실패로_바뀐다() {
        GroupPurchase group = openGroup();
        group.addPaidQuantity(9);

        assertThat(group.failIfDeadlineReached(DEADLINE, false)).isTrue();
        assertThat(group.getStatus()).isEqualTo(GroupPurchase.Status.FAILED);
        assertThat(group.failIfDeadlineReached(
                DEADLINE.plusMinutes(1), false)).isFalse();
    }

    @Test
    void 승인_결과를_기다리면_마감이_지나도_판정을_보류한다() {
        GroupPurchase group = openGroup();
        group.addPaidQuantity(9);
        LocalDateTime afterDeadline = DEADLINE.plusMinutes(1);

        assertThat(group.failIfDeadlineReached(afterDeadline, true)).isFalse();
        assertThat(group.getStatus()).isEqualTo(GroupPurchase.Status.OPEN);

        // 기다리던 결제가 거절되어 추가 결제 수량이 없는 경우
        assertThat(group.failIfDeadlineReached(afterDeadline, false)).isTrue();
        assertThat(group.getStatus()).isEqualTo(GroupPurchase.Status.FAILED);
    }

    @Test
    void 기다리던_결제가_승인되어_목표를_달성하면_성공을_유지한다() {
        GroupPurchase group = openGroup();
        group.addPaidQuantity(9);

        assertThat(group.failIfDeadlineReached(DEADLINE, true)).isFalse();

        group.addPaidQuantity(1);
        assertThat(group.succeedIfTargetReached()).isTrue();

        assertThat(group.failIfDeadlineReached(
                DEADLINE.plusMinutes(1), false)).isFalse();
        assertThat(group.getStatus()).isEqualTo(GroupPurchase.Status.SUCCESS);
    }

    private GroupPurchase openGroup() {
        return new GroupPurchase(
                null, null, "마감 테스트 공동구매",
                1000, 10, 20, 2,
                DEADLINE.minusDays(1), DEADLINE,
                DEADLINE.plusDays(1), DEADLINE.plusDays(2),
                null, null, GroupPurchase.Status.OPEN
        );
    }
}