package com.recruitment.screening;

import com.recruitment.common.exception.InvalidScreeningAnswerException;
import com.recruitment.common.exception.ScreeningAnswerViolation;
import com.recruitment.common.exception.ScreeningQuestionsChangedException;
import com.recruitment.screening.dto.ScreeningAnswerInput;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// FR-U09 - buoc chup cau tra loi sang loc cua ApplicationService.apply, chay TRONG transaction nop don (MANDATORY: goi
// ngoai transaction la loi lap trinh, khong tu mo transaction rieng). Hai buoc:
//   prepare - khoa hang jobs (FOR SHARE), so revision, doc cau dang hoat dong, kiem cau tra loi -> danh sach draft.
//             Chay TRUOC khi INSERT don de 409/400 xay ra truoc 409 APPLICATION_DUPLICATE (R-V3).
//   persist - ghi moi draft thanh mot dong application_screening_answers SAU khi don da co id (R-S1).
// Khoa giu toi commit nen ban chup luon khop dung bo cau hoi da kiem (R-R3, R-R5).
@Service
public class ScreeningCaptureService {

    static final int MAX_ANSWER_LENGTH = 500;

    private final ScreeningQuestionRepository questionRepository;
    private final ApplicationScreeningAnswerRepository answerRepository;

    public ScreeningCaptureService(
            ScreeningQuestionRepository questionRepository, ApplicationScreeningAnswerRepository answerRepository) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<ScreeningAnswerDraft> prepare(
            UUID jobId, Integer screeningRevision, List<ScreeningAnswerInput> answers) {
        // R-R3 - khoa TRUOC, roi moi doc cau hoi: tap cau hoi doc duoi day khong doi toi khi transaction nay commit.
        int currentRevision = questionRepository.findRevisionForShare(jobId);
        List<ScreeningQuestion> questions =
                questionRepository.findByJobIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(jobId);

        // R-R1: null = client khong thay cau hoi nao - chi nhan khi tin hien co 0 cau. R-R2: khac null phai bang dung.
        boolean changed = screeningRevision == null ? !questions.isEmpty() : screeningRevision != currentRevision;
        if (changed) {
            throw new ScreeningQuestionsChangedException();
        }

        Map<UUID, ScreeningAnswerInput> answerByQuestion = indexAnswers(questions, answers == null ? List.of() : answers);

        List<ScreeningAnswerDraft> drafts = new ArrayList<>(questions.size());
        for (ScreeningQuestion question : questions) {
            drafts.add(validate(question, answerByQuestion.get(question.getId())));
        }
        return drafts;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void persist(UUID applicationId, List<ScreeningAnswerDraft> drafts) {
        List<ApplicationScreeningAnswer> rows = drafts.stream()
                .map(draft -> ApplicationScreeningAnswer.capture(
                        applicationId, draft.question(), draft.answerText(), draft.answerYesNo()))
                .toList();
        // saveAllAndFlush: INSERT chay ngay trong request; moi vi pham da chan o prepare nen CHECK cua V13 khong bao gio
        // la noi bao loi (R-V).
        answerRepository.saveAllAndFlush(rows);
    }

    // R-V2 buoc (1): duyet het danh sach tim UNKNOWN_QUESTION truoc, roi moi tim DUPLICATE_QUESTION.
    private static Map<UUID, ScreeningAnswerInput> indexAnswers(
            List<ScreeningQuestion> questions, List<ScreeningAnswerInput> answers) {
        Set<UUID> activeIds = new HashSet<>();
        for (ScreeningQuestion question : questions) {
            activeIds.add(question.getId());
        }
        for (ScreeningAnswerInput answer : answers) {
            if (answer == null || answer.questionId() == null || !activeIds.contains(answer.questionId())) {
                throw new InvalidScreeningAnswerException(ScreeningAnswerViolation.UNKNOWN_QUESTION);
            }
        }
        Map<UUID, ScreeningAnswerInput> byQuestion = new HashMap<>();
        for (ScreeningAnswerInput answer : answers) {
            if (byQuestion.putIfAbsent(answer.questionId(), answer) != null) {
                throw new InvalidScreeningAnswerException(ScreeningAnswerViolation.DUPLICATE_QUESTION);
            }
        }
        return byQuestion;
    }

    // R-V2 buoc (2): voi moi cau theo display_order - TYPE_MISMATCH -> REQUIRED_MISSING -> ANSWER_TOO_LONG ->
    // OPTION_NOT_FOUND. answer null = ung vien khong gui phan tu cho cau nay.
    private static ScreeningAnswerDraft validate(ScreeningQuestion question, ScreeningAnswerInput answer) {
        String rawText = answer == null ? null : answer.answerText();
        Boolean yesNo = answer == null ? null : answer.answerYesNo();

        if (question.getQuestionType() == ScreeningQuestionType.YES_NO) {
            if (rawText != null) {
                throw new InvalidScreeningAnswerException(ScreeningAnswerViolation.TYPE_MISMATCH);
            }
            if (yesNo == null && question.isRequired()) {
                throw new InvalidScreeningAnswerException(ScreeningAnswerViolation.REQUIRED_MISSING);
            }
            return new ScreeningAnswerDraft(question, null, yesNo);
        }

        if (yesNo != null) {
            throw new InvalidScreeningAnswerException(ScreeningAnswerViolation.TYPE_MISMATCH);
        }
        // Kiem null TRUOC khi strip; do dai tinh SAU khi strip, theo code point (khop char_length cua Postgres).
        String text = rawText == null ? "" : rawText.strip();
        if (text.isEmpty()) {
            if (question.isRequired()) {
                throw new InvalidScreeningAnswerException(ScreeningAnswerViolation.REQUIRED_MISSING);
            }
            // R-V5 - cau khong bat buoc bo trong van co dong, hai cot tra loi null.
            return new ScreeningAnswerDraft(question, null, null);
        }

        if (question.getQuestionType() == ScreeningQuestionType.SHORT_TEXT) {
            if (text.codePointCount(0, text.length()) > MAX_ANSWER_LENGTH) {
                throw new InvalidScreeningAnswerException(ScreeningAnswerViolation.ANSWER_TOO_LONG);
            }
            return new ScreeningAnswerDraft(question, text, null);
        }

        // SINGLE_CHOICE - so khop DUNG tung ky tu (phan biet hoa thuong) voi phuong an hien tai; luu CHU cua phuong an
        // trong cau hoi, khong luu chu client gui hay chi so (R-V4).
        for (String option : question.getOptions()) {
            if (option.equals(text)) {
                return new ScreeningAnswerDraft(question, option, null);
            }
        }
        throw new InvalidScreeningAnswerException(ScreeningAnswerViolation.OPTION_NOT_FOUND);
    }
}
