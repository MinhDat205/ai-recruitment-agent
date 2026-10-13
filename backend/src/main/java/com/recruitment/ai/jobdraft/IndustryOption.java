package com.recruitment.ai.jobdraft;

// FR-H11 muc 5.2 - mot dong cua danh sach ma nganh dua vao tham so {industries} cua prompt phan JOB. Record rieng cua
// ai/jobdraft (khong import catalog/ - T21): noi goi doi tu CatalogRegistry.industries(). Thanh phan ten "name", khong
// "label" - T6(a) cam ten khop "label" o moi record cua ai/jobdraft.
public record IndustryOption(String code, String name) {
}
