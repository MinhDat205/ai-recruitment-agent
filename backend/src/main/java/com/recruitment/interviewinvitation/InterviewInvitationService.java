package com.recruitment.interviewinvitation;

import com.recruitment.blindreview.BlindReviewResolver;
import com.recruitment.blindreview.CandidateAlias;
import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.InterviewInvitationConflictException;
import com.recruitment.common.exception.InterviewInvitationNotFoundException;
import com.recruitment.common.exception.InterviewSlotAlreadyConfirmedException;
import com.recruitment.common.exception.InterviewSlotPassedException;
import com.recruitment.common.exception.InterviewTemplateNotFoundException;
import com.recruitment.common.exception.InvitationRenderedTooLongException;
import com.recruitment.common.exception.InvitationResendNotAllowedException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.common.exception.SlotSelectionNotAllowedException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.interviewinvitation.InterviewSlotValidator.ValidSlot;
import com.recruitment.interviewinvitation.dto.CandidateInterviewInvitationResponse;
import com.recruitment.interviewinvitation.dto.InterviewInvitationListResponse;
import com.recruitment.interviewinvitation.dto.InterviewInvitationPreviewResponse;
import com.recruitment.interviewinvitation.dto.InterviewInvitationResponse;
import com.recruitment.interviewinvitation.dto.InterviewInvitationSendRequest;
import com.recruitment.interviewtemplate.InterviewTemplate;
import com.recruitment.interviewtemplate.InterviewTemplateRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.ApplicationStatusService;
import com.recruitment.jobapplication.HrApplicationAccess;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.notification.InterviewInvitationResentEvent;
import com.recruitment.notification.InterviewInvitationResentEvent.SlotInfo;
import com.recruitment.notification.InterviewSlotConfirmedEvent;
import com.recruitment.reconsideration.ReconsiderationHrService;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
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
    // HR dien qua danh sach khung gio rieng o buoc gui (POST), khong nhet vao noi dung mau (FR-H12 R-C1).
    private static final String PLACEHOLDER_CANDIDATE_NAME = "{{candidateName}}";
    private static final String PLACEHOLDER_JOB_TITLE = "{{jobTitle}}";
    private static final String PLACEHOLDER_COMPANY_NAME = "{{companyName}}";
    // FR-H16 R-I3 - cung gioi han @Size cua InterviewInvitationSendRequest (subject 255, content 10000), kiem lai SAU khi
    // thay ho ten (String.length(), cung cach dem cua @Size).
    static final int MAX_SUBJECT_LENGTH = 255;
    static final int MAX_CONTENT_LENGTH = 10000;

    private final JobApplicationRepository jobApplicationRepository;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final InterviewTemplateRepository interviewTemplateRepository;
    private final UserRepository userRepository;
    private final InterviewInvitationRepository interviewInvitationRepository;
    private final InterviewInvitationSlotRepository slotRepository;
    private final ApplicationStatusService applicationStatusService;
    private final HrApplicationAccess hrApplicationAccess;
    private final ApplicationEventPublisher eventPublisher;
    private final BlindReviewResolver blindReviewResolver;
    private final ReconsiderationHrService reconsiderationHrService;

    public InterviewInvitationService(
            JobApplicationRepository jobApplicationRepository,
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            InterviewTemplateRepository interviewTemplateRepository,
            UserRepository userRepository,
            InterviewInvitationRepository interviewInvitationRepository,
            InterviewInvitationSlotRepository slotRepository,
            ApplicationStatusService applicationStatusService,
            HrApplicationAccess hrApplicationAccess,
            ApplicationEventPublisher eventPublisher,
            BlindReviewResolver blindReviewResolver,
            ReconsiderationHrService reconsiderationHrService) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.interviewTemplateRepository = interviewTemplateRepository;
        this.userRepository = userRepository;
        this.interviewInvitationRepository = interviewInvitationRepository;
        this.slotRepository = slotRepository;
        this.applicationStatusService = applicationStatusService;
        this.hrApplicationAccess = hrApplicationAccess;
        this.eventPublisher = eventPublisher;
        this.blindReviewResolver = blindReviewResolver;
        this.reconsiderationHrService = reconsiderationHrService;
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

        // FR-H16 R-I1 [Q6] - don dang an danh: GIU NGUYEN chu {{candidateName}} (backend thay luc gui - R-I2),
        // candidateName = nhan. HR khong thay ho ten truoc khi bam gui.
        boolean masked = blindReviewResolver.isMasked(applicationId);
        String nameForRender = masked ? PLACEHOLDER_CANDIDATE_NAME : candidate.getFullName();

        // Render bang company_name DA DONG BANG trong template (luc tao job) - KHONG dung ten cong
        // ty hien tai cua Company, du hai gia tri co the da lech nhau (xem canh bao ben duoi).
        String subject = render(template.getSubject(), nameForRender, job.getTitle(), template.getCompanyName());
        String content = render(template.getBody(), nameForRender, job.getTitle(), template.getCompanyName());

        boolean mismatch = !template.getCompanyName().equals(company.getName());

        return new InterviewInvitationPreviewResponse(
                subject,
                content,
                masked ? CandidateAlias.of(applicationId) : candidate.getFullName(),
                mismatch,
                template.getCompanyName(),
                company.getName(),
                InterviewSlotValidator.MAX_SLOTS);
    }

    @Transactional
    public InterviewInvitationResponse sendInvitation(UUID ownerId, UUID applicationId, InterviewInvitationSendRequest request) {
        JobApplication application = loadOwnedApplication(applicationId, ownerId);
        Job job = jobRepository.findById(application.getJobId()).orElseThrow(() -> new JobNotFoundException(application.getJobId()));
        // Doi hoi template ton tai truoc khi gui - dam bao job/company hop le (tinh huong phong
        // thu, xem comment InterviewTemplate.java: JobOwnerService.create() luon tao template cung
        // Job, khong co duong nao thieu no trong luong binh thuong).
        loadTemplate(job.getId());

        // FR-H12 R-F1 - kiem khung (R-S8) TRUOC khi doi trang thai: dau vao sai thi khong ghi gi.
        List<ValidSlot> slots = InterviewSlotValidator.validate(request.slots(), Instant.now());

        // FR-H16 R-I2, R-I3 - don dang an danh TAI LUC GUI (doc trong CHINH transaction nay, TRUOC buoc doi trang thai -
        // sau buoc do da co dong lich su INTERVIEW_INVITED nen don het an danh): thay {{candidateName}} bang ho ten that
        // roi kiem lai do dai; vuot -> 400, chua ghi gi. Don khong an danh: luu nguyen van nhu H12 (khong thay).
        InterviewInvitationSendRequest toSave = blindReviewResolver.isMasked(applicationId)
                ? withCandidateName(request, application)
                : request;

        if (application.getStatus() == ApplicationStatus.REJECTED) {
            // FR-C09 R-S3 - nhanh DUY NHAT moi lai don bi tu choi, SAU buoc kiem an danh (don con an thi {{candidateName}}
            // da thay o tren). (0)-(1): yeu cau OPEN cua chinh don -> ACCEPTED (khong co yeu cau / da quyet -> 400 nhu
            // FR-H12 hien nay; thua tranh chap -> 409); (2) canh rieng REJECTED -> INTERVIEW_INVITED; (3) tao giay moi
            // nhu cu ben duoi. Ca ba trong CUNG transaction nay: buoc sau loi thi yeu cau van OPEN.
            reconsiderationHrService.claimForAcceptance(applicationId, ownerId);
            applicationStatusService.reinviteAfterReconsideration(ownerId, applicationId);
        } else {
            // Tai dung nguyen ven logic doi trang thai Dot 1: tu kiem don dang PENDING (khong thi 400
            // InvalidApplicationStatusTransitionException), tu ghi 1 dong application_status_history.
            // Method nay KHONG render/kiem tra gi ve noi dung loi moi - subject/content la nguyen van
            // HR gui, xem comment tren InterviewInvitationSendRequest.
            applicationStatusService.changeStatus(ownerId, applicationId, ApplicationStatus.INTERVIEW_INVITED);
        }

        // FR-H12 R-F2 - gui lan dau KHONG phat them thong bao nao (thong bao doi trang thai do buoc doi trang thai ngay tren phat).
        Instant sentAt = Instant.now();
        return createInvitation(applicationId, ownerId, toSave, slots, sentAt).response();
    }

    // FR-H16 R-I2, R-I3 - ban sao request voi {{candidateName}} da thay bang users.full_name; kiem do dai SAU khi thay.
    private InterviewInvitationSendRequest withCandidateName(
            InterviewInvitationSendRequest request, JobApplication application) {
        String fullName = userRepository
                .findById(application.getCandidateId())
                .map(User::getFullName)
                .orElseThrow(() -> new IllegalStateException("Khong tim thay ung vien cua don: " + application.getId()));
        String subject = request.subject().replace(PLACEHOLDER_CANDIDATE_NAME, fullName);
        String content = request.content().replace(PLACEHOLDER_CANDIDATE_NAME, fullName);
        if (subject.length() > MAX_SUBJECT_LENGTH || content.length() > MAX_CONTENT_LENGTH) {
            throw new InvitationRenderedTooLongException();
        }
        return new InterviewInvitationSendRequest(request.slots(), subject, content);
    }

    // FR-H12 I3 (R-G1..R-G6) - gui lai giay moi. MOT transaction ngan, DUNG thu tu R-G3 (bat buoc):
    // (1) kiem quyen; (2) kiem khung; (3) khoa hang don + kiem INTERVIEW_INVITED trong CUNG mot cau UPDATE (0 dong -> 409);
    // (4) lay now MOT lan; (5) danh dau giay moi hieu luc la cu (MOT cau UPDATE theo application_id); (6) tao giay moi moi +
    // khung (ba buoc L2); (7) phat su kien thong bao.
    // Khoa (3) PHAI chay TRUOC moi thao tac doc/danh dau giay moi hieu luc (5, 6): doc giay moi hieu luc truoc khi khoa thi
    // hai lan gui lai dong thoi lam luot sau vi pham uq_invitation_active (muc 8). Buoc (1) chi doc don/Job/cong ty.
    // KHONG goi ApplicationStatusService (khong doi trang thai), KHONG ghi lich su trang thai, KHONG doi mau giay moi ton
    // tai (R-G1). KHONG khoa bang SELECT co menh de khoa hay annotation khoa cua JPA - chi cau UPDATE co dieu kien (Q2).
    @Transactional
    public InterviewInvitationResponse resendInvitation(
            UUID ownerId, UUID applicationId, InterviewInvitationSendRequest request) {
        HrApplicationAccess.OwnedApplication owned = hrApplicationAccess.loadOwned(ownerId, applicationId); // (1)
        // (2) Moc rieng cho kiem "tuong lai" (R-S4) - buoc nay chay truoc khoa nen khong dung duoc moc cua buoc (4).
        List<ValidSlot> slots = InterviewSlotValidator.validate(request.slots(), Instant.now());

        if (jobApplicationRepository.lockIfInterviewInvited(applicationId) == 0) { // (3)
            throw new InvitationResendNotAllowedException();
        }

        Instant now = Instant.now(); // (4) - dung cho CA superseded_at cua giay moi cu LAN sent_at cua giay moi moi
        // (5) 0 dong van tiep tuc: don INTERVIEW_INVITED khong co giay moi chi co the do du lieu ghi tay.
        interviewInvitationRepository.markActiveSuperseded(applicationId, now);

        CreatedInvitation created = createInvitation(applicationId, ownerId, request, slots, now); // (6)

        // (7) Gia tri khung VUA LUU (da cat phut, da strip) - listener dung email tu day, khong doc lai bang giay moi.
        List<SlotInfo> slotInfos = created.slots().stream()
                .map(slot -> new SlotInfo(slot.getScheduledAt(), slot.getLocation()))
                .toList();
        JobApplication application = owned.application();
        eventPublisher.publishEvent(new InterviewInvitationResentEvent(
                applicationId, application.getJobId(), application.getCandidateId(), slotInfos));

        return created.response();
    }

    // FR-H12 I1 (R-A2) - moi giay moi cua don, hieu luc truoc roi giay moi cu theo superseded_at giam dan; khung theo
    // display_order. Don chua co giay moi -> 200 danh sach rong (khong 404). Khong loc theo trang thai don/Job.
    // Chi doc, khong @Transactional - cung mau previewInvitation.
    public InterviewInvitationListResponse listInvitations(UUID ownerId, UUID applicationId) {
        hrApplicationAccess.loadOwned(ownerId, applicationId);

        List<InterviewInvitation> invitations =
                new ArrayList<>(interviewInvitationRepository.findByApplicationId(applicationId));
        invitations.sort(Comparator.comparing(
                        InterviewInvitation::getSupersededAt, Comparator.nullsFirst(Comparator.<Instant>reverseOrder()))
                .thenComparing(InterviewInvitation::getSentAt, Comparator.nullsLast(Comparator.<Instant>reverseOrder())));

        Map<UUID, List<InterviewInvitationSlot>> slotsByInvitation = invitations.isEmpty()
                ? Map.of()
                : slotRepository
                        .findByInvitationIdInOrderByDisplayOrderAsc(
                                invitations.stream().map(InterviewInvitation::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(InterviewInvitationSlot::getInvitationId));

        List<InterviewInvitationResponse> items = invitations.stream()
                .map(i -> toResponse(i, slotsByInvitation.getOrDefault(i.getId(), List.of())))
                .toList();
        return new InterviewInvitationListResponse(applicationId, InterviewSlotValidator.MAX_SLOTS, items);
    }

    private record CreatedInvitation(InterviewInvitationResponse response, List<InterviewInvitationSlot> slots) {
    }

    // FR-H12 R-K1/R-K2 - ghi theo BA buoc (muc 12 L2): (1) giay moi CHUA co khung chot -> saveAndFlush; (2) cac khung ->
    // saveAllAndFlush; (3) giay moi MOT khung: dat khung chot + confirmedAt = sentAt (cung mot Instant) roi flush (UPDATE).
    // Dat confirmedSlotId truoc buoc (2) thi INSERT giay moi (hoan toi flush) vi pham FK ghep fk_invitation_confirmed_slot.
    // Giay moi nhieu khung giu confirmed_* = NULL - FR-H12 khong co duong nao chot khung cho no (R-K2, R-B1).
    private CreatedInvitation createInvitation(
            UUID applicationId, UUID ownerId, InterviewInvitationSendRequest request, List<ValidSlot> slots, Instant sentAt) {
        InterviewInvitation invitation = new InterviewInvitation();
        invitation.setApplicationId(applicationId);
        invitation.setSubject(request.subject());
        invitation.setRenderedContent(request.content());
        invitation.setSentAt(sentAt);
        invitation.setSentBy(ownerId);
        InterviewInvitation saved = interviewInvitationRepository.saveAndFlush(invitation);

        List<InterviewInvitationSlot> toSave = new ArrayList<>(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            ValidSlot slot = slots.get(i);
            toSave.add(new InterviewInvitationSlot(saved.getId(), i + 1, slot.scheduledAt(), slot.location()));
        }
        List<InterviewInvitationSlot> savedSlots = slotRepository.saveAllAndFlush(toSave);

        if (savedSlots.size() == 1) {
            saved.setConfirmedSlotId(savedSlots.get(0).getId());
            saved.setConfirmedAt(sentAt);
            interviewInvitationRepository.flush();
        }

        return new CreatedInvitation(toResponse(saved, savedSlots), savedSlots);
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
    public CandidateInterviewInvitationResponse getActiveInvitationForCandidate(UUID candidateId, UUID applicationId) {
        JobApplication application = jobApplicationRepository
                .findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        if (!application.getCandidateId().equals(candidateId)) {
            throw new AccessDeniedException("Khong co quyen xem giay moi phong van cua don ung tuyen nay");
        }

        // FR-H12 R-A1, R-A3 - CHI giay moi hieu luc (superseded_at IS NULL, toi da mot - uq_invitation_active), KHONG xac
        // dinh bang created_at/sent_at. Ung vien khong thay giay moi cu.
        InterviewInvitation active = interviewInvitationRepository
                .findByApplicationIdAndSupersededAtIsNull(applicationId)
                .orElseThrow(() -> new InterviewInvitationNotFoundException(applicationId));

        // Khong loc theo application.status - don da HIRED/REJECTED van doc lai duoc giay moi cu
        // (ung vien doi chieu lich hen sau khi da co ket qua cuoi).
        return toCandidateResponse(active);
    }

    // FR-U10 C1 (R-W1..R-W8) - ung vien chon MOT khung cua giay moi hieu luc. MOT transaction ngan, DUNG thu tu R-W1 (bat buoc):
    // (1) kiem quyen - CHI doc job_applications, khuon I4: don khong ton tai -> 404, don cua ung vien khac -> 403 (R-A1);
    // (2) khoa hang don + kiem INTERVIEW_INVITED trong CUNG mot cau (lockIfInterviewInvited) - 0 dong -> 409;
    // (3) lay now MOT lan - dung cho CA dieu kien "chua qua" LAN confirmed_at;
    // (4) ghi khung chot bang MOT cau UPDATE co dieu kien (confirmSlotIfAvailable);
    // (5) 1 dong -> doc lai giay moi + khung de dung DTO I4 (R-W7); 0 dong -> doc lai de tra dung ma (R-W5);
    // (6) phat su kien thong bao (R-N1) - CHI tren nhanh 1 dong; listener AFTER_COMMIT nen transaction rollback thi khong co
    // thong bao.
    // Khoa (2) PHAI chay TRUOC moi thao tac doc giay moi hieu luc (R-W2): I2, I3, Trung tuyen/Tu choi, Rut don deu ghi hang
    // job_applications, nen sau khi khoa giay moi hieu luc khong doi toi het transaction - doc o (5) phan anh dung ly do.
    // KHONG goi ApplicationStatusService, KHONG ghi lich su trang thai, KHONG doi trang thai don (R-S1). KHONG khoa bang
    // SELECT co menh de khoa hay annotation khoa cua JPA (R-W3).
    @Transactional
    public CandidateInterviewInvitationResponse confirmSlot(UUID candidateId, UUID applicationId, UUID slotId) {
        JobApplication application = jobApplicationRepository
                .findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId)); // (1)
        if (!application.getCandidateId().equals(candidateId)) {
            throw new AccessDeniedException("Khong co quyen chon khung gio cua don ung tuyen nay");
        }

        if (jobApplicationRepository.lockIfInterviewInvited(applicationId) == 0) { // (2)
            throw new SlotSelectionNotAllowedException();
        }

        // (3) Cat ve micro giay (do chinh xac cua TIMESTAMPTZ): gia tri Java dung de phan loai loi o (5) va gia tri bind vao
        // cau UPDATE phai la MOT - khong de JDBC lam tron nano giay khac voi phep so sanh Java (L14).
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        if (interviewInvitationRepository.confirmSlotIfAvailable(applicationId, slotId, now) == 0) { // (4)
            throw rejection(applicationId, slotId, now); // (5) 0 dong
        }

        // (5) 1 dong - persistence context da bi xoa (clearAutomatically) nen day la SELECT that, thay confirmed_* vua ghi.
        InterviewInvitation active = interviewInvitationRepository
                .findByApplicationIdAndSupersededAtIsNull(applicationId)
                .orElseThrow(() -> new IllegalStateException("Khong tim thay giay moi hieu luc vua chot: " + applicationId));
        CandidateInterviewInvitationResponse response = toCandidateResponse(active);

        // (6) Gia tri khung VUA CHOT lay tu ket qua doc lai o (5) - listener dung noi dung tu day, khong doc lai bang giay moi.
        CandidateInterviewInvitationResponse.Slot confirmed = response.slots().stream()
                .filter(CandidateInterviewInvitationResponse.Slot::confirmed)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Khong tim thay khung vua chot: " + applicationId));
        eventPublisher.publishEvent(new InterviewSlotConfirmedEvent(
                applicationId,
                application.getJobId(),
                application.getCandidateId(),
                confirmed.scheduledAt(),
                confirmed.location()));

        return response;
    }

    // FR-U10 R-W5 - cau UPDATE tra 0 dong: doc lai (SAU khoa, ket qua on dinh) va tra loi DAU TIEN khop theo thu tu bang R-W5.
    // Dong 3 KHONG phan biet "khung khong ton tai" / "khung cua giay moi cu" / "khung cua don khac" - cung mot ma, cung mot
    // cau, khong lo khung cua don nguoi khac. Dong 2 dung truoc dong 3 (Q3: chon lai dung khung da chot van la "da chot").
    private RuntimeException rejection(UUID applicationId, UUID slotId, Instant now) {
        InterviewInvitation active = interviewInvitationRepository
                .findByApplicationIdAndSupersededAtIsNull(applicationId)
                .orElse(null);
        if (active == null) {
            return new InterviewInvitationNotFoundException(applicationId); // dong 1
        }
        if (active.getConfirmedSlotId() != null) {
            return new InterviewSlotAlreadyConfirmedException(); // dong 2
        }
        InterviewInvitationSlot slot = slotRepository
                .findById(slotId)
                .filter(candidate -> candidate.getInvitationId().equals(active.getId()))
                .orElse(null);
        if (slot == null) {
            return new InterviewInvitationConflictException(); // dong 3
        }
        if (!slot.getScheduledAt().isAfter(now)) {
            return new InterviewSlotPassedException(); // dong 4 - cung moc now voi cau UPDATE
        }
        // dong 5 - khong the xay ra duoi khoa; phong thu, khong 500.
        return new InterviewInvitationConflictException();
    }

    // DTO I4 cua giay moi hieu luc - dung chung cho I4 (getActiveInvitationForCandidate) va C1 (confirmSlot), FR-U10 R-W7.
    private CandidateInterviewInvitationResponse toCandidateResponse(InterviewInvitation active) {
        List<CandidateInterviewInvitationResponse.Slot> slots =
                slotRepository.findByInvitationIdOrderByDisplayOrderAsc(active.getId()).stream()
                        .map(slot -> new CandidateInterviewInvitationResponse.Slot(
                                slot.getId(),
                                slot.getDisplayOrder(),
                                slot.getScheduledAt(),
                                slot.getLocation(),
                                slot.getId().equals(active.getConfirmedSlotId())))
                        .toList();
        return new CandidateInterviewInvitationResponse(
                active.getSubject(), active.getRenderedContent(), active.getSentAt(), active.getConfirmedAt(), slots);
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

    private static InterviewInvitationResponse toResponse(InterviewInvitation i, List<InterviewInvitationSlot> slots) {
        return new InterviewInvitationResponse(
                i.getId(),
                i.getApplicationId(),
                i.getSupersededAt() == null,
                i.getSubject(),
                i.getRenderedContent(),
                i.getSentAt(),
                i.getSentBy(),
                i.getSupersededAt(),
                i.getConfirmedSlotId(),
                i.getConfirmedAt(),
                slots.stream()
                        .map(slot -> new InterviewInvitationResponse.Slot(
                                slot.getId(), slot.getDisplayOrder(), slot.getScheduledAt(), slot.getLocation()))
                        .toList());
    }
}
