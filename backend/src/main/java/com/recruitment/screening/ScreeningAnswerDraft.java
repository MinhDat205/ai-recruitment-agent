package com.recruitment.screening;

// FR-U09 - mot dong ban chup da kiem va chuan hoa (R-V4, R-V5), chua ghi. Moi cau dang hoat dong cua tin co dung mot
// draft, ke ca cau khong bat buoc bo trong (answerText va answerYesNo deu null).
public record ScreeningAnswerDraft(ScreeningQuestion question, String answerText, Boolean answerYesNo) {
}
