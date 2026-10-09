package com.recruitment.messaging;

// Vai tro nguoi gui (R-M6) - suy tu duong dan endpoint (/api/hr/** hay /api/candidates/**), KHONG nhan tu
// request (R-Q3).
public enum MessageSenderRole {
    HR,
    CANDIDATE;

    public MessageSenderRole other() {
        return this == HR ? CANDIDATE : HR;
    }
}
