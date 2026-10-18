package com.recruitment.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.ai.embedding.EmbeddingTextFormat;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// FR-U13 T4 - JobRepository.findRankedForSearch (R-Q1) + JobEmbeddingRepository.findJobIdsWithEmbeddingIn (R-K4, L8).
//
// KHONG @Transactional cap lop (CLAUDE.md 3c): moi saveAndFlush tu commit; thu tu thoi gian dang duoc backdate TUONG
// MINH qua published_at, khong dua vao now() cua transaction.
//
// Cach ly voi job OPEN ro ri tu lop test khac (Testcontainers dung chung): moi test gan mot nhan rieng vao title va
// truyen titlePattern = "%nhan%" - dieu kien titlePattern nam san trong PUBLIC_JOB_FILTER_WHERE, khong doi SQL dang
// kiem. Nho vay khang dinh duoc DUNG tap/DUNG thu tu (containsExactly) ma khong phai soft-delete job cua lop khac.
// @AfterEach soft-delete job cua chinh test (khong de lai job OPEN co embedding cho lop khac).
//
// Vector nam tren truc 2-3 (khong dung truc 0-1 nhu JobEmbeddingRepositoryTest/U15): job o day co do tuong dong 0 voi
// vector truy van truc 0 cua cac lop do, du co sot lai cung khong lot nguong cua ho.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class JobRankedForSearchTest {

    private static final String NONE = "__NONE__";
    private static final String MODEL = "text-embedding-3-small";
    private static final String QUERY_VECTOR = EmbeddingTextFormat.toVectorText(vectorWithSimilarity(1.0));
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    // Thu tu uuid cua Postgres la so sanh byte khong dau = so sanh chuoi hex chuan; UUID.compareTo cua Java so sanh
    // long CO dau nen co the lech - dung chuoi.
    private static final Comparator<UUID> PG_UUID_ORDER = Comparator.comparing(UUID::toString);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobEmbeddingRepository jobEmbeddingRepository;

    @Autowired
    private JobEmbeddingStateService jobEmbeddingStateService;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tag;
    private UUID companyId;
    private UUID hrId;

    @BeforeEach
    void setUp() {
        tag = "U13T4-" + UUID.randomUUID();
        User hr = new User();
        hr.setEmail("hr-" + UUID.randomUUID() + "@example.com");
        hr.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        hr.setRole(Role.HR);
        hr.setFullName("Nha Tuyen Dung Test");
        hrId = userRepository.saveAndFlush(hr).getId();
        Company company = new Company();
        company.setOwnerId(hrId);
        company.setName("Cong ty Test " + UUID.randomUUID());
        companyId = companyRepository.saveAndFlush(company).getId();
    }

    @AfterEach
    void softDeleteOwnJobs() {
        jdbcTemplate.update("UPDATE jobs SET deleted_at = now() WHERE title LIKE ? AND deleted_at IS NULL", tag + "%");
    }

    // Vector don vi tren truc 2-3: cosine voi vector truy van [0,0,1,0,...] dung bang s.
    private static float[] vectorWithSimilarity(double s) {
        float[] v = new float[1536];
        v[2] = (float) s;
        v[3] = (float) Math.sqrt(1 - s * s);
        return v;
    }

    private Job newJob(String name) {
        Job job = new Job();
        job.setCompanyId(companyId);
        job.setCreatedBy(hrId);
        job.setTitle(tag + " " + name);
        job.setDescription("Mo ta cong viec test");
        job.setStatus(JobStatus.OPEN);
        job.setRecruitmentCycle(1);
        return job;
    }

    private UUID save(Job job) {
        return jobRepository.saveAndFlush(job).getId();
    }

    private UUID openJob(String name, Instant publishedAt) {
        Job job = newJob(name);
        job.setPublishedAt(publishedAt);
        return save(job);
    }

    private UUID embedded(UUID jobId, double similarity) {
        jobEmbeddingStateService.save(jobId, vectorWithSimilarity(similarity), MODEL);
        return jobId;
    }

    private List<UUID> search(
            List<String> categoryCodes,
            List<String> locationCodes,
            BigDecimal salaryMinVnd,
            List<String> workModes,
            int limit) {
        return jobRepository
                .findRankedForSearch(
                        "%" + tag + "%",
                        null,
                        null,
                        !categoryCodes.isEmpty(),
                        categoryCodes.isEmpty() ? List.of(NONE) : categoryCodes,
                        !locationCodes.isEmpty(),
                        locationCodes.isEmpty() ? List.of(NONE) : locationCodes,
                        salaryMinVnd,
                        null,
                        false,
                        !workModes.isEmpty(),
                        workModes.isEmpty() ? List.of(NONE) : workModes,
                        null,
                        QUERY_VECTOR,
                        limit)
                .stream()
                .map(Job::getId)
                .toList();
    }

    private List<UUID> searchAll(int limit) {
        return search(List.of(), List.of(), null, List.of(), limit);
    }

    private static List<UUID> pgSorted(UUID first, UUID second) {
        return Stream.of(first, second).sorted(PG_UUID_ORDER).toList();
    }

    // ---- (2) + (3) thu tu ----

    // Co embedding: khoang cach tang dan, cung khoang cach -> j.id ASC. Khong embedding: sau MOI viec co embedding (ke
    // ca viec co embedding dang tu 10 ngay truoc), COALESCE(published_at, created_at) DESC, cung thoi diem -> j.id DESC.
    @Test
    void findRankedForSearch_embeddedByDistanceIdAsc_thenUnembeddedByNewestIdDesc() {
        Instant old = NOW.minus(10, ChronoUnit.DAYS);
        UUID near = embedded(openJob("gan", old), 0.9);
        UUID tieA = embedded(openJob("hoa A", old), 0.6);
        UUID tieB = embedded(openJob("hoa B", old), 0.6);
        UUID unpublished = openJob("chua dang - lay created_at", null);
        UUID oneDay = openJob("1 ngay", NOW.minus(1, ChronoUnit.DAYS));
        Instant twoDays = NOW.minus(2, ChronoUnit.DAYS);
        UUID sameTimeA = openJob("2 ngay A", twoDays);
        UUID sameTimeB = openJob("2 ngay B", twoDays);

        List<UUID> result = searchAll(50);

        List<UUID> ties = pgSorted(tieA, tieB);
        List<UUID> sameTime = pgSorted(sameTimeA, sameTimeB);
        assertThat(result).containsExactly(
                near, ties.get(0), ties.get(1), unpublished, oneDay, sameTime.get(1), sameTime.get(0));
    }

    // ---- (4) limit ----

    @Test
    void findRankedForSearch_limit3With5Valid_returnsExactlyTheThreeEmbedded() {
        UUID first = embedded(openJob("e1", NOW.minus(5, ChronoUnit.DAYS)), 0.9);
        UUID second = embedded(openJob("e2", NOW.minus(5, ChronoUnit.DAYS)), 0.7);
        UUID third = embedded(openJob("e3", NOW.minus(5, ChronoUnit.DAYS)), 0.5);
        openJob("khong embedding moi nhat", NOW);
        openJob("khong embedding", NOW.minus(1, ChronoUnit.DAYS));

        assertThat(searchAll(3)).containsExactly(first, second, third);
        assertThat(searchAll(50)).hasSize(5);
    }

    // ---- (1) viec da nop KHONG bi loai (R-K5) ----

    @Test
    void findRankedForSearch_jobAlreadyAppliedInSameCycle_isIncluded() {
        UUID applied = embedded(openJob("da nop", NOW), 0.8);
        UUID other = embedded(openJob("chua nop", NOW), 0.5);

        User candidate = new User();
        candidate.setEmail("cand-" + UUID.randomUUID() + "@example.com");
        candidate.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        candidate.setRole(Role.CANDIDATE);
        candidate.setFullName("Ung Vien Test");
        UUID candidateId = userRepository.saveAndFlush(candidate).getId();
        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        UUID resumeId = resumeRepository.saveAndFlush(resume).getId();
        JobApplication application = new JobApplication();
        application.setJobId(applied);
        application.setCandidateId(candidateId);
        application.setResumeId(resumeId);
        application.setRecruitmentCycle(1);
        application.setStatus(ApplicationStatus.PENDING);
        application.setAiConsent(true);
        application.setAiConsentAt(Instant.now());
        jobApplicationRepository.saveAndFlush(application);

        assertThat(searchAll(50)).containsExactly(applied, other);
    }

    // ---- (5) dieu kien loc cua PUBLIC_JOB_FILTER_WHERE ----

    @Test
    void findRankedForSearch_locationCodes_excludesOtherProvince_keepsNullCode() {
        Job hanoi = newJob("Ha Noi");
        hanoi.setLocationCode("HA_NOI");
        UUID hanoiId = embedded(save(hanoi), 0.5);
        Job danang = newJob("Da Nang");
        danang.setLocationCode("DA_NANG");
        embedded(save(danang), 0.99);
        UUID nullCodeId = save(newJob("ma NULL"));

        assertThat(search(List.of(), List.of("HA_NOI"), null, List.of(), 50)).containsExactly(hanoiId, nullCodeId);
    }

    @Test
    void findRankedForSearch_categoryCodes_excludesOtherIndustry_keepsNullCode() {
        Job it = newJob("IT");
        it.setCategoryCode("IT_SOFTWARE");
        UUID itId = embedded(save(it), 0.5);
        Job accounting = newJob("Ke toan");
        accounting.setCategoryCode("ACCOUNTING_AUDIT");
        embedded(save(accounting), 0.99);
        UUID nullCodeId = save(newJob("ma NULL"));

        assertThat(search(List.of("IT_SOFTWARE"), List.of(), null, List.of(), 50)).containsExactly(itId, nullCodeId);
    }

    private UUID salaryJob(String name, Long min, Long max, String currency, Instant publishedAt) {
        Job job = newJob(name);
        job.setSalaryMin(min == null ? null : BigDecimal.valueOf(min));
        job.setSalaryMax(max == null ? null : BigDecimal.valueOf(max));
        job.setSalaryCurrency(currency);
        job.setPublishedAt(publishedAt);
        return save(job);
    }

    // salaryMinVnd 15 000 000: viec 9-14 trieu loai; dung bien salary_max = 15 000 000 giu; thoa thuan va USD giu (U07).
    @Test
    void findRankedForSearch_salaryMin15m_excludes9to14m_keepsBoundaryNegotiableAndForeignCurrency() {
        salaryJob("9-14 trieu", 9_000_000L, 14_000_000L, "VND", NOW);
        UUID boundary = salaryJob("10-15 trieu", 10_000_000L, 15_000_000L, "VND", NOW.minus(1, ChronoUnit.DAYS));
        UUID negotiable = salaryJob("thoa thuan", null, null, "VND", NOW.minus(2, ChronoUnit.DAYS));
        UUID usd = salaryJob("USD", 1_000L, 2_000L, "USD", NOW.minus(3, ChronoUnit.DAYS));

        assertThat(search(List.of(), List.of(), BigDecimal.valueOf(15_000_000L), List.of(), 50))
                .containsExactly(boundary, negotiable, usd);
    }

    @Test
    void findRankedForSearch_workModeRemote_excludesOnsite() {
        Job remote = newJob("tu xa");
        remote.setWorkMode("REMOTE");
        UUID remoteId = embedded(save(remote), 0.5);
        Job onsite = newJob("van phong");
        onsite.setWorkMode("ONSITE");
        embedded(save(onsite), 0.99);

        assertThat(search(List.of(), List.of(), null, List.of("REMOTE"), 50)).containsExactly(remoteId);
    }

    // ---- (6) viec khong cong khai ----

    @Test
    void findRankedForSearch_closedSoftDeletedAndExpiredJobs_areExcluded_evenWithHighSimilarity() {
        UUID open = embedded(openJob("OPEN", NOW), 0.5);
        Job closed = newJob("CLOSED");
        closed.setStatus(JobStatus.CLOSED);
        embedded(save(closed), 0.99);
        Job deleted = newJob("da xoa mem");
        deleted.setDeletedAt(Instant.now());
        embedded(save(deleted), 0.99);
        Job expired = newJob("qua han");
        expired.setDeadline(LocalDate.now().minusDays(2));
        embedded(save(expired), 0.99);

        assertThat(searchAll(50)).containsExactly(open);
    }

    // ---- guard NaN: chi ap cho hang co embedding ----

    // Vector 0 ghi thang (bo qua EmbeddingService) - mo phong du lieu cu. Hang nay co embedding nen bi guard loai han,
    // KHONG roi xuong nhom "chua co embedding"; viec that su chua co embedding van co.
    @Test
    void findRankedForSearch_zeroVectorEmbedding_isExcluded_unembeddedJobStillIncluded() {
        UUID normal = embedded(openJob("binh thuong", NOW), 0.5);
        UUID zero = openJob("vector 0", NOW);
        jobEmbeddingStateService.save(zero, new float[1536], MODEL);
        UUID unembedded = openJob("chua co embedding", NOW);

        List<UUID> result = searchAll(50);

        assertThat(result).containsExactly(normal, unembedded);
        assertThat(result).doesNotContain(zero);
    }

    // ---- (7) findJobIdsWithEmbeddingIn (L8) ----

    @Test
    void findJobIdsWithEmbeddingIn_returnsOnlyIdsWithEmbedding_ignoresUnknownIds() {
        UUID withA = embedded(openJob("co A", NOW), 0.5);
        UUID withB = embedded(openJob("co B", NOW), 0.7);
        UUID without = openJob("khong co", NOW);
        UUID unknown = UUID.randomUUID();

        List<UUID> ids = jobEmbeddingRepository.findJobIdsWithEmbeddingIn(List.of(withA, without, withB, unknown));

        assertThat(ids).containsExactlyInAnyOrder(withA, withB);
    }
}
