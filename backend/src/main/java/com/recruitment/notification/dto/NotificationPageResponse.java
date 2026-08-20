package com.recruitment.notification.dto;

import com.recruitment.common.dto.PageResponse;

// Boc them unreadCount canh trang du lieu - PHASES.md muc E2 chi doi hoi GET /api/notifications va
// PATCH .../read, khong co endpoint /unread-count rieng. Frontend doc badge so tu CHINH response
// nay, khong goi them request nao khac.
public record NotificationPageResponse(PageResponse<NotificationResponse> page, long unreadCount) {}
