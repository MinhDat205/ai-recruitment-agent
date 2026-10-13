package com.recruitment.interviewinvitation;

import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.InterviewInvitationNotFoundException;
import com.recruitment.common.exception.InterviewTemplateNotFoundException;
import com.recruitment.common.exception.InvalidInterviewScheduleException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.interviewinvitation.dto.CandidateInterviewInvitationResponse;
import com.recruitment.interviewinvitation.dto.InterviewInvitationPreviewResponse;
import com.recruitment.interviewinvitation.dto.InterviewInvitationResponse;
import com.recruitment.interviewinvitation.dto.InterviewInvitationSendRequest;
import com.recruitment.interviewtemplate.InterviewTemplate;
import com.recruitment.interviewtemplate.InterviewTemplateRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.ApplicationStatusService;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FR-H07 (E1) Dot 2 - render thu (preview) va gui that (send) giay moi phong van. KHONG import gi
// tu package scoring o day (khong ScoringRunRepository/CriterionScoreRepository...) - viec gui loi
// moi khong lien quan diem so, dung ranh gioi CLAUDE.md muc 2/7.
@Service
public class InterviewInvitationService {

    // Placeholder toi thieu ho tro trong subject/body cua template - quy uoc MOI, chua co truoc do
    // trong repo (interview_templates.body la text tu do, khong co dong templating nao san). Ngay
    // gio phong van CO CHU DICH khong co placeholder o day - preview khong render/doi hoi ngay gio,
    // HR dien qua field scheduledAt rieng o buoc gui (POST), khong nhet vao noi dung mau.
    private static final String PLACEHOLDER_CANDIDATE_NAME = "{{candidateName}}";
    private static final String PLACEHOLDER_JOB_TITLE = "{{jobTitle}}";
    private static final String PLACEHOLDER_COMPANY_NAME = "{{companyName}}";

    private final JobApplicationRepository jobApplicationRepository;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final InterviewTemplateRepository interviewTemplateRepository;
    private final UserRepository userRepository;
    private final InterviewInvitationRepository interviewInvitationRepository;
    private final InterviewInvitationSlotRepository slotRepository;
    private final ApplicationStatusService applicationStatusService;

    public InterviewInvitationService(
            JobApplicationRepository jobApplicationRepository,
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            InterviewTemplateRepository interviewTemplateRepository,
            UserRepository userRepository,
            InterviewInvitationRepository interviewInvitationRepository,
            InterviewInvitationSlotRepository slotRepository,
            ApplicationStatusService applicationStatusService) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.interviewTemplateRepository = interviewTemplateRepository;
        this.userRepository = userRepository;
        this.interviewInvitationRepository = interviewInvitationRepository;
        this.slotRepository = slotRepository;
        this.applicationStatusService = applicationStatusService;
    }

    // Chi doc, khong @Transactional - mau ApplicationOwnerService/ScoringRunService.listScoringRuns.
    public InterviewInvitationPreviewResponse previewInvitation(UUID ownerId, UUID applicationId) {
        JobApplication application = loadOwnedApplication(applicationId, ownerId);
        Job job = jobRepository.findById(application.getJobId()).orElseThrow(() -> new JobNotFoundException(application.getJobId()));
        InterviewTemplate template = loadTemplate(job.getId());
        Company company = requireOwnCompany(ownerId);
        User candidate = userRepository
                .findById(application.getCandidateId())
                .orElseThrow(() -> new IllegalStateException("Khong tim thay ung vien cua don: " + applicationId));

        // Render bang company_name DA DONG BANG trong template (luc tao job) - KHONG dung ten cong
        // ty hien tai cua Company, du hai gia tri co the da lech nhau (xem canh bao ben duoi).
        String subject = render(template.getSubject(), candidate.getFullName(), job.getTitle(), template.getCompanyName());
        String content = render(template.getBody(), candidate.getFullName(), job.getTitle(), template.getCompanyName());

        boolean mismatch = !template.getCompanyName().equals(company.getName());

        return new InterviewInvitationPreviewResponse(
                subject, content, candidate.getFullName(), mismatch, template.getCompanyName(), company.getName());
    }

    @Transactional
    public InterviewInvitationResponse sendInvitation(UUID ownerId, UUID applicationId, InterviewInvitationSendRequest request) {
        JobApplication application = loadOwnedApplication(applicationId, ownerId);
        Job job = jobRepository.findById(application.getJobId()).orElseThrow(() -> new JobNotFoundException(application.getJobId()));
        // Doi hoi template ton tai truoc khi gui - dam bao job/company hop le (tinh huong phong
        // thu, xem comment InterviewTemplate.java: JobOwnerService.create() luon tao template cung
        // Job, khong co duong nao thieu no trong luong binh thuong).
        loadTemplate(job.getId());

        if (!request.scheduledAt().isAfter(Instant.now())) {
            throw new InvalidInterviewScheduleException();
        }

        // Tai dung nguyen ven logic doi trang thai Dot 1: tu kiem don dang PENDING (khong thi 400
        // InvalidApplicationStatusTransitionException), tu ghi 1 dong application_status_history.
        // Method nay KHONG render/kiem tra gi ve noi dung loi moi - subject/content la nguyen van
        // HR gui, xem comment tren InterviewInvitationSendRequest.
        applicationStatusService.changeStatus(ownerId, applicationId, ApplicationStatus.INTERVIEW_INVITED);

        // FR-H12 dot 2 (REQUIREMENT muc 12 L7) - than request cu giu nguyen, ghi thanh MOT khung da chot theo ba buoc
        // L2: (1) giay moi CHUA co khung chot -> saveAndFlush; (2) khung -> saveAndFlush; (3) dat khung chot -> flush
        // (UPDATE). Dat confirmedSlotId truoc buoc (2) thi INSERT giay moi (hoan toi flush) vi pham FK ghep
        // fk_invitation_confirmed_slot. sentAt va confirmedAt cung MOT gia tri Instant (R-K1).
        Instant sentAt = Instant.now();
        InterviewInvitation invitation = new InterviewInvitation();
        invitation.setApplicationId(applicationId);
        invitation.setSubject(request.subject());
        invitation.setRenderedContent(request.content());
        invitation.setSentAt(sentAt);
        invitation.setSentBy(ownerId);
        InterviewInvitation saved = interviewInvitationRepository.saveAndFlush(invitation);

        InterviewInvitationSlot slot = slotRepository.saveAndFlush(
                new InterviewInvitationSlot(saved.getId(), 1, request.scheduledAt(), request.location()));

        saved.setConfirmedSlotId(slot.getId());
        saved.setConfirmedAt(sentAt);
        interviewInvitationRepository.flush();

        return toResponse(saved, slot);
    }

    // Chi doc, khong @Transactional - cung mau previewInvitation. Kiem so huu khac han
    // loadOwnedApplication (phia HR): so truc tiep application.candidateId, khong qua Company - vi
    // day la quyen cua UNG VIEN, khong phai HR.
    //
    // Co chu dich tra 403 (AccessDeniedException) cho "don cua nguoi khac", KHONG dung pattern
    // findByIdAndCandidateId->404-cho-ca-hai nhu ApplicationService.getMyApplicationHistory. Ly do:
    // applicationId la UUID v4 khong doan duoc va nguoi goi da dang nhap (JWT hop le), nen lo "don
    // nay ton tai nhung khong phai cua ban" khong cho ke tan cong thong tin khai thac duoc gi hon
    // mot UUID ngau nhien da co san; doi lai tach 403/404 giup debug ro rang hon o phia client. Hai
    // pattern (403 o day, 404-che-giau o ApplicationService) CUNG TON TAI co chu dich trong du an,
    // khong phai mot cho quen dong bo voi cho kia - xem walkthrough candidate-view-invitation.
    public CandidateInterviewInvitationResponse getLatestInvitationForCandidate(UUID candidateId, UUID applicationId) {
        JobApplication application = jobApplicationRepository
                .findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        if (!application.getCandidateId().equals(candidateId)) {
            throw new AccessDeniedException("Khong co quyen xem giay moi phong van cua don ung tuyen nay");
        }

        // findFirst tren danh sach da sap createdAt DESC: phong thu cho truong hop co nhieu dong
        // (hien luong nghiep vu that khong tao ra duoc - ALLOWED_TRANSITIONS chan gui loi moi lan
        // hai tren cung don, xem ApplicationStatusService), khong co duong nao qua API tao ra 2 dong
        // interview_invitations cho cung applicationId nen khong co test rieng cho nhanh nay.
        InterviewInvitation latest = interviewInvitationRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .findFirst()
                .orElseThrow(() -> new InterviewInvitationNotFoundException(applicationId));

        // Khong loc theo application.status - don da HIRED/REJECTED van doc lai duoc giay moi cu
        // (ung vien doi chieu lich hen sau khi da co ket qua cuoi).
        // FR-H12 dot 2 (muc 12 L7) - giu cach chon giay moi theo created_at nhu cu; chi doi nguon thoi diem/dia diem sang
        // khung chot cua CHINH giay moi da chon. Doi sang giay moi hieu luc (superseded_at) la viec cua dot 3 (I4).
        InterviewInvitationSlot slot = confirmedSlotOf(latest);
        return new CandidateInterviewInvitationResponse(
                slot == null ? null : slot.getScheduledAt(),
                slot == null ? null : slot.getLocation(),
                latest.getSubject(),
                latest.getRenderedContent(),
                latest.getSentAt());
    }

    // Khung chot cua giay moi; null khi giay moi chua chot (giay moi nhieu khung - chi co tu seed/du lieu ghi tay o dot 2).
    private InterviewInvitationSlot confirmedSlotOf(InterviewInvitation invitation) {
        if (invitation.getConfirmedSlotId() == null) {
            return null;
        }
        return slotRepository
                .findById(invitation.getConfirmedSlotId())
                .orElseThrow(() -> new IllegalStateException("Khong tim thay khung chot cua giay moi " + invitation.getId()));
    }

    private String render(String text, String candidateName, String jobTitle, String companyName) {
        return text.replace(PLACEHOLDER_CANDIDATE_NAME, candidateName)
                .replace(PLACEHOLDER_JOB_TITLE, jobTitle)
                .replace(PLACEHOLDER_COMPANY_NAME, companyName);
    }

    // Mau y het ApplicationStatusService.loadOwnedApplication - khong tach dung chung, dung tien
    // le lap lai cua du an.
    private JobApplication loadOwnedApplication(UUID applicationId, UUID ownerId) {
        JobApplication application = jobApplicationRepository
                .findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        Job job = jobRepository
                .findById(application.getJobId())
                .orElseThrow(() -> new JobNotFoundException(application.getJobId()));
        Company company = requireOwnCompany(ownerId);
        if (!job.getCompanyId().equals(company.getId())) {
            throw new AccessDeniedException("Khong co quyen truy cap giay moi phong van cua don ung tuyen nay");
        }
        return application;
    }

    private InterviewTemplate loadTemplate(UUID jobId) {
        return interviewTemplateRepository.findByJobId(jobId).orElseThrow(() -> new InterviewTemplateNotFoundException(jobId));
    }

    private Company requireOwnCompany(UUID ownerId) {
        return companyRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new CompanyNotFoundException("HR chưa tạo hồ sơ công ty"));
    }

    private static InterviewInvitationResponse toResponse(InterviewInvitation i, InterviewInvitationSlot slot) {
        return new InterviewInvitationResponse(
                i.getId(),
                i.getApplicationId(),
                slot.getScheduledAt(),
                slot.getLocation(),
                i.getSubject(),
                i.getRenderedContent(),
                i.getSentAt(),
                i.getSentBy());
    }
}
