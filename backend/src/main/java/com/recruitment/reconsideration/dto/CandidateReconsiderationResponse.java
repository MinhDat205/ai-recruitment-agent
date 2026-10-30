package com.recruitment.reconsideration.dto;

import java.time.Instant;

// FR-C09 R-C1, muc 4.4 - khoi xem xet lai phia UNG VIEN (C1, E1 ung vien). DTO rieng, KHONG tai dung DTO phia HR: khong
// decidedBy, khong id nguoi HR, khong identityMasked, khong diem/rubric/giai thich AI/ghi chu HR (R-C3). Moi gia tri thoi
// gian do backend tinh (R-T4) - giao dien chi hien thi, khong tu so voi dong ho may.
public record CandidateReconsiderationResponse(
        CandidateReconsiderationState state,
        Instant deadline, // luon co khi khoi khac null
        Instant requestedAt, // null khi chua gui
        String reason, // nguyen van, null khi khong nhap
        Instant decidedAt, // null khi chua quyet
        String hrResponse) {} // chi khi DECLINED; nguyen van, null khi HR khong nhap
