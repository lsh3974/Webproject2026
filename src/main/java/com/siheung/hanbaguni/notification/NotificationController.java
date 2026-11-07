package com.siheung.hanbaguni.notification;

import com.siheung.hanbaguni.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Map;

@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationInboxService inbox;
    private final NotificationPushSubscriptionService pushSubscriptions;
    private final NotificationProperties properties;

    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        model.addAttribute("notifications", inbox.list(loginUser.getUserId()));
        model.addAttribute("unreadCount", inbox.unreadCount(loginUser.getUserId()));
        return "notifications/list";
    }

    @PostMapping("/{notificationId}/read")
    public String read(@PathVariable Long notificationId, @AuthenticationPrincipal LoginUser loginUser) {
        return "redirect:" + inbox.markRead(notificationId, loginUser.getUserId());
    }

    @PostMapping("/read-all")
    public String readAll(@AuthenticationPrincipal LoginUser loginUser, RedirectAttributes redirectAttributes) {
        inbox.markAllRead(loginUser.getUserId());
        redirectAttributes.addFlashAttribute("successMessage", "모든 알림을 읽음 처리했습니다.");
        return "redirect:/notifications";
    }

    @GetMapping("/preferences")
    public String preferences(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        model.addAttribute("form", inbox.preferenceForm(loginUser.getUserId()));
        model.addAttribute("contactStatus", inbox.contactStatus(loginUser.getUserId()));
        model.addAttribute("vapidPublicKey", properties.getPush().getPublicKey());
        model.addAttribute("pushConfigured", !properties.getPush().getPublicKey().isBlank()
                && !properties.getPush().getPrivateKey().isBlank());
        return "notifications/preferences";
    }

    @PostMapping("/preferences")
    public String updatePreferences(@AuthenticationPrincipal LoginUser loginUser,
                                    @ModelAttribute("form") NotificationPreferenceForm form,
                                    RedirectAttributes redirectAttributes) {
        inbox.updatePreferences(loginUser.getUserId(), form);
        redirectAttributes.addFlashAttribute("successMessage", "알림 수신 설정을 저장했습니다.");
        return "redirect:/notifications/preferences";
    }

    @GetMapping("/api/unread-count")
    @ResponseBody
    public Map<String, Long> unreadCount(@AuthenticationPrincipal LoginUser loginUser) {
        return Map.of("count", inbox.unreadCount(loginUser.getUserId()));
    }

    @GetMapping("/api/recent")
    @ResponseBody
    public java.util.List<NotificationView> recent(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "6") int limit) {
        return inbox.recent(loginUser.getUserId(), limit);
    }

    @PostMapping("/api/{notificationId}/read")
    @ResponseBody
    public Map<String, String> markReadApi(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Map.of("redirectUrl", inbox.markRead(notificationId, loginUser.getUserId()));
    }

    @PostMapping("/api/push-subscriptions")
    @ResponseBody
    public ResponseEntity<Void> subscribe(@AuthenticationPrincipal LoginUser loginUser,
                                           @Valid @RequestBody PushSubscriptionRequest request) {
        pushSubscriptions.subscribe(loginUser.getUserId(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/push-subscriptions")
    @ResponseBody
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal LoginUser loginUser,
                                            @RequestParam String endpoint) {
        pushSubscriptions.unsubscribe(loginUser.getUserId(), endpoint);
        return ResponseEntity.noContent().build();
    }
}
