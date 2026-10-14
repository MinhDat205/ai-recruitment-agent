package com.recruitment.interviewinvitation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

// FR-U10 C1 (REQUIREMENT muc 4.5) - than request ung vien chon khung. KHONG co id giay moi: backend tu xac dinh giay moi
// hieu luc SAU khi khoa hang don (R-W2). Thieu slotId -> 400 dang map truong cua Bean Validation (O3).
public record ConfirmInterviewSlotRequest(@NotNull(message = "Vui lòng chọn một khung giờ.") UUID slotId) {
}
