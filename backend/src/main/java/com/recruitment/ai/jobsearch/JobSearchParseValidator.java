package com.recruitment.ai.jobsearch;

import com.recruitment.ai.citation.VerbatimQuoteVerifier;
import com.recruitment.ai.jobsearch.JobSearchParsePayload.CodeCondition;
import com.recruitment.ai.jobsearch.JobSearchParsePayload.SalaryCondition;
import java.util.ArrayList;
import java.util.List;

// FR-U13 R-V - ham kiem output tach dieu kien, truyen cho K3 lam predicate: false = output hong -> K3 thu lai 1 lan ->
// van hong -> 502 AI_INVALID_OUTPUT. HAM TONG: khong bao gio nem exception (SyncAiCaller goi validator ngoai try,
// exception o day thanh 500 thay vi thu lai/502) - moi tham so/phan tu null deu tra false.
//
// q = cau DA chuan hoa R-I2 (NFC + normalizeWhitespace). Truoc khi kiem, moi chuoi output doi nguoc ‹ › -> < > (R-P2).
// Ma la, ma OTHER, luong am, min > max KHONG phai output hong - jobsearch/ xu ly o R-C (bo dieu kien, giu chu).
final class JobSearchParseValidator {

    private JobSearchParseValidator() {}

    // R-V1 - toi da 10 phan tu moi danh sach.
    static final int MAX_ITEMS = 10;

    static boolean isValid(JobSearchParsePayload payload, String q) {
        if (payload == null || q == null) {
            return false;
        }
        JobSearchParsePayload p = unsanitize(payload);
        if (!conditionsValid(p.locations(), q)
                || !conditionsValid(p.industries(), q)
                || !conditionsValid(p.workModes(), q)) {
            return false;
        }
        if (!sizeValid(p.personalInfoTexts())) {
            return false;
        }
        for (String text : p.personalInfoTexts()) {
            if (!sourceTextValid(text, q)) {
                return false;
            }
        }
        return salaryValid(p.salary(), q);
    }

    private static boolean conditionsValid(List<CodeCondition> conditions, String q) {
        if (!sizeValid(conditions)) {
            return false;
        }
        for (CodeCondition condition : conditions) {
            if (!sourceTextValid(condition.sourceText(), q)) {
                return false;
            }
        }
        return true;
    }

    // R-V1 - <= 10 phan tu, khong phan tu null.
    private static boolean sizeValid(List<?> items) {
        if (items.size() > MAX_ITEMS) {
            return false;
        }
        for (Object item : items) {
            if (item == null) {
                return false;
            }
        }
        return true;
    }

    // R-V3 - salary null hop le; khac null thi can it nhat mot moc va sourceText nguyen van.
    private static boolean salaryValid(SalaryCondition salary, String q) {
        if (salary == null) {
            return true;
        }
        if (salary.minMillionVnd() == null && salary.maxMillionVnd() == null) {
            return false;
        }
        return sourceTextValid(salary.sourceText(), q);
    }

    // R-V2 - khac null, khac rong sau gop khoang trang, nguyen van trong q (K2).
    private static boolean sourceTextValid(String text, String q) {
        if (text == null || VerbatimQuoteVerifier.normalizeWhitespace(text).isEmpty()) {
            return false;
        }
        return VerbatimQuoteVerifier.isVerbatim(text, q);
    }

    // R-P2 - doi nguoc MOI chuoi cua payload (ma va doan chu). HAM TONG: phan tu null giu null (validator bat sau).
    // JobSearchParseService tra ban da doi nguoc nay cho jobsearch/ - cung chuoi validator da kiem.
    static JobSearchParsePayload unsanitize(JobSearchParsePayload payload) {
        if (payload == null) {
            return null;
        }
        SalaryCondition salary = payload.salary();
        return new JobSearchParsePayload(
                unsanitizeConditions(payload.locations()),
                unsanitizeConditions(payload.industries()),
                salary == null
                        ? null
                        : new SalaryCondition(
                                salary.minMillionVnd(), salary.maxMillionVnd(), unsanitizeText(salary.sourceText())),
                unsanitizeConditions(payload.workModes()),
                unsanitizeTexts(payload.personalInfoTexts()));
    }

    private static List<CodeCondition> unsanitizeConditions(List<CodeCondition> conditions) {
        List<CodeCondition> result = new ArrayList<>(conditions.size());
        for (CodeCondition condition : conditions) {
            result.add(condition == null
                    ? null
                    : new CodeCondition(unsanitizeText(condition.code()), unsanitizeText(condition.sourceText())));
        }
        return result;
    }

    private static List<String> unsanitizeTexts(List<String> texts) {
        List<String> result = new ArrayList<>(texts.size());
        for (String text : texts) {
            result.add(unsanitizeText(text));
        }
        return result;
    }

    // Gioi han da biet (R-P2): cau co san ‹ › that -> doan AI tro toi mang < > -> khong khop K2 -> output hong.
    private static String unsanitizeText(String value) {
        return value == null ? null : value.replace('‹', '<').replace('›', '>');
    }
}
