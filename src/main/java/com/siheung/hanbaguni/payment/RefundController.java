package com.siheung.hanbaguni.payment;

import com.siheung.hanbaguni.security.LoginUser;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.function.Supplier;

@Controller
@RequestMapping("/orders/{orderId}/refund")
public class RefundController {

    private final RefundService refunds;

    public RefundController(RefundService refunds) {
        this.refunds = refunds;
    }

    @PostMapping
    public String cancel(
            @PathVariable Long orderId,
            @RequestParam(name = "reason", defaultValue = "") String reason,
            @AuthenticationPrincipal LoginUser loginUser,
            RedirectAttributes redirectAttributes
    ) {
        return handle(
                orderId,
                redirectAttributes,
                () -> refunds.cancelMyOrder(
                        orderId,
                        loginUser.getUserId(),
                        reason
                )
        );
    }

    @PostMapping("/retry")
    public String retry(
            @PathVariable Long orderId,
            @AuthenticationPrincipal LoginUser loginUser,
            RedirectAttributes redirectAttributes
    ) {
        return handle(
                orderId,
                redirectAttributes,
                () -> refunds.retryMyRefund(
                        orderId,
                        loginUser.getUserId()
                )
        );
    }

    private String handle(
            Long orderId,
            RedirectAttributes redirectAttributes,
            Supplier<RefundData.Result> action
    ) {
        try {
            RefundData.Result result = action.get();

            String attribute = "REFUNDED".equals(result.status())
                    ? "successMessage"
                    : "warningMessage";

            redirectAttributes.addFlashAttribute(
                    attribute,
                    result.message()
            );

        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value()
                    != HttpStatus.BAD_REQUEST.value()
                    && exception.getStatusCode().value()
                    != HttpStatus.CONFLICT.value()) {
                // 권한 부족과 존재하지 않는 주문 등의 오류는 그대로 처리
                throw exception;
            }

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getReason() != null
                            ? exception.getReason()
                            : "환불 요청을 처리할 수 없습니다."
            );

        } catch (ConcurrencyFailureException exception) {
            redirectAttributes.addFlashAttribute(
                    "warningMessage",
                    "다른 요청이 처리 중입니다. 주문 상태를 확인한 뒤 다시 시도해주세요."
            );

        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "환불 처리 상태 또는 결제 설정 확인이 필요합니다."
            );
        }

        return "redirect:/orders/" + orderId;
    }
}