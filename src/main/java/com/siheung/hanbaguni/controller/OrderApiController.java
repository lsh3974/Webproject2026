package com.siheung.hanbaguni.controller;

import com.siheung.hanbaguni.dto.OrderCancelRequest;
import com.siheung.hanbaguni.dto.OrderCreateRequest;
import com.siheung.hanbaguni.dto.OrderResponse;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.security.LoginUser;
import com.siheung.hanbaguni.service.OrderReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderApiController {

    private final OrderReservationService orderReservationService;

    public OrderApiController(OrderReservationService orderReservationService) {
        this.orderReservationService = orderReservationService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> reserveOrder(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody OrderCreateRequest request) {

        Order savedOrder = orderReservationService.reserveOrder(
                loginUser.getUserId(),
                request.getGroupPurchaseId(),
                request.getQuantity()
        );
        Order loaded = orderReservationService.getOrderWithOwnershipCheck(savedOrder.getId(), loginUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(loaded));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> listMyOrders(
            @AuthenticationPrincipal LoginUser loginUser) {

        List<OrderResponse> responses = orderReservationService.getMyOrders(loginUser.getUserId())
                .stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getMyOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal LoginUser loginUser) {

        Order order = orderReservationService.getOrderWithOwnershipCheck(orderId, loginUser.getUserId());
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @PostMapping("/{orderId}/cancel-reservation")
    public ResponseEntity<OrderResponse> cancelReservation(
            @PathVariable Long orderId,
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody(required = false) @Valid OrderCancelRequest request) {

        String reason = (request != null) ? request.getCancelReason() : null;
        Order cancelled = orderReservationService.cancelReservation(orderId, loginUser.getUserId(), reason);
        return ResponseEntity.ok(OrderResponse.from(cancelled));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", e.getStatusCode().value());
        body.put("message", e.getReason() != null ? e.getReason() : "요청을 처리할 수 없습니다.");
        return ResponseEntity.status(e.getStatusCode()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = (fieldError != null) ? fieldError.getDefaultMessage() : "입력값이 올바르지 않습니다.";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("message", message);
        return ResponseEntity.badRequest().body(body);
    }
}
