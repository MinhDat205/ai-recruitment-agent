package com.recruitment.notification;

import com.recruitment.common.dto.PageResponse;
import com.recruitment.common.exception.NotificationNotFoundException;
import com.recruitment.notification.dto.NotificationPageResponse;
import com.recruitment.notification.dto.NotificationResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse list(UUID userId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size));
        Page<Notification> notificationPage = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        long unreadCount = notificationRepository.countByUserIdAndReadFalse(userId);
        return new NotificationPageResponse(
                PageResponse.from(notificationPage, NotificationResponse::from), unreadCount);
    }

    // Chi nguoi nhan (notification.userId) moi doi duoc trang thai da doc cua chinh no -
    // notifications.user_id tro thang users(id) nen kiem so huu don gian, khong can join qua
    // job/company nhu ApplicationStatusService.loadOwnedApplication.
    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository
                .findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        if (!notification.getUserId().equals(userId)) {
            throw new AccessDeniedException("Khong co quyen doi trang thai thong bao nay");
        }
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    private int safePage(Integer page) {
        return (page == null || page < 0) ? 0 : page;
    }

    private int safeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
