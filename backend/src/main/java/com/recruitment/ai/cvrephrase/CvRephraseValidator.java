package com.recruitment.ai.cvrephrase;

import com.recruitment.common.text.VietnameseLetters;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// FR-U12 R-A4 - ham kiem goi y dien dat, truyen cho K3 lam predicate: false = output hong -> K3 thu lai 1 lan -> van
// hong -> 502 AI_INVALID_OUTPUT. HAM TONG: khong bao gio nem exception (SyncAiCaller goi validator ngoai try, exception o
// day thanh 500 thay vi thu lai/502) - moi tham so null deu tra false.
//
// in = doan goc DA chuan hoa (R-F3 o nhieu dong, facade lam); out = suggestion DA doi nguoc ‹ › (R-I2) va chuan hoa R-F3
// (CvRephraseService lam). out bang in van hop le. Gioi han da biet (R-A6): khong bat duoc ky nang/so lieu viet bang
// chu, doi nghia; heuristic ngon ngu sai voi doan tieng Anh co ten rieng tieng Viet co dau.
final class CvRephraseValidator {

    private CvRephraseValidator() {}

    // R-A4 buoc 2 - out.length() * 2 <= in.length() * 3 (<= 1,5 lan).
    static final int MAX_GROWTH_NUMERATOR = 3;
    static final int MAX_GROWTH_DENOMINATOR = 2;

    private static final Pattern DIGITS = Pattern.compile("[0-9]+");

    static boolean isValid(String in, String out) {
        if (in == null || out == null || out.strip().isEmpty()) {
            return false;
        }
        // long: khong tran so du do dai bat thuong.
        if ((long) out.length() * MAX_GROWTH_DENOMINATOR > (long) in.length() * MAX_GROWTH_NUMERATOR) {
            return false;
        }
        if (!digitRunsOf(in).containsAll(digitRunsOf(out))) {
            return false;
        }
        if (hasVietnameseLetter(in) != hasVietnameseLetter(out)) {
            return false;
        }
        return !hasDisallowedCharacter(out);
    }

    // R-A4 buoc 3 - tap day chu so lien [0-9]+; so khop NGUYEN day ("30" khong cho phep "3").
    static Set<String> digitRunsOf(String value) {
        Set<String> runs = new HashSet<>();
        Matcher matcher = DIGITS.matcher(value);
        while (matcher.find()) {
            runs.add(matcher.group());
        }
        return runs;
    }

    // R-A4 buoc 4 - co it nhat mot chu cai tieng Viet co dau (hang dung chung common/text/VietnameseLetters - L8).
    static boolean hasVietnameseLetter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (VietnameseLetters.ALL.indexOf(value.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    // R-A4 buoc 5 - ky tu loai R-F4: CONTROL (tru \n) va FORMAT. Cung luat CvBuilderFormValidator.hasDisallowedCharacter
    // (o nhieu dong); cai rieng o day vi ai/cvrephrase/ KHONG import cvbuilder/ (muc 4.2).
    static boolean hasDisallowedCharacter(String value) {
        return value.codePoints().anyMatch(cp -> {
            int type = Character.getType(cp);
            if (type == Character.CONTROL) {
                return cp != '\n';
            }
            return type == Character.FORMAT;
        });
    }
}
