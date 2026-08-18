package com.recruitment.resume;

import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.common.exception.ResumeNotFoundException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
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

    public ResumeHrService(
            JobApplicationRepository jobApplicationRepository,
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            ResumeRepository resumeRepository,
            StorageService storageService) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.resumeRepository = resumeRepository;
        this.storageService = storageService;
    }

    public ResumeDownload downloadForApplication(UUID ownerId, UUID applicationId) {
        JobApplication application = loadOwnedApplication(applicationId, ownerId);

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
