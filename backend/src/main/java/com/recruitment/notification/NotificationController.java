package com.recruitment.notification;

import com.recruitment.notification.dto.NotificationPageResponse;
import com.recruitment.notification.dto.NotificationResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Dung chung cho ca HR lan candidate - notifications.user_id tro thang users(id) truc tiep, khong
// phan biet duoc role tu path (xem SecurityConfig - day la endpoint dung-chung-2-role dau tien cua
// du an). Quyen so huu kiem o NotificationService, khong o day.
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationPageResponse list(
            Authentication authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        UUID userId = UUID.fromString(authentication.getName());
        return notificationService.list(userId, page, size);
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(Authentication authentication, @PathVariable UUID id) {
        UUID userId = UUID.fromString(authentication.getName());
        return notificationService.markRead(userId, id);
    }
}
