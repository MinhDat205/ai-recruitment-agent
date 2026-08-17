package com.recruitment.jobapplication;

import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.dto.ApplicationHrListItemResponse;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.scoring.CriterionScore;
import com.recruitment.scoring.CriterionScoreRepository;
import com.recruitment.scoring.LatestDoneScoringRunView;
import com.recruitment.scoring.LatestScoringRunView;
import com.recruitment.scoring.RubricSnapshot;
import com.recruitment.scoring.ScoringRun;
import com.recruitment.scoring.ScoringRunRepository;
import com.recruitment.scoring.ScoringRunStatus;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

// KHONG @Transactional: chi DOC, nhieu repository doc lap khong can atomic voi nhau - cung tinh
// than voi ScoringRunService. Doc cheo sang ScoringRunRepository/CriterionScoreRepository (package
// scoring) de lay tien do + ket qua xep hang, giong cach JobOwnerService da doc cheo sang
// RubricRepository o B2.
@Service
public class ApplicationOwnerService {

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final ScoringRunRepository scoringRunRepository;
    private final CriterionScoreRepository criterionScoreRepository;

    public ApplicationOwnerService(
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            JobApplicationRepository jobApplicationRepository,
            ResumeRepository resumeRepository,
            UserRepository userRepository,
            ScoringRunRepository scoringRunRepository,
            CriterionScoreRepository criterionScoreRepository) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.scoringRunRepository = scoringRunRepository;
        this.criterionScoreRepository = criterionScoreRepository;
    }

    public List<ApplicationHrListItemResponse> listApplications(UUID ownerId, UUID jobId, ApplicationSortOption sort) {
        Job job = loadOwnedJob(jobId, ownerId);
        List<JobApplication> applications = jobApplicationRepository.findByJobIdOrderByAppliedAtDesc(job.getId());
        if (applications.isEmpty()) {
            return List.of();
        }
        List<UUID> applicationIds = applications.stream().map(JobApplication::getId).toList();

        // Nam lan tra cuu HANG LOAT (khong phai tung don mot trong vong lap) - tranh N+1. Dung
        // dung 8 query bat ke danh sach dai bao nhieu (2 cho ownership da chay o loadOwnedJob, 6 o
        // day): findByJobId, findAllById (resume), findAllById (user), findLatestByApplicationIdIn
        // (tien do), findLatestDoneByApplicationIdIn (nguon diem xep hang, Q5), findAllById (full
        // ScoringRun cua CAC lot DONE do - chi de doc rubric_snapshot lam thu tu tieu chi, Diem 3),
        // findByScoringRunIdIn (diem tung tieu chi cua CAC lot DONE do).
        Map<UUID, ParseStatus> parseStatusByResumeId = resumeRepository
                .findAllById(applications.stream().map(JobApplication::getResumeId).toList())
                .stream()
                .collect(Collectors.toMap(Resume::getId, Resume::getParseStatus));

        Map<UUID, String> candidateNameById = userRepository
                .findAllById(applications.stream().map(JobApplication::getCandidateId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        Map<UUID, LatestScoringRunView> latestRunByApplicationId = scoringRunRepository
                .findLatestByApplicationIdIn(applicationIds)
                .stream()
                .collect(Collectors.toMap(LatestScoringRunView::getApplicationId, v -> v));

        // Nguon diem xep hang (Q5, ke hoach D3): lot DONE MOI NHAT cua moi don - KHAC voi
        // latestRunByApplicationId o tren (lot moi nhat BAT KE trang thai, chi phuc vu hien thi
        // tien do). Mot don co lot DONE cu hon nhung lot FAILED/dang cham moi hon van lay diem tu
        // lot DONE, khong phai null.
        Map<UUID, LatestDoneScoringRunView> latestDoneRunByApplicationId = scoringRunRepository
                .findLatestDoneByApplicationIdIn(applicationIds)
                .stream()
                .collect(Collectors.toMap(LatestDoneScoringRunView::getApplicationId, v -> v));

        List<UUID> doneRunIds =
                latestDoneRunByApplicationId.values().stream().map(LatestDoneScoringRunView::getId).toList();

        // Can toan bo entity ScoringRun (khong chi projection) CHI de doc rubric_snapshot, dung
        // lam thu tu hien thi tieu chi (Diem 3, xem buildCriterionScores) - khong doc lai
        // rubric_criteria song. Guard rong: findAllById/IN () tren danh sach rong la loi hoac query
        // thua, mau CriterionScoreRepository.countByScoringRunIdIn.
        Map<UUID, ScoringRun> doneRunById = doneRunIds.isEmpty()
                ? Map.of()
                : scoringRunRepository.findAllById(doneRunIds).stream()
                        .collect(Collectors.toMap(ScoringRun::getId, r -> r));

        Map<UUID, List<CriterionScore>> criterionScoresByRunId = doneRunIds.isEmpty()
                ? Map.of()
                : criterionScoreRepository.findByScoringRunIdIn(doneRunIds).stream()
                        .collect(Collectors.groupingBy(CriterionScore::getScoringRunId));

        List<RankableRow> rows = applications.stream()
                .map(app -> toRankableRow(
                        app,
                        parseStatusByResumeId,
                        candidateNameById,
                        latestRunByApplicationId,
                        latestDoneRunByApplicationId,
                        doneRunById,
                        criterionScoresByRunId))
                .toList();

        Map<UUID, Integer> rankByApplicationId = assignRanks(rows);
        List<RankableRow> ordered = sort == ApplicationSortOption.APPLIED_AT_DESC ? rows : sortByRank(rows);

        return ordered.stream()
                .map(row -> row.toResponse(rankByApplicationId.get(row.applicationId())))
                .toList();
    }

    private RankableRow toRankableRow(
            JobApplication application,
            Map<UUID, ParseStatus> parseStatusByResumeId,
            Map<UUID, String> candidateNameById,
            Map<UUID, LatestScoringRunView> latestRunByApplicationId,
            Map<UUID, LatestDoneScoringRunView> latestDoneRunByApplicationId,
            Map<UUID, ScoringRun> doneRunById,
            Map<UUID, List<CriterionScore>> criterionScoresByRunId) {
        LatestScoringRunView latestRun = latestRunByApplicationId.get(application.getId());
        LatestDoneScoringRunView latestDoneRun = latestDoneRunByApplicationId.get(application.getId());

        BigDecimal totalScore = latestDoneRun == null ? null : latestDoneRun.getTotalScore();
        List<ApplicationHrListItemResponse.CriterionScoreItem> criterionScores = latestDoneRun == null
                ? List.of()
                : buildCriterionScores(
                        doneRunById.get(latestDoneRun.getId()),
                        criterionScoresByRunId.getOrDefault(latestDoneRun.getId(), List.of()));

        return new RankableRow(
                application.getId(),
                candidateNameById.get(application.getCandidateId()),
                parseStatusByResumeId.get(application.getResumeId()),
                application.getAppliedAt(),
                latestRun == null ? null : latestRun.getId(),
                latestRun == null ? null : ScoringRunStatus.valueOf(latestRun.getStatus()),
                latestRun == null ? null : latestRun.getFinishedAt(),
                totalScore,
                criterionScores);
    }

    // Thu tu tieu chi = DUNG thu tu da chup trong CHINH rubric_snapshot cua lot DONE nay (Diem 3,
    // Dot 4) - KHONG doc lai rubric_criteria song (co the da doi display_order hoac bi xoa sau khi
    // cham), va on dinh vinh vien voi CHINH lot do vi la du lieu snapshot bat bien, khop tinh than
    // "doc snapshot, khong doc rubric song" da ap dung xuyen suot D2/D3. filter(nonNull): luoi an
    // toan phong khi mot ten trong snapshot khong co dong criterion_scores tuong ung (ve ly thuyet
    // khong xay ra voi mot lot DONE - AggregationIntegrityChecker da kiem o D3 - nhung tang hien
    // thi khong nen nem loi vi mot du lieu thieu, chi bo qua dong do).
    private List<ApplicationHrListItemResponse.CriterionScoreItem> buildCriterionScores(
            ScoringRun doneRun, List<CriterionScore> scores) {
        if (doneRun == null) {
            return List.of();
        }
        Map<String, CriterionScore> scoreByName =
                scores.stream().collect(Collectors.toMap(CriterionScore::getCriterionNameSnapshot, s -> s));
        return doneRun.getRubricSnapshot().criteria().stream()
                .map(RubricSnapshot.CriterionSnapshot::name)
                .map(scoreByName::get)
                .filter(Objects::nonNull)
                .map(s -> new ApplicationHrListItemResponse.CriterionScoreItem(
                        s.getCriterionNameSnapshot(), s.getScore(), s.getMaxScoreSnapshot(), s.getWeightSnapshot(), s.getReasoning()))
                .toList();
    }

    // Xep hang kieu 1-2-2-4 (Q5, ke hoach D3): hoa diem -> CUNG hang (khong xep tuan tu 1-2-3-4),
    // hang ke tiep sau mot cum hoa NHAY dung bang vi tri thuc te (vd hai don hang 2 -> don thu tu
    // nhan hang 4, khong phai 3). Tinh o Java tren danh sach da sap theo sortByRank (khong dung SQL
    // window function RANK() - xem danh doi da chot o Q5). Don khong co totalScore (chua cham/toan
    // FAILED/dang cham dang) khong duoc dua vao map -> get() tra ve null, DTO hien thi rank=null.
    private Map<UUID, Integer> assignRanks(List<RankableRow> rows) {
        List<RankableRow> sorted = sortByRank(rows);
        Map<UUID, Integer> rankByApplicationId = new LinkedHashMap<>();
        int position = 0;
        int rank = 0;
        BigDecimal previousScore = null;
        for (RankableRow row : sorted) {
            position++;
            if (row.totalScore() == null) {
                continue;
            }
            if (previousScore == null || row.totalScore().compareTo(previousScore) != 0) {
                rank = position;
            }
            rankByApplicationId.put(row.applicationId(), rank);
            previousScore = row.totalScore();
        }
        return rankByApplicationId;
    }

    // totalScore giam dan, NULL xep CUOI (Comparator.nullsLast). Khoa phu appliedAt TANG dan giu
    // thu tu dong ON DINH qua cac lan tai trang khi hoa diem (ke ca hoa o "ca hai deu null") - ap
    // dung DUNG MOT lan cho ca hai muc dich: vua la thu tu tinh RANK (luon dung cach nay bat ke
    // tham so sort cua request), vua la thu tu HIEN THI khi sort=TOTAL_SCORE_DESC (mac dinh).
    private List<RankableRow> sortByRank(List<RankableRow> rows) {
        return rows.stream()
                .sorted(Comparator.comparing(RankableRow::totalScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RankableRow::appliedAt))
                .toList();
    }

    // Mau ownership giong het JobOwnerService.loadOwned/RubricOwnerService.loadOwnedRubric.
    private Job loadOwnedJob(UUID jobId, UUID ownerId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        Company company = requireOwnCompany(ownerId);
        if (!job.getCompanyId().equals(company.getId())) {
            throw new AccessDeniedException("Khong co quyen truy cap danh sach ung vien cua tin tuyen dung nay");
        }
        return job;
    }

    private Company requireOwnCompany(UUID ownerId) {
        return companyRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new CompanyNotFoundException("HR chưa tạo hồ sơ công ty"));
    }

    // Du lieu trung gian truoc khi biet rank (rank phu thuoc TOAN BO danh sach nen phai tinh sau
    // khi da co moi dong) - giu totalScore/appliedAt rieng de sapByRank dung truc tiep, khong phai
    // doc lai tu ApplicationHrListItemResponse (record khong co accessor long nhau tien loi bang).
    private record RankableRow(
            UUID applicationId,
            String candidateName,
            ParseStatus resumeParseStatus,
            Instant appliedAt,
            UUID latestScoringRunId,
            ScoringRunStatus latestScoringRunStatus,
            Instant latestScoringRunFinishedAt,
            BigDecimal totalScore,
            List<ApplicationHrListItemResponse.CriterionScoreItem> criterionScores) {

        ApplicationHrListItemResponse toResponse(Integer rank) {
            return new ApplicationHrListItemResponse(
                    applicationId,
                    candidateName,
                    resumeParseStatus,
                    appliedAt,
                    latestScoringRunId,
                    latestScoringRunStatus,
                    latestScoringRunFinishedAt,
                    totalScore,
                    rank,
                    criterionScores);
        }
    }
}
