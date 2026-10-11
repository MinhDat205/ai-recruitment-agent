package com.recruitment.ai.messagedraft;

import com.recruitment.aicontext.ContextViewer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

// FR-C07 R-S1 - tinh huong co dinh theo phia. Thu tu khai bao = thu tu bang R-S1 = thu tu A1 tra ve. Mo ta tung ma
// cho AI nam trong prompt message-draft-v1.st, khong o day.
//
// Dat o ai/messagedraft/ (L11) de MessageDraftService dung duoc ma khong import messagedraft/ - phu thuoc MOT CHIEU
// messagedraft/ -> ai/messagedraft/. Dieu kien dung duoc theo trang thai don (R-S3) nam o MessageDraftFacade vi tra
// DraftUnavailableReason cua messagedraft/.
public enum DraftScenario {
    REQUEST_MORE_INFO(ContextViewer.HR),
    INTERVIEW_REMINDER(ContextViewer.HR),
    THANK_FOR_APPLYING(ContextViewer.HR),
    RESULT_NOTICE(ContextViewer.HR),
    ASK_PROGRESS(ContextViewer.CANDIDATE),
    THANK_AFTER_INTERVIEW(ContextViewer.CANDIDATE),
    REQUEST_RESCHEDULE(ContextViewer.CANDIDATE),
    CUSTOM(ContextViewer.HR, ContextViewer.CANDIDATE);

    private final Set<ContextViewer> sides;

    DraftScenario(ContextViewer... sides) {
        this.sides = Set.of(sides);
    }

    public boolean usableBy(ContextViewer side) {
        return sides.contains(side);
    }

    public static List<DraftScenario> forSide(ContextViewer side) {
        return Arrays.stream(values()).filter(scenario -> scenario.usableBy(side)).toList();
    }
}
