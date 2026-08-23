package com.recruitment.notification;

// Khop dung 4 gia tri CHECK constraint cua notifications.email_status (V1__init_schema.sql).
// SKIPPED chua duoc dung o FR-C03 nay - khai du de enum anh xa dung cot, danh cho truong hop
// tuong lai (vd nguoi dung tat nhan email) khong can migration moi.
public enum EmailStatus {
    PENDING,
    SENT,
    FAILED,
    SKIPPED
}
