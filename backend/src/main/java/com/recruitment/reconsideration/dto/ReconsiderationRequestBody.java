package com.recruitment.reconsideration.dto;

// FR-C09 C1 - body { "reason": string | null }. Khong rang buoc o day: chuan hoa va do dai kiem o service SAU cac kiem
// trang thai (thu tu R-E5).
public record ReconsiderationRequestBody(String reason) {}
