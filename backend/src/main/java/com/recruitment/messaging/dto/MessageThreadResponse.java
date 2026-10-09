package com.recruitment.messaging.dto;

import java.util.List;

// M1 - dung chung hai phia. KHONG them field nao khac (FR-C06 muc 4.4).
public record MessageThreadResponse(
        boolean canSend,              // R-M8
        boolean olderMessagesHidden,  // R-M7
        int unreadCount,              // so tin cua BEN KIA chua doc (R-A3)
        List<MessageResponse> messages) {}
