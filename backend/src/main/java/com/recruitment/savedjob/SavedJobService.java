package com.recruitment.savedjob;

import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.common.exception.JobNotSaveableException;
import com.recruitment.common.exception.SavedJobLimitReachedException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobAvailability;
import com.recruitment.job.JobAvailabilityResolver;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobSummaryMapper;
import com.recruitment.job.dto.JobSummaryResponse;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.savedjob.dto.SavedJobResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FR-U16 - luu / bo luu / danh sach tin da luu cua CHINH ung vien (candidateId lay tu JWT o controller). Khong AI, khong
// xep hang, khong diem phu hop (R-G1, R-G2): danh sach chi sap saved_at DESC, id DESC (cau Q7).
@Service
public class SavedJobService {

    // R-L1 - tran tin dang luu, khai MOT cho.
    public static final int MAX_ACTIVE_SAVED_JOBS = 100;

    private final SavedJobRepository savedJobRepository;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final JobAvailabilityResolver jobAvailabilityResolver;
    private final CatalogRegistry catalogRegistry;

    public SavedJobService(
            SavedJobRepository savedJobRepository,
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            JobAvailabilityResolver jobAvailabilityResolver,
            CatalogRegistry catalogRegistry) {
        this.savedJobRepository = savedJobRepository;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.jobAvailabilityResolver = jobAvailabilityResolver;
        this.catalogRegistry = catalogRegistry;
    }

    // E1 - thu tu kiem REQUIREMENT muc 4.2 / R-L3: (1) tin mo (R-S2, ke ca khi dang luu - B7) -> (2) khoa hang users ->
    // (3) dong hien co -> (4) tran -> (5) ghi. Buoc 3-5 chay sau khoa nen hai request cua cung ung vien noi tiep nhau.
    @Transactional
    public void save(UUID candidateId, UUID jobId) {
        if (jobRepository.findOpenJobById(jobId).isEmpty()) {
            throw new JobNotSaveableException();
        }
        savedJobRepository
                .lockCandidateForSave(candidateId)
                .orElseThrow(() -> new IllegalStateException("Khong tim thay nguoi dung de khoa: " + candidateId));

        Optional<SavedJob> existing = savedJobRepository.findByCandidateIdAndJobId(candidateId, jobId);
        if (existing.isPresent() && existing.get().getDeletedAt() == null) {
            // R-S4 - da dang luu: idempotent, khong ghi gi, saved_at giu nguyen, khong xet tran.
            return;
        }
        if (savedJobRepository.countActiveByCandidateId(candidateId) >= MAX_ACTIVE_SAVED_JOBS) {
            throw new SavedJobLimitReachedException(MAX_ACTIVE_SAVED_JOBS);
        }
        if (existing.isPresent()) {
            // R-S6 - dung lai dong da bo luu. Duoi khoa luon ra 1 dong; khac 1 la loi khoa, khong che di.
            if (savedJobRepository.reactivate(candidateId, jobId) != 1) {
                throw new IllegalStateException("Luu lai khong cap nhat dung mot dong: " + candidateId + "/" + jobId);
            }
            return;
        }
        SavedJob row = new SavedJob();
        row.setCandidateId(candidateId);
        row.setJobId(jobId);
        // saveAndFlush: INSERT chay ngay trong request (CLAUDE.md muc 4). Vi pham uq_saved_job_per_candidate khong duoc
        // xay ra duoi khoa - neu co thi de roi ve 500, khong dich sang 204/409 (R-L4).
        savedJobRepository.saveAndFlush(row);
    }

    // E2 - R-S5: dat deleted_at cho dong dang luu; khong co dong nao cung la thanh cong (idempotent).
    @Transactional
    public void unsave(UUID candidateId, UUID jobId) {
        savedJobRepository.softDelete(candidateId, jobId);
    }

    // E3 - so cau SQL co dinh bat ke so tin (khong N+1): danh sach, tin, cong ty, tin mo theo lo.
    @Transactional(readOnly = true)
    public List<SavedJobResponse> listMine(UUID candidateId) {
        List<SavedJobListRow> rows = savedJobRepository.findActiveList(candidateId);
        if (rows.isEmpty()) {
            return List.of();
        }
        // findAllById khong loc deleted_at/status (Job khong co @SQLRestriction): tin da go van hien (R-V6).
        List<UUID> jobIds = rows.stream().map(SavedJobListRow::getJobId).toList();
        Map<UUID, Job> jobsById = jobRepository.findAllById(jobIds).stream()
                .collect(Collectors.toMap(Job::getId, Function.identity()));
        List<UUID> companyIds = jobsById.values().stream().map(Job::getCompanyId).distinct().toList();
        Map<UUID, Company> companiesById = companyRepository.findByIdIn(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, Function.identity()));
        Map<UUID, JobAvailability> availabilityById = jobAvailabilityResolver.of(jobsById.values());

        return rows.stream()
                .map(row -> toResponse(row, jobsById, companiesById, availabilityById))
                .toList();
    }

    // E4 - id cac tin dang luu, cung thu tu E3.
    @Transactional(readOnly = true)
    public List<UUID> listMyJobIds(UUID candidateId) {
        return savedJobRepository.findActiveJobIds(candidateId);
    }

    private SavedJobResponse toResponse(
            SavedJobListRow row,
            Map<UUID, Job> jobsById,
            Map<UUID, Company> companiesById,
            Map<UUID, JobAvailability> availabilityById) {
        // job_id la FK toi jobs (V20), companies khong co xoa - thieu la loi du lieu, khong phai truong hop nghiep vu.
        Job job = jobsById.get(row.getJobId());
        if (job == null) {
            throw new IllegalStateException("Khong tim thay tin cua dong luu: " + row.getSavedJobId());
        }
        Company company = companiesById.get(job.getCompanyId());
        JobAvailability availability = availabilityById.get(job.getId());
        // R-V6 - tin UNAVAILABLE (xoa mem / ve nhap): chi ten tin + cong ty, khong tom tat.
        JobSummaryResponse summary = availability == JobAvailability.UNAVAILABLE
                ? null
                : JobSummaryMapper.toSummary(job, company, catalogRegistry);
        SavedJobResponse.CurrentApplication application = row.getApplicationId() == null
                ? null
                : new SavedJobResponse.CurrentApplication(
                        row.getApplicationId(), ApplicationStatus.valueOf(row.getApplicationStatus()));
        return new SavedJobResponse(
                job.getId(),
                job.getTitle(),
                JobSummaryMapper.toCompanyRef(company),
                row.getSavedAt(),
                availability,
                summary,
                application);
    }
}
