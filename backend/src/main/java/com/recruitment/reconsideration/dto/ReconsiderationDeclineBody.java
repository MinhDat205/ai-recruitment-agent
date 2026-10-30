package com.recruitment.reconsideration.dto;

// FR-C09 H2 - body { "response": string | null }. Phan hoi GUI CHO UNG VIEN, khong phai ghi chu noi bo (R-V3). Chuan hoa
// va do dai kiem o service (thu tu H2: quyen -> van ban -> UPDATE co dieu kien).
public record ReconsiderationDeclineBody(String response) {}
