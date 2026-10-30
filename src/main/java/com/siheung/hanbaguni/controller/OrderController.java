package com.siheung.hanbaguni.controller;

import com.siheung.hanbaguni.dto.GroupPurchaseReservationSummary;
import com.siheung.hanbaguni.dto.OrderReservationForm;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.security.LoginUser;
import com.siheung.hanbaguni.service.OrderReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/orders")
public class OrderController {

    private final OrderReservationService orderReservationService;

    public OrderController(OrderReservationService orderReservationService) {
        this.orderReservationService = orderReservationService;
    }

    @GetMapping
    public String listMyOrders(
            @AuthenticationPrincipal LoginUser loginUser,
            Model model) {

        List<Order> orders = orderReservationService.getMyOrders(loginUser.getUserId());
        model.addAttribute("orders", orders);
        return "order/list";
    }

    @GetMapping("/new")
    public String showReservationForm(
            @RequestParam(name = "groupPurchaseId", required = false) Long groupPurchaseId,
            @AuthenticationPrincipal LoginUser loginUser,
            Model model) {

        Long userId = loginUser.getUserId();

        if (groupPurchaseId == null) {
            List<GroupPurchaseReservationSummary> openList =
                    orderReservationService.getOpenGroupPurchaseSummaries(userId);
            model.addAttribute("openGroupPurchases", openList);
            return "order/select";
        }

        GroupPurchaseReservationSummary summary =
                orderReservationService.getReservationSummary(groupPurchaseId, userId);

        OrderReservationForm form = new OrderReservationForm();
        form.setGroupPurchaseId(groupPurchaseId);
        form.setQuantity(1);

        model.addAttribute("summary", summary);
        model.addAttribute("orderForm", form);
        model.addAttribute("holdMinutes", OrderReservationService.RESERVATION_HOLD_MINUTES);
        return "order/new";
    }

    @PostMapping("/new")
    public String createReservation(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @ModelAttribute("orderForm") OrderReservationForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        Long userId = loginUser.getUserId();

        if (form.getGroupPurchaseId() == null) {
            return "redirect:/orders/new";
        }

        if (bindingResult.hasErrors()) {
            GroupPurchaseReservationSummary summary =
                    orderReservationService.getReservationSummary(form.getGroupPurchaseId(), userId);
            model.addAttribute("summary", summary);
            model.addAttribute("holdMinutes", OrderReservationService.RESERVATION_HOLD_MINUTES);
            return "order/new";
        }

        try {
            Order savedOrder = orderReservationService.reserveOrder(
                    userId,
                    form.getGroupPurchaseId(),
                    form.getQuantity()
            );
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "수량 예약이 완료되었습니다. 예약 만료 기한 내에 결제를 진행해주세요."
            );
            return "redirect:/orders/" + savedOrder.getId();
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST || e.getStatusCode() == HttpStatus.CONFLICT) {
                bindingResult.reject(
                        "order.reservation.failed",
                        e.getReason() != null ? e.getReason() : "수량 예약을 처리할 수 없습니다."
                );
                GroupPurchaseReservationSummary summary =
                        orderReservationService.getReservationSummary(form.getGroupPurchaseId(), userId);
                model.addAttribute("summary", summary);
                model.addAttribute("holdMinutes", OrderReservationService.RESERVATION_HOLD_MINUTES);
                return "order/new";
            }
            throw e;
        }
    }

    @GetMapping("/{orderId}")
    public String viewOrderDetail(
            @PathVariable Long orderId,
            @AuthenticationPrincipal LoginUser loginUser,
            Model model) {

        Order order = orderReservationService.getOrderWithOwnershipCheck(orderId, loginUser.getUserId());
        model.addAttribute("order", order);
        return "order/detail";
    }

    @PostMapping("/{orderId}/cancel")
    public String cancelReservation(
            @PathVariable Long orderId,
            @RequestParam(name = "cancelReason", required = false) String cancelReason,
            @AuthenticationPrincipal LoginUser loginUser,
            RedirectAttributes redirectAttributes) {

        try {
            Order updated = orderReservationService.cancelReservation(
                    orderId,
                    loginUser.getUserId(),
                    cancelReason
            );
            if (updated.getStatus() == Order.Status.EXPIRED) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        "예약 가능 시간이 이미 만료되어 만료 처리되었습니다."
                );
            } else {
                redirectAttributes.addFlashAttribute(
                        "successMessage",
                        "수량 예약이 정상적으로 취소되어 선점 수량이 반환되었습니다."
                );
            }
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        e.getReason() != null ? e.getReason() : "예약을 취소할 수 없습니다."
                );
            } else {
                throw e;
            }
        }

        return "redirect:/orders/" + orderId;
    }
}
