package com.recruitment.common.text;

// FR-U12 L8 - bo chu cai tieng Viet CO DAU, dang dung san (NFC), ca thuong va hoa. Chi hang, khong phu thuoc package nao.
// Dung chung: kiem glyph T6 (cvbuilder/) va R-A4 (ai/cvrephrase/, dot 5).
// 67 chu moi bo = 11 nguyen am x 5 thanh (sac, huyen, hoi, nga, nang) + 6 nguyen am mang dau phu khong thanh
// (a-trang, a-mu, e-mu, o-mu, o-moc, u-moc) + d-gach. Nguyen am tron a, e, i, o, u, y KHONG nam trong bo.
public final class VietnameseLetters {

    private VietnameseLetters() {}

    public static final String LOWERCASE =
            "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ";

    public static final String UPPERCASE =
            "ÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ";

    public static final String ALL = LOWERCASE + UPPERCASE;
}
