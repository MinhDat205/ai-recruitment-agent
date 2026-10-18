package com.recruitment.resume;

import com.recruitment.blindreview.BlindReviewResolver;
import com.recruitment.blindreview.IdentityMaskSource;
import com.recruitment.blindreview.IdentityMasker;
import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.IdentityMaskedException;
import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.common.exception.ResumeNotFoundException;
import com.recruitment.common.exception.ResumeParsedDataNotFoundException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.resume.dto.ResumeParsedDataResponse;
import com.recruitment.storage.StorageService;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

// Tai CV cho HR (FR-H06, Dot 5b) - KHAC ResumeService (chi phuc vu candidate, kiem quyen theo
// candidateId). O day quyen so huu kiem theo chuoi applicationId -> job -> company, KHONG nhan
// resumeId truc tiep tu client (controller chi nhan applicationId) - resumeId luon tu suy ra o
// server tu JobApplication.resumeId, tranh HR do id resume tuy y.
//
// Ly do nam vao FR-H06 chu khong phai FR-H07 (da thong nhat voi nguoi dung): khong co CV goc thi
// khong ai doi chieu duoc evidence trong bao cao AI (score_explanations, criterion_scores.evidence)
// voi van ban that trong CV - kha nang kiem chung do CHINH la nguyen tac Explainable AI cua FR-H06
// (CLAUDE.md muc 2: "Moi giai thich phai kiem chung duoc tu noi dung CV"). Xem walkthrough Dot 6.
@Service
public class ResumeHrService {

    private final JobApplicationRepository jobApplicationRepository;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final ResumeRepository resumeRepository;
    private final StorageService storageService;
    private final ResumeParsedDataRepository resumeParsedDataRepository;
    private final ResumeParsedDataResponseMapper resumeParsedDataResponseMapper;
    private final BlindReviewResolver blindReviewResolver;
    private final IdentityMaskSource identityMaskSource;

    public ResumeHrService(
            JobApplicationRepository jobApplicationRepository,
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            ResumeRepository resumeRepository,
            StorageService storageService,
            ResumeParsedDataRepository resumeParsedDataRepository,
            ResumeParsedDataResponseMapper resumeParsedDataResponseMapper,
            BlindReviewResolver blindReviewResolver,
            IdentityMaskSource identityMaskSource) {
        this.blindReviewResolver = blindReviewResolver;
        this.identityMaskSource = identityMaskSource;
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.resumeRepository = resumeRepository;
        this.storageService = storageService;
        this.resumeParsedDataRepository = resumeParsedDataRepository;
        this.resumeParsedDataResponseMapper = resumeParsedDataResponseMapper;
    }

    public ResumeDownload downloadForApplication(UUID ownerId, UUID applicationId) {
        JobApplication application = loadOwnedApplication(applicationId, ownerId);
        // FR-H16 R-V1 - ghi de FR-H09 R-V1: don dang an danh -> 409, KHONG doc tep (kiem sau quyen so huu).
        if (blindReviewResolver.isMasked(applicationId)) {
            throw new IdentityMaskedException();
        }

        // resumeId luon ton tai tren JobApplication (cot NOT NULL, khong bao gio null) nhung ban
        // than dong resumes co the da bi mat (ve ly thuyet - resumes KHONG co deleted_at, khong co
        // tinh nang xoa mem/xoa cung nao cho resume trong codebase hien tai, nen day la luoi an
        // toan phong thu chu khong phai duong xay ra that trong du an). Dung lai chinh xac
        // ResumeNotFoundException - cung ma loi RESUME_NOT_FOUND voi ResumeService.downloadMine,
        // khong tao ma loi rieng cho cung mot y nghia "khong doc duoc file".
        Resume resume = resumeRepository
                .findById(application.getResumeId())
                .orElseThrow(() -> new ResumeNotFoundException(application.getResumeId()));
        Resource resource =
                storageService.load(resume.getFileUrl()).orElseThrow(() -> new ResumeNotFoundException(resume.getId()));

        MediaType contentType =
                resume.getFileType() == ResumeFileType.PDF
                        ? MediaType.APPLICATION_PDF
                        : MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        // fileName = TEN GOC ung vien da upload (Resume.fileName, xem ResumeService.originalFileName),
        // KHONG phai ten da chuan hoa noi bo (fileId + duoi, dung lam key luu tru). HR can nhan ra
        // ung vien tu ten file, khong can biet key luu tru noi bo. Dau tieng Viet/ky tu la trong ten
        // nay duoc RFC 5987/6266 hoa dung dan o tang controller (ContentDisposition.filename(...,
        // UTF_8)) - cung co che voi ResumeCandidateController.download, khong can xu ly gi them o day.
        return new ResumeDownload(resource, resume.getFileName(), contentType);
    }

    // FR-H09 E2 - CV da trich xuat cua DUNG CV ung vien da nop vao don nay (job_applications.resume_id,
    // khong phai CV chinh hien tai - R-D2). Kiem quyen bang chinh loadOwnedApplication ben duoi
    // (R-Q3: 404 don khong ton tai, 403 don cua cong ty khac). Chua co dong resume_parsed_data (CV
    // PENDING/PROCESSING/FAILED) -> 404 RESUME_PARSED_DATA_NOT_FOUND, giong het phia ung vien.
    // Response map qua ResumeParsedDataResponseMapper dung chung - khong them field, khong tra
    // raw_text (R-D2).
    public ResumeParsedDataResponse getParsedDataForApplication(UUID ownerId, UUID applicationId) {
        JobApplication application = loadOwnedApplication(applicationId, ownerId);
        ResumeParsedData data = resumeParsedDataRepository
                .findByResumeId(application.getResumeId())
                .orElseThrow(() -> new ResumeParsedDataNotFoundException(application.getResumeId()));
        ResumeParsedDataResponse response = resumeParsedDataResponseMapper.toResponse(data);
        if (!blindReviewResolver.isMasked(applicationId)) {
            return response;
        }
        return masked(response, identityMaskSource.load(applicationId));
    }

    // FR-H16 R-V4, R-M4 - BAN SAO da che cua E2 khi don dang an danh; KHONG sua ResumeParsedDataResponseMapper (dung chung
    // voi phia ung vien, R-U1) va khong ghi gi xuong DB (R-A3). contact = null; moi chuoi cua education/experience/skills/
    // certifications/projects/currentTitle/locationText (trong data) va hai ban sao currentTitle/locationText ngoai data
    // qua bo che. industryCode (ma danh muc), resumeId, parsedAt, schemaVersion, industry/location (ma + nhan C05),
    // experience (so thang/nam) giu nguyen.
    private static ResumeParsedDataResponse masked(ResumeParsedDataResponse response, IdentityMasker m) {
        ResumeParsedPayload p = response.data();
        ResumeParsedPayload maskedPayload = p == null
                ? null
                : new ResumeParsedPayload(
                        null,
                        p.education().stream()
                                .map(e -> new ResumeParsedPayload.Education(
                                        m.mask(e.school()), m.mask(e.degree()), m.mask(e.major()),
                                        m.mask(e.startDate()), m.mask(e.endDate()), e.gpa()))
                                .toList(),
                        p.experience().stream()
                                .map(e -> new ResumeParsedPayload.Experience(
                                        m.mask(e.company()), m.mask(e.title()), m.mask(e.startDate()),
                                        m.mask(e.endDate()), m.mask(e.description())))
                                .toList(),
                        p.skills().stream().map(m::mask).toList(),
                        p.certifications().stream()
                                .map(c -> new ResumeParsedPayload.Certification(
                                        m.mask(c.name()), m.mask(c.issuer()), m.mask(c.issueDate())))
                                .toList(),
                        p.projects().stream()
                                .map(pr -> new ResumeParsedPayload.Project(
                                        m.mask(pr.name()),
                                        m.mask(pr.description()),
                                        pr.technologies().stream().map(m::mask).toList()))
                                .toList(),
                        m.mask(p.currentTitle()),
                        p.industryCode(),
                        m.mask(p.locationText()));
        return new ResumeParsedDataResponse(
                response.resumeId(),
                maskedPayload,
                response.parsedAt(),
                response.schemaVersion(),
                m.mask(response.currentTitle()),
                response.industry(),
                response.location(),
                m.mask(response.locationText()),
                response.experience());
    }

    // Mau y HET ScoringRunService.loadOwnedApplication (khong trich xuat dung chung - dung tien le
    // lap lai method nho nay da co san hai lan trong du an, ScoringRunService/ApplicationOwnerService.
    // loadOwnedJob): 404 khi don khong ton tai, 403 (AccessDeniedException) khi don ton tai nhung
    // job cua no khong thuoc cong ty cua HR dang dang nhap.
    //
    // CO Y khac voi ResumeService.downloadMine (endpoint phia ung vien, luon tra 404 cho CA HAI
    // truong hop bang MOT query gop findByIdAndCandidateId) - hai endpoint kiem quyen so huu tren
    // HAI loai tai nguyen khac nhau, o hai ranh gioi tin cay khac nhan:
    //   - ResumeService.downloadMine: resumeId gan voi candidateId, hai NGUOI XA LA voi nhau (ung
    //     vien A do id resume cua ung vien B ma khong co quan he gi) - an ton toi da la 404 cho ca
    //     hai, khong tiet lo resume do co ton tai hay khong.
    //   - Method nay: applicationId gan voi companyId, hai TAI KHOAN DOANH NGHIEP cung van hanh
    //     trong CUNG mot quy trinh tuyen dung B2B - applicationId KHONG phai bi mat can giau giua
    //     cac HR, va day CHINH LA tai nguyen ma ScoringRunService/ApplicationOwnerService da tra 403
    //     cho truong hop nay tu D2/D3. Neu method nay tra 404 thay vi 403 se tao HAI quy uoc khac
    //     nhau cho CUNG MOT applicationId tren CUNG mot nhom route /api/hr/applications/{id}/**,
    //     gay kho hieu hon la bao ve them duoc gi (applicationId da la 403 o hai endpoint chi em roi).
    private JobApplication loadOwnedApplication(UUID applicationId, UUID ownerId) {
        JobApplication application = jobApplicationRepository
                .findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        Job job = jobRepository
                .findById(application.getJobId())
                .orElseThrow(() -> new JobNotFoundException(application.getJobId()));
        Company company = requireOwnCompany(ownerId);
        if (!job.getCompanyId().equals(company.getId())) {
            throw new AccessDeniedException("Khong co quyen tai CV cua don ung tuyen nay");
        }
        return application;
    }

    private Company requireOwnCompany(UUID ownerId) {
        return companyRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new CompanyNotFoundException("HR chưa tạo hồ sơ công ty"));
    }
}
