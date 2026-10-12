package com.recruitment.ai.jobdraft;

import com.recruitment.ai.jobdraft.CriteriaDraftPayload.CriterionDraft;
import com.recruitment.ai.jobdraft.QuestionsDraftPayload.QuestionDraft;
import com.recruitment.common.exception.InvalidScreeningQuestionException;
import com.recruitment.screening.ScreeningQuestionRules;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// FR-H11 R-P3, R-C2, R-Q2 - ham validate cua ba phan, truyen vao SyncAiCaller.call: false -> K3 coi la output hong, thu
// lai 1 lan, hong tiep -> AI_INVALID_OUTPUT. Chi gom dieu kien lam HONG output; dieu kien lam VO HIEU mot truong (R-P4:
// nganh la/OTHER, dia danh, han nop qua khu, chuoi rong) la viec cua facade, khong o day.
// Do dai van ban: String.length() sau khi doi CRLF -> LF va bo khoang trang hai dau (R-P3). Cau hoi do theo
// ScreeningQuestionRules cua FR-H10 (code point) - khong chep so 300/100/2-10 (R-Q2, Q15).
public final class JobDraftOutputValidators {

    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_DESCRIPTION_LENGTH = 2000;
    static final int MAX_REQUIREMENTS_LENGTH = 1500;
    static final int MAX_BENEFITS_LENGTH = 1000;
    // NUMERIC(14,2) - phan nguyen toi da 12 chu so.
    static final BigDecimal SALARY_UPPER_BOUND_EXCLUSIVE = new BigDecimal("1000000000000");
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

    // Q14
    static final int MAX_CRITERIA = 6;
    static final int MAX_CRITERION_NAME_LENGTH = 100;
    static final int MAX_CRITERION_DESCRIPTION_LENGTH = 300;
    static final int MAX_QUESTIONS = 5;

    private JobDraftOutputValidators() {
    }

    public static boolean isValidJob(JobDraftPayload payload) {
        if (payload == null) {
            return false;
        }
        if (tooLong(payload.title(), MAX_TITLE_LENGTH)
                || tooLong(payload.description(), MAX_DESCRIPTION_LENGTH)
                || tooLong(payload.requirements(), MAX_REQUIREMENTS_LENGTH)
                || tooLong(payload.benefits(), MAX_BENEFITS_LENGTH)) {
            return false;
        }
        if (!validSalary(payload.salaryMin()) || !validSalary(payload.salaryMax())) {
            return false;
        }
        if (payload.salaryMin() != null
                && payload.salaryMax() != null
                && payload.salaryMax().compareTo(payload.salaryMin()) < 0) {
            return false;
        }
        // Chuoi chi co khoang trang duoc coi nhu null (R-P4 vo hieu no sau), khong lam hong output.
        String currency = clean(payload.salaryCurrency());
        return currency.isEmpty() || CURRENCY.matcher(currency).matches();
    }

    // R-C2 - 0 tieu chi la hop le (mo ta khong du can cu; giao dien bao trang thai rong).
    public static boolean isValidCriteria(CriteriaDraftPayload payload) {
        if (payload == null || payload.criteria().size() > MAX_CRITERIA) {
            return false;
        }
        Set<String> names = new HashSet<>();
        for (CriterionDraft criterion : payload.criteria()) {
            if (criterion == null) {
                return false;
            }
            String name = clean(criterion.name());
            if (name.isEmpty() || name.length() > MAX_CRITERION_NAME_LENGTH) {
                return false;
            }
            if (tooLong(criterion.description(), MAX_CRITERION_DESCRIPTION_LENGTH)) {
                return false;
            }
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    // R-Q2 - tung cau qua dung quy tac FR-H10 (ScreeningQuestionRules.normalize); them rieng cho goi y: <= 5 cau, loai
    // khac null, khong hai cau trung nhau (sau khi bo khoang trang, khong phan biet hoa thuong).
    public static boolean isValidQuestions(QuestionsDraftPayload payload) {
        if (payload == null || payload.questions().size() > MAX_QUESTIONS) {
            return false;
        }
        Set<String> texts = new HashSet<>();
        for (QuestionDraft question : payload.questions()) {
            if (question == null || question.questionType() == null) {
                return false;
            }
            ScreeningQuestionRules.NormalizedQuestion normalized;
            try {
                normalized = ScreeningQuestionRules.normalize(
                        question.questionType(), question.questionText(), question.options());
            } catch (InvalidScreeningQuestionException violation) {
                return false;
            }
            if (!texts.add(normalized.text().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private static boolean tooLong(String value, int max) {
        return clean(value).length() > max;
    }

    private static boolean validSalary(BigDecimal value) {
        return value == null
                || (value.signum() >= 0 && value.compareTo(SALARY_UPPER_BOUND_EXCLUSIVE) < 0);
    }

    // null -> "", doi CRLF/CR -> LF, bo khoang trang hai dau.
    static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").replace('\r', '\n').strip();
    }
}
