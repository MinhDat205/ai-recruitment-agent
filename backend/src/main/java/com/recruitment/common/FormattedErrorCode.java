package com.recruitment.common;

// Hop dong chung cho cac enum ma loi chuan hoa (ResumeParsingErrorCode, CriterionScoringErrorCode,
// ScoringRunErrorCode...) dung cho cac cot *_error_message: buoc noi GHI (vd
// ScoringRunStateService.markFailed) chi nhan mot gia tri enum da co formatted() co dinh, KHONG
// nhan String tu do - trinh bien dich chan viec truyen nham e.getMessage() (stack trace) hoac
// output tho cua LLM vao cot loi.
//
// Dat o com.recruitment.common (khong phai com.recruitment.scoring hay com.recruitment.ai): package
// ai/ khong duoc import tu package scoring/ (xem CLAUDE.md muc 7 - "AI khong tinh tong diem", ranh
// gioi kien truc rong hon la ai/ khong phu thuoc nguoc vao cac package nghiep vu ben tren no).
// CriterionScoringErrorCode (ai.criterion) implement interface nay THI PHAI import no, nen interface
// KHONG THE dat trong scoring/ (se tao dung phu thuoc nguoc ai/ -> scoring/ dung cam). common/ la
// package trung lap: da xac minh common/ KHONG import gi tu ai/ lan scoring/ (chi chua exception/dto
// dung chung), nen ca hai phia deu co the phu thuoc vao common/ ma khong tao chu trinh (cycle) -
// CriterionScoringErrorCode them MOT dependency moi vao common/ (truoc gio ai/ chua dung toi common/),
// ScoringRunErrorCode (scoring/) cung phu thuoc common/ nhu cac exception khac trong package do.
public interface FormattedErrorCode {
    String formatted();
}
