package com.recruitment.messagedraft;

import com.recruitment.ai.messagedraft.DraftScenario;
import com.recruitment.ai.messagedraft.MessageDraftService;
import com.recruitment.aicontext.ContextViewer;
import com.recruitment.aicontext.ConversationContext;
import com.recruitment.aicontext.ConversationContextAssembler;
import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.ConversationReadOnlyException;
import com.recruitment.common.exception.DraftScenarioUnavailableException;
import com.recruitment.common.exception.InvalidDraftRequestException;
import com.recruitment.interviewinvitation.InterviewInvitationRepository;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.HrApplicationAccess;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.messagedraft.dto.DraftScenariosResponse;
import com.recruitment.messagedraft.dto.DraftScenariosResponse.ScenarioOption;
import com.recruitment.messagedraft.dto.MessageDraftRequest;
import com.recruitment.messagedraft.dto.MessageDraftResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

// FR-C07 - dieu phoi A1/A2. KHONG @Transactional (R-K3-4, CLAUDE.md muc 3c): moi buoc nap du lieu chay trong transaction
// ngan rieng cua repository/K1 va dong truoc khi goi AI - K3 nem IllegalStateException neu lo bi boc transaction.
//
// Kiem quyen DUNG LAI hai co che cua FR-C06 (R-Q1), khong viet ban thu ba: HR - HrApplicationAccess.loadOwned (khong
// cong ty 404, khong co don 404, don cong ty khac 403); ung vien - findByIdAndCandidateId (don nguoi khac va don khong
// ton tai CUNG 404). Vai tro suy tu controller (duong dan), khong nhan tu request (R-Q2).
//
// Thu tu A2 (R-Q3): quyen -> don WITHDRAWN (R-S2) -> hop le request (R-S1 dung phia, R-S5, R-S4) -> dieu kien tinh huong
// (R-S3) -> K1 -> K3. Dung o buoc loi dau tien; moi loi truoc K3 khong goi ChatModel. Ngoai le R-Q3b (body khong doc
// duoc) xu ly o MessageDraftExceptionAdvice, truoc khi vao day.
//
// Khong luu gi, khong goi M2, khong doi trang thai don, khong tao thong bao (R-D1, R-D2, R-S6).
@Service
public class MessageDraftFacade {

    static final int MAX_CUSTOM_PURPOSE_LENGTH = 500;

    private final HrApplicationAccess hrApplicationAccess;
    private final JobApplicationRepository jobApplicationRepository;
    private final InterviewInvitationRepository interviewInvitationRepository;
    private final ConversationContextAssembler contextAssembler;
    private final MessageDraftService messageDraftService;

    public MessageDraftFacade(
            HrApplicationAccess hrApplicationAccess,
            JobApplicationRepository jobApplicationRepository,
            InterviewInvitationRepository interviewInvitationRepository,
            ConversationContextAssembler contextAssembler,
            MessageDraftService messageDraftService) {
        this.hrApplicationAccess = hrApplicationAccess;
        this.jobApplicationRepository = jobApplicationRepository;
        this.interviewInvitationRepository = interviewInvitationRepository;
        this.contextAssembler = contextAssembler;
        this.messageDraftService = messageDraftService;
    }

    // ---- A1 ----

    public DraftScenariosResponse scenariosAsHr(UUID ownerId, UUID applicationId) {
        return scenarios(ContextViewer.HR, loadForHr(ownerId, applicationId));
    }

    public DraftScenariosResponse scenariosAsCandidate(UUID candidateId, UUID applicationId) {
        return scenarios(ContextViewer.CANDIDATE, loadForCandidate(candidateId, applicationId));
    }

    // ---- A2 ----

    public MessageDraftResponse draftAsHr(UUID ownerId, UUID applicationId, MessageDraftRequest request) {
        return draft(ContextViewer.HR, loadForHr(ownerId, applicationId), request);
    }

    public MessageDraftResponse draftAsCandidate(UUID candidateId, UUID applicationId, MessageDraftRequest request) {
        return draft(ContextViewer.CANDIDATE, loadForCandidate(candidateId, applicationId), request);
    }

    // ---- noi bo ----

    private DraftScenariosResponse scenarios(ContextViewer side, JobApplication application) {
        requireWritable(application);
        ApplicationStatus status = application.getStatus();
        InvitationState invitation = invitationState(application.getId());
        List<ScenarioOption> options = DraftScenario.forSide(side).stream()
                .map(scenario -> {
                    DraftUnavailableReason reason = unavailableReason(scenario, status, invitation);
                    return new ScenarioOption(scenario, reason == null, reason);
                })
                .toList();
        return new DraftScenariosResponse(options);
    }

    private MessageDraftResponse draft(ContextViewer side, JobApplication application, MessageDraftRequest request) {
        requireWritable(application);
        String customPurpose = validateRequest(side, request);
        DraftScenario scenario = request.scenario();
        if (unavailableReason(scenario, application.getStatus(), invitationState(application.getId())) != null) {
            throw new DraftScenarioUnavailableException();
        }

        ConversationContext context = contextAssembler.forConversation(side, application.getId());
        String draft = messageDraftService.draft(context, scenario, request.tone(), customPurpose);
        return new MessageDraftResponse(draft);
    }

    // R-S3 - dieu kien cot "Dieu kien dung duoc" cua bang R-S1 (ngoai R-S2). null = dung duoc. CUSTOM luon dung duoc o
    // day - customPurpose kiem o buoc hop le cua request (R-S4, 400), khong phai 409. Dat o facade (khong o DraftScenario)
    // de ai/messagedraft/ khong import messagedraft/ (L11).
    //
    // FR-H12 R-K2x - hai co tinh tu giay moi HIEU LUC: INTERVIEW_REMINDER can khung DA CHOT; REQUEST_RESCHEDULE chi can co
    // giay moi hieu luc (chot hay chua); THANK_AFTER_INTERVIEW can co giay moi hieu luc. KHONG them ma ly do moi (Q3):
    // giay moi chua chot van tra NO_ACTIVE_INTERVIEW cho INTERVIEW_REMINDER.
    static DraftUnavailableReason unavailableReason(
            DraftScenario scenario, ApplicationStatus status, InvitationState invitation) {
        boolean invited = status == ApplicationStatus.INTERVIEW_INVITED;
        return switch (scenario) {
            case RESULT_NOTICE -> (status == ApplicationStatus.HIRED || status == ApplicationStatus.REJECTED)
                    ? null
                    : DraftUnavailableReason.RESULT_NOT_FINAL;
            case INTERVIEW_REMINDER -> (invited && invitation.confirmed())
                    ? null
                    : DraftUnavailableReason.NO_ACTIVE_INTERVIEW;
            case REQUEST_RESCHEDULE -> (invited && invitation.active())
                    ? null
                    : DraftUnavailableReason.NO_ACTIVE_INTERVIEW;
            case THANK_AFTER_INTERVIEW -> invitation.active() ? null : DraftUnavailableReason.NO_INTERVIEW;
            case REQUEST_MORE_INFO, THANK_FOR_APPLYING, ASK_PROGRESS, CUSTOM -> null;
        };
    }

    // R-S2 - "gui duoc" cua C06 (status != WITHDRAWN); cung ma va cau 409 CONVERSATION_READ_ONLY.
    private static void requireWritable(JobApplication application) {
        if (application.getStatus() == ApplicationStatus.WITHDRAWN) {
            throw new ConversationReadOnlyException();
        }
    }

    // R-S1 (dung phia), R-S5, R-S4. Tra customPurpose da chuan hoa khi CUSTOM, null voi tinh huong khac (bi bo qua).
    private static String validateRequest(ContextViewer side, MessageDraftRequest request) {
        if (request.scenario() == null || !request.scenario().usableBy(side)) {
            throw InvalidDraftRequestException.invalidScenario();
        }
        if (request.tone() == null) {
            throw InvalidDraftRequestException.missingTone();
        }
        if (request.scenario() != DraftScenario.CUSTOM) {
            return null;
        }
        String purpose = request.customPurpose() == null
                ? null
                : request.customPurpose().replace("\r\n", "\n").replace('\r', '\n');
        if (purpose == null || purpose.isBlank()) {
            throw InvalidDraftRequestException.emptyPurpose();
        }
        if (purpose.length() > MAX_CUSTOM_PURPOSE_LENGTH) {
            throw InvalidDraftRequestException.purposeTooLong();
        }
        return purpose;
    }

    // FR-H12 R-K2x - active: don co giay moi hieu luc (superseded_at IS NULL, R-A1); confirmed: giay moi hieu luc da chot
    // khung. Moi don da co giay moi deu co dung mot giay moi hieu luc (uq_invitation_active), nen "co giay moi hieu luc"
    // giu nghia "co giay moi" cu cua THANK_AFTER_INTERVIEW.
    record InvitationState(boolean active, boolean confirmed) {
    }

    // Dung method repository co san (FR-H12 R-A1), khong them method moi.
    private InvitationState invitationState(UUID applicationId) {
        return interviewInvitationRepository
                .findByApplicationIdAndSupersededAtIsNull(applicationId)
                .map(invitation -> new InvitationState(true, invitation.getConfirmedSlotId() != null))
                .orElse(new InvitationState(false, false));
    }

    private JobApplication loadForHr(UUID ownerId, UUID applicationId) {
        return hrApplicationAccess.loadOwned(ownerId, applicationId).application();
    }

    private JobApplication loadForCandidate(UUID candidateId, UUID applicationId) {
        return jobApplicationRepository
                .findByIdAndCandidateId(applicationId, candidateId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
    }
}
