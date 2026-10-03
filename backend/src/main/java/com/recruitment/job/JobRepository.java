package com.recruitment.job;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, UUID> {

    // FR-U07 R-Q1/R-Q2: dieu kien "cung" (OPEN + chua xoa + con han, cong R-N category/location-code
    // ma NULL van hien, R-S luong chong lap + Thoa thuan + ngoai te, R-W workMode, R-T postedWithin)
    // dinh nghia MOT LAN o hang so nay - noi vao ca hai bien the ORDER BY (R-O4) va countQuery (R-Q2:
    // truy van dem phai dung CUNG dieu kien voi truy van lay du lieu). @Query doi gia tri la
    // ConstantExpression nen khong the goi phuong thuc Java de "dung chung" - noi chuoi hang so
    // static final nhu the nay la cach DUY NHAT de dinh nghia MOT LAN trong MA NGUON ma van thoa man
    // yeu cau cua annotation (van bi lap lai trong .class/annotation da bien dich, nhung khong the
    // lech nhau vi cung xuat phat tu mot hang so).
    //
    // keyword/category/location (R-F1) giu nguyen y het ILIKE truoc khi co U07 - chi KHONG dua vao
    // PublicJobSearchCriteria (R-Q1) de FR-U15 tai dung dung phan dieu kien cung ma khong mang theo
    // tim kiem van ban cua U07.
    //
    // categoryCode/locationCode (R-N1/R-N2): so bang HOAC ma NULL (tin "chua chuan hoa" khong bi loai
    // am tham). FR-U15 R-H2: mo rong tu so bang MOT gia tri (=) sang danh sach (IN), cung khuon
    // workMode (:categoryCodesPresent/:locationCodesPresent = FALSE bo qua IN-list khi danh sach
    // rong, Postgres khong cho "IN ()" voi danh sach rong) - OR category_code/location_code IS NULL
    // giu nguyen, khong doi y nghia R-N. Luong (R-S2/R-S4/R-S5): chuan hoa salary_currency ve VND
    // khi NULL/rong, so sanh khong phan biet hoa-thuong va khoang trang; nhom ngoai te (R-S5) khong
    // bi R-S2 so sanh, luon hien. hideUnlisted (R-S4/R-S6) la dieu kien DOC LAP, chi loai dung nhom
    // Thoa thuan. workMode (R-W1): :workModesPresent = FALSE bo qua IN-list khi danh sach rong.
    // postedWithin (R-T2): nguong :sinceTimestamp da tinh san o Java bang Clock, KHONG viet NOW() -
    // INTERVAL trong SQL.
    //
    // CAST(:param AS text) bat buoc o ca hai ve: Postgres khong tu suy duoc kieu tham so khi ve con
    // lai la NULL (ERROR: could not determine data type of parameter).
    //
    // FR-C05 R-J8: location/category khop nhan cua ma HOAC gia tri cu (ke ca Job da co ma - vi du
    // Job cu ghi "Binh Duong" nay mang ma HO_CHI_MINH van tim ra bang "Binh Duong"). EXISTS thay vi
    // JOIN de giu SELECT * chi co cot cua jobs (map thang vao entity Job). Loc theo MA
    // (categoryCode/locationCode) nam ngay trong hang so nay (R-N1/R-N2 o tren).
    String PUBLIC_JOB_FILTER_WHERE =
            """
            WHERE j.status = 'OPEN' AND j.deleted_at IS NULL
              AND (j.deadline IS NULL OR j.deadline >= CURRENT_DATE)
              AND (CAST(:titlePattern AS text) IS NULL OR j.title ILIKE CAST(:titlePattern AS text))
              AND (CAST(:locationPattern AS text) IS NULL
                   OR j.location ILIKE CAST(:locationPattern AS text)
                   OR EXISTS (SELECT 1 FROM catalog_provinces cp WHERE cp.code = j.location_code
                              AND cp.label ILIKE CAST(:locationPattern AS text)))
              AND (CAST(:categoryPattern AS text) IS NULL
                   OR j.category ILIKE CAST(:categoryPattern AS text)
                   OR EXISTS (SELECT 1 FROM catalog_industries ci WHERE ci.code = j.category_code
                              AND ci.label ILIKE CAST(:categoryPattern AS text)))
              AND (:categoryCodesPresent = FALSE OR j.category_code IN :categoryCodeParams
                   OR j.category_code IS NULL)
              AND (:locationCodesPresent = FALSE OR j.location_code IN :locationCodeParams
                   OR j.location_code IS NULL)
              AND (
                    (UPPER(TRIM(COALESCE(j.salary_currency, 'VND'))) = 'VND'
                      AND (CAST(:salaryMinVnd AS numeric) IS NULL OR j.salary_max IS NULL
                           OR j.salary_max >= CAST(:salaryMinVnd AS numeric))
                      AND (CAST(:salaryMaxVnd AS numeric) IS NULL OR j.salary_min IS NULL
                           OR j.salary_min <= CAST(:salaryMaxVnd AS numeric)))
                    OR UPPER(TRIM(COALESCE(j.salary_currency, 'VND'))) <> 'VND'
                  )
              AND NOT (:hideUnlisted = TRUE AND j.salary_min IS NULL AND j.salary_max IS NULL)
              AND (:workModesPresent = FALSE OR j.work_mode IN :workModeParams)
              AND (CAST(:sinceTimestamp AS timestamptz) IS NULL
                   OR COALESCE(j.published_at, j.created_at) >= CAST(:sinceTimestamp AS timestamptz))
            """;

    @Query(
            value =
                    "SELECT * FROM jobs j "
                            + PUBLIC_JOB_FILTER_WHERE
                            + " ORDER BY COALESCE(j.published_at, j.created_at) DESC, j.id DESC",
            countQuery = "SELECT count(*) FROM jobs j " + PUBLIC_JOB_FILTER_WHERE,
            nativeQuery = true)
    Page<Job> searchPublicJobsSortedByNewest(
            @Param("titlePattern") String titlePattern,
            @Param("locationPattern") String locationPattern,
            @Param("categoryPattern") String categoryPattern,
            @Param("categoryCodesPresent") boolean categoryCodesPresent,
            @Param("categoryCodeParams") List<String> categoryCodeParams,
            @Param("locationCodesPresent") boolean locationCodesPresent,
            @Param("locationCodeParams") List<String> locationCodeParams,
            @Param("salaryMinVnd") BigDecimal salaryMinVnd,
            @Param("salaryMaxVnd") BigDecimal salaryMaxVnd,
            @Param("hideUnlisted") boolean hideUnlisted,
            @Param("workModesPresent") boolean workModesPresent,
            @Param("workModeParams") List<String> workModeParams,
            @Param("sinceTimestamp") Instant sinceTimestamp,
            Pageable pageable);

    // R-O2: tin Thoa thuan (ca hai cot NULL) co COALESCE(salary_max, salary_min) IS NULL -> NULLS
    // LAST day xuong cuoi. Tin ngoai te (R-S5) KHONG loai khoi sort - van sap theo dung so luu trong
    // salary_max/salary_min du don vi tien te khac nhau (no ky thuat, muc 10 REQUIREMENT - khong quy
    // doi ty gia).
    @Query(
            value =
                    "SELECT * FROM jobs j "
                            + PUBLIC_JOB_FILTER_WHERE
                            + " ORDER BY COALESCE(j.salary_max, j.salary_min) DESC NULLS LAST,"
                            + " COALESCE(j.published_at, j.created_at) DESC, j.id DESC",
            countQuery = "SELECT count(*) FROM jobs j " + PUBLIC_JOB_FILTER_WHERE,
            nativeQuery = true)
    Page<Job> searchPublicJobsSortedBySalaryDesc(
            @Param("titlePattern") String titlePattern,
            @Param("locationPattern") String locationPattern,
            @Param("categoryPattern") String categoryPattern,
            @Param("categoryCodesPresent") boolean categoryCodesPresent,
            @Param("categoryCodeParams") List<String> categoryCodeParams,
            @Param("locationCodesPresent") boolean locationCodesPresent,
            @Param("locationCodeParams") List<String> locationCodeParams,
            @Param("salaryMinVnd") BigDecimal salaryMinVnd,
            @Param("salaryMaxVnd") BigDecimal salaryMaxVnd,
            @Param("hideUnlisted") boolean hideUnlisted,
            @Param("workModesPresent") boolean workModesPresent,
            @Param("workModeParams") List<String> workModeParams,
            @Param("sinceTimestamp") Instant sinceTimestamp,
            Pageable pageable);

    // Dung cho noi can "Job OPEN moi nhat, khong loc gi" (CvImprovementOrchestrator - van ban xu
    // huong thi truong cho goi y cai thien CV). Uy quyen sang searchPublicJobsSortedByNewest voi moi
    // dieu kien la "khong loc" - thay the dung cho ban searchPublicJobs(3 String, Pageable) cu truoc
    // FR-U07 (da xoa: ORDER BY created_at DESC thieu tie-break id, R-O3 phai sua, khong duoc giu lam
    // "ban thu hai" chi vi mot noi goi con dung).
    // FR-U15 R-H2 - sua co hoc theo chu ky moi cua searchPublicJobsSortedByNewest (categoryCode/
    // locationCode don gia tri -> cap Present+IN): categoryCodesPresent=false/locationCodesPresent=
    // false bo qua IN-list (danh sach sentinel khong bao gio khop, giong workModeParams), HANH VI
    // KHONG DOI - van tra ve moi job OPEN, khong loc gi.
    default Page<Job> searchOpenJobsNewest(Pageable pageable) {
        return searchPublicJobsSortedByNewest(
                null, null, null,
                false, List.of("__NONE__"),
                false, List.of("__NONE__"),
                null, null, false, false, List.of("__NONE__"), null, pageable);
    }

    @Query(
            value =
                    """
                    SELECT * FROM jobs j
                    WHERE j.id = :id AND j.status = 'OPEN' AND j.deleted_at IS NULL
                      AND (j.deadline IS NULL OR j.deadline >= CURRENT_DATE)
                    """,
            nativeQuery = true)
    Optional<Job> findOpenJobById(@Param("id") UUID id);

    // Sinh doi voi findOpenJobById nhung nhan danh sach id - dung cho
    // JobPublicService.getByIds (Dot 5): loc lai OPEN + deleted_at IS NULL + deadline chua qua TAI
    // THOI DIEM DOC, phong job_recommendations cache con giu mot job vua dong/het han/bi xoa giua
    // hai lot lam moi cache (Plan Mode F1 muc F). Khong dam bao thu tu ket qua theo dung thu tu
    // ids dau vao - caller (JobPublicService.getByIds) tu sap lai qua Map.
    @Query(
            value =
                    """
                    SELECT * FROM jobs j
                    WHERE j.id IN :ids AND j.status = 'OPEN' AND j.deleted_at IS NULL
                      AND (j.deadline IS NULL OR j.deadline >= CURRENT_DATE)
                    """,
            nativeQuery = true)
    List<Job> findOpenJobsByIdIn(@Param("ids") List<UUID> ids);

    Page<Job> findByCompanyIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID companyId, Pageable pageable);

    Page<Job> findByCompanyIdAndDeletedAtIsNullAndStatusOrderByCreatedAtDesc(
            UUID companyId, JobStatus status, Pageable pageable);

    // Dashboard F3 (FR-H08) - hieu suat tung chien dich (job) cua cong ty. LEFT JOIN job_applications
    // de job CHUA co don nao van xuat hien (totalApplications=0). LATERAL latest_done lay dung
    // luot DONE MOI NHAT cua moi don - cung ngu nghia voi
    // ScoringRunRepository.findLatestDoneByApplicationIdIn (D3/D4), khong phai luot moi nhat bat
    // ke trang thai. LATERAL ORDER BY co them sr.id DESC lam khoa cuoi (khac ban goc D3 - xem bao
    // cao duyet Dot 3): hai luot DONE cua CUNG mot don co the trung created_at (cung transaction),
    // thieu khoa cuoi thi LIMIT 1 chon hang nao trong nhom hoa la KHONG xac dinh. LATERAL
    // ever_invited/ever_hired dem theo "DA TUNG dat trang thai" qua
    // application_status_history - CUNG nguyen tac voi
    // JobApplicationRepository.countFunnelForCompany, ap dung nhat quan cho ca hai cot trong bang
    // nay (Dot 2, quyet dinh #6 trong plan). GROUP BY liet ke ca j.created_at vi no chi dung o
    // ORDER BY, khong phai SELECT list.
    //
    // COUNT(ja.id)/COUNT(latest_done.id) (khong phai COUNT(*)) va COUNT(*) FILTER(...) (khong
    // phai COUNT(*) tran) - ca hai deu can thiet de job 0 don tra ve 0 chu khong phai 1 (da kiem
    // thuc nghiem tren Postgres 17 that, xem bao cao duyet Dot 2).
    //
    // ORDER BY j.created_at DESC, j.id ASC: created_at la transaction-scoped (now()), nhieu job
    // tao trong CUNG mot transaction (seed, test) se trung created_at tuyet doi - j.id lam khoa
    // cuoi DUY NHAT de thu tu bang on dinh qua nhieu lan goi (quy uoc chung cho moi ORDER BY trong
    // F3, xem CLAUDE.md muc 3c ve now() transaction-scoped).
    @Query(
            value =
                    """
                    SELECT j.id AS jobId, j.title AS title, j.status AS status,
                           j.recruitment_cycle AS recruitmentCycle,
                           COUNT(ja.id) AS totalApplications,
                           COUNT(latest_done.id) AS scoredApplications,
                           ROUND(AVG(latest_done.total_score), 3) AS averageScore,
                           COUNT(*) FILTER (WHERE ever_invited.hit IS NOT NULL) AS everInvitedCount,
                           COUNT(*) FILTER (WHERE ever_hired.hit IS NOT NULL) AS everHiredCount
                    FROM jobs j
                    LEFT JOIN job_applications ja ON ja.job_id = j.id
                    LEFT JOIN LATERAL (
                        SELECT sr.id, sr.total_score FROM scoring_runs sr
                        WHERE sr.application_id = ja.id AND sr.status = 'DONE'
                        ORDER BY sr.created_at DESC, sr.id DESC LIMIT 1
                    ) latest_done ON true
                    LEFT JOIN LATERAL (
                        SELECT 1 AS hit FROM application_status_history ash
                        WHERE ash.application_id = ja.id AND ash.to_status = 'INTERVIEW_INVITED' LIMIT 1
                    ) ever_invited ON true
                    LEFT JOIN LATERAL (
                        SELECT 1 AS hit FROM application_status_history ash
                        WHERE ash.application_id = ja.id AND ash.to_status = 'HIRED' LIMIT 1
                    ) ever_hired ON true
                    WHERE j.company_id = :companyId AND j.deleted_at IS NULL
                    GROUP BY j.id, j.title, j.status, j.recruitment_cycle, j.created_at
                    ORDER BY j.created_at DESC, j.id ASC
                    """,
            nativeQuery = true)
    List<JobPerformanceView> findJobPerformanceForCompany(@Param("companyId") UUID companyId);
}
