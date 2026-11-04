package com.siheung.hanbaguni.payment;

import com.siheung.hanbaguni.security.LoginUser;
import com.siheung.hanbaguni.service.OrderReservationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

import static com.siheung.hanbaguni.payment.PaymentData.*;

@Controller
@RequestMapping("/orders/{orderId}/payment")
public class PaymentController {

    private final PaymentService payments;
    private final OrderReservationService reservations;
    private final String clientKey;
    private final String baseUrl;

    public PaymentController(
            PaymentService payments,
            OrderReservationService reservations,
            @Value("${payment.toss.client-key:}") String clientKey,
            @Value("${payment.base-url:http://localhost:8080}")
            String baseUrl
    ) {
        this.payments = payments;
        this.reservations = reservations;
        this.clientKey = clientKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @GetMapping
    public String page(
            @PathVariable Long orderId,
            @AuthenticationPrincipal LoginUser loginUser,
            Model model
    ) {
        addPageModel(orderId, loginUser, model, "checkout");
        return "payment/checkout";
    }

    @GetMapping("/success")
    public String success(
            @PathVariable Long orderId,
            @AuthenticationPrincipal LoginUser loginUser,
            Model model
    ) {
        addPageModel(orderId, loginUser, model, "success");
        return "payment/checkout";
    }

    @GetMapping("/fail")
    public String fail(
            @PathVariable Long orderId,
            @AuthenticationPrincipal LoginUser loginUser,
            Model model
    ) {
        addPageModel(orderId, loginUser, model, "fail");
        return "payment/checkout";
    }

    @PostMapping("/prepare")
    @ResponseBody
    public Prepared prepare(
            @PathVariable Long orderId,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        if (!clientKey.startsWith("test_ck_")) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Toss API 개별 연동 테스트 클라이언트 키를 설정해주세요."
            );
        }

        return payments.prepare(orderId, loginUser.getUserId());
    }

    @PostMapping("/confirm")
    @ResponseBody
    public Result confirm(
            @PathVariable Long orderId,
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody ConfirmRequest request
    ) {
        return payments.confirm(
                orderId,
                loginUser.getUserId(),
                request
        );
    }

    @PostMapping("/retry")
    @ResponseBody
    public Result retry(
            @PathVariable Long orderId,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return payments.retry(orderId, loginUser.getUserId());
    }

    private void addPageModel(
            Long orderId,
            LoginUser loginUser,
            Model model,
            String mode
    ) {
        var order = reservations.getOrderWithOwnershipCheck(
                orderId,
                loginUser.getUserId()
        );

        model.addAttribute("order", order);
        model.addAttribute("mode", mode);
        model.addAttribute("clientKey", clientKey);
        model.addAttribute("customerKey", UUID.randomUUID().toString());

        String paymentUrl =
                baseUrl + "/orders/" + orderId + "/payment";

        model.addAttribute("successUrl", paymentUrl + "/success");
        model.addAttribute("failUrl", paymentUrl + "/fail");
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleStatus(
            ResponseStatusException exception
    ) {
        String message = exception.getReason() != null
                ? exception.getReason()
                : "요청을 처리할 수 없습니다.";

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(Map.of("message", message));
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleConcurrency() {
        return ResponseEntity.status(409)
                .body(Map.of(
                        "message",
                        "다른 요청이 처리 중입니다. 잠시 후 다시 확인해주세요."
                ));
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleState(
            IllegalStateException exception
    ) {
        return ResponseEntity.status(409)
                .body(Map.of(
                        "message",
                        "결제 설정 또는 주문 상태를 확인해주세요."
                ));
    }
}