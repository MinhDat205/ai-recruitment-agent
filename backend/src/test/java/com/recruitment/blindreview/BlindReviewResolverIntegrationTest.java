package com.recruitment.blindreview;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// FR-H16 T3 - BlindReviewResolver (R-S1, R-S3, R-S4) va IdentityMaskSource (R-M1). @Transactional: moi @Test rollback
// rieng; test khong so sanh thoi diem nen now() transaction-scoped khong anh huong (CLAUDE.md muc 3c). Du lieu dung bang
// repository/JDBC (khuon TalentPoolOwnerControllerIntegrationTest); lich su trang thai ghi thang bang JDBC.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BlindReviewResolverIntegrationTest {

    private static final String HIDDEN = IdentityMasker.REPLACEMENT;

    @Autowired
    private BlindReviewResolver resolver;

    @Autowired
    private IdentityMaskSource maskSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    private record Fixture(UUID hrId, UUID companyId) {
    }

    private record App(UUID id, UUID candidateId, UUID resumeId) {
    }

    // ---- Du lieu ----

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private User user(Role role, String fullName, String email, String phone) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        user.setRole(role);
        user.setFullName(fullName);
        user.setPhone(phone);
        return userRepository.saveAndFlush(user);
    }

    private Fixture company() {
        User hr = user(Role.HR, "Nguoi Tuyen Dung", uniqueEmail("h16r-hr"), "0911111111");
        Company company = new Company();
        company.setOwnerId(hr.getId());
        company.setName("Cong ty " + UUID.randomUUID());
        return new Fixture(hr.getId(), companyRepository.saveAndFlush(company).getId());
    }

    private UUID job(Fixture f, boolean blindReview) {
        Job job = new Job();
        job.setCompanyId(f.companyId());
        job.setCreatedBy(f.hrId());
        job.setTitle("Tin H16");
        job.setDescription("Mo ta cong viec");
        job.setStatus(JobStatus.OPEN);
        job.setRecruitmentCycle(1);
        UUID id = jobRepository.saveAndFlush(job).getId();
        if (blindReview) {
            setBlindReview(id, true);
        }
        return id;
    }

    private void setBlindReview(UUID jobId, boolean enabled) {
        jdbcTemplate.update("UPDATE jobs SET blind_review_enabled = ? WHERE id = ?", enabled, jobId);
    }

    private App application(UUID jobId, String status, String fullName, String email, String phone) {
        User candidate = user(Role.CANDIDATE, fullName, email, phone);
        Resume resume = new Resume();
        resume.setCandidateId(candidate.getId());
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        resume = resumeRepository.saveAndFlush(resume);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO job_applications (id, job_id, candidate_id, resume_id, recruitment_cycle, status, ai_consent,
                                              ai_consent_at)
                VALUES (?, ?, ?, ?, 1, ?, TRUE, now())
                """,
                id, jobId, candidate.getId(), resume.getId(), status);
        history(id, null, "PENDING");
        return new App(id, candidate.getId(), resume.getId());
    }

    private App application(UUID jobId, String status) {
        return application(jobId, status, "Ung Vien " + UUID.randomUUID().toString().substring(0, 8),
                uniqueEmail("h16r-c"), null);
    }

    private void history(UUID applicationId, String from, String to) {
        jdbcTemplate.update(
                "INSERT INTO application_status_history (application_id, from_status, to_status) VALUES (?, ?, ?)",
                applicationId, from, to);
    }

    private void parsedContact(UUID resumeId, String contactJson) {
        jdbcTemplate.update(
                "INSERT INTO resume_parsed_data (id, resume_id, raw_text, data, model, prompt_version) "
                        + "VALUES (?, ?, 'raw', CAST(? AS jsonb), 'claude-sonnet-4-6', 'resume-parse-v1')",
                UUID.randomUUID(), resumeId, "{\"contact\": " + contactJson + "}");
    }

    // ---- R-S1 ----

    @Test
    void jobDisabledNeverMasks() {
        UUID jobId = job(company(), false);
        App pending = application(jobId, "PENDING");

        assertThat(resolver.isMasked(pending.id())).isFalse();
        assertThat(resolver.maskedAmong(List.of(pending.id()))).isEmpty();
    }

    @Test
    void jobEnabledMasksPendingApplication() {
        UUID jobId = job(company(), true);
        App pending = application(jobId, "PENDING");

        assertThat(resolver.isMasked(pending.id())).isTrue();
    }

    @Test
    void rejectedOrWithdrawnFromPendingStaysMasked() {
        // R-S4: doi trang thai khac (khong qua giay moi) KHONG bo an danh.
        UUID jobId = job(company(), true);
        App rejected = application(jobId, "REJECTED");
        history(rejected.id(), "PENDING", "REJECTED");
        App withdrawn = application(jobId, "WITHDRAWN");
        history(withdrawn.id(), "PENDING", "WITHDRAWN");

        assertThat(resolver.isMasked(rejected.id())).isTrue();
        assertThat(resolver.isMasked(withdrawn.id())).isTrue();
    }

    @Test
    void everInvitedIsNeverMaskedWhateverCurrentStatus() {
        UUID jobId = job(company(), true);
        App invited = application(jobId, "INTERVIEW_INVITED");
        history(invited.id(), "PENDING", "INTERVIEW_INVITED");
        App hired = application(jobId, "HIRED");
        history(hired.id(), "PENDING", "INTERVIEW_INVITED");
        history(hired.id(), "INTERVIEW_INVITED", "HIRED");
        App rejectedAfterInvite = application(jobId, "REJECTED");
        history(rejectedAfterInvite.id(), "PENDING", "INTERVIEW_INVITED");
        history(rejectedAfterInvite.id(), "INTERVIEW_INVITED", "REJECTED");

        assertThat(resolver.isMasked(invited.id())).isFalse();
        assertThat(resolver.isMasked(hired.id())).isFalse();
        assertThat(resolver.isMasked(rejectedAfterInvite.id())).isFalse();
    }

    @Test
    void reEnablingAfterInviteDoesNotMaskAgain() {
        // R-S3: da co dong INTERVIEW_INVITED thi bat lai cong tac cung khong an danh lai.
        UUID jobId = job(company(), true);
        App invited = application(jobId, "INTERVIEW_INVITED");
        history(invited.id(), "PENDING", "INTERVIEW_INVITED");
        App pending = application(jobId, "PENDING");

        setBlindReview(jobId, false);
        assertThat(resolver.isMasked(pending.id())).isFalse();
        setBlindReview(jobId, true);

        assertThat(resolver.isMasked(invited.id())).isFalse();
        assertThat(resolver.isMasked(pending.id())).isTrue();
    }

    @Test
    void historyOfAnotherApplicationDoesNotUnmask() {
        UUID jobId = job(company(), true);
        App invited = application(jobId, "INTERVIEW_INVITED");
        history(invited.id(), "PENDING", "INTERVIEW_INVITED");
        App pending = application(jobId, "PENDING");

        assertThat(resolver.isMasked(pending.id())).isTrue();
    }

    @Test
    void batchMatchesSingleAcrossJobs() {
        Fixture f = company();
        UUID enabledJob = job(f, true);
        UUID disabledJob = job(f, false);
        App maskedA = application(enabledJob, "PENDING");
        App maskedB = application(enabledJob, "REJECTED");
        history(maskedB.id(), "PENDING", "REJECTED");
        App invited = application(enabledJob, "INTERVIEW_INVITED");
        history(invited.id(), "PENDING", "INTERVIEW_INVITED");
        App otherJob = application(disabledJob, "PENDING");
        UUID unknown = UUID.randomUUID();
        List<UUID> ids = List.of(maskedA.id(), maskedB.id(), invited.id(), otherJob.id(), unknown);

        Set<UUID> batch = resolver.maskedAmong(ids);

        assertThat(batch).containsExactlyInAnyOrder(maskedA.id(), maskedB.id());
        for (UUID id : ids) {
            assertThat(resolver.isMasked(id)).as("don %s", id).isEqualTo(batch.contains(id));
        }
    }

    @Test
    void emptyBatchReturnsEmpty() {
        assertThat(resolver.maskedAmong(List.of())).isEmpty();
    }

    // ---- R-M1 (IdentityMaskSource) ----

    @Test
    void sourceLoadsAccountAndParsedContactValues() {
        UUID jobId = job(company(), true);
        App app = application(jobId, "PENDING", "Quốc Huy", "quochuyuv-" + UUID.randomUUID() + "@gmail.com",
                "0900000111");
        String accountEmail = userRepository.findById(app.candidateId()).orElseThrow().getEmail();
        parsedContact(app.resumeId(), """
                {"fullName": "QUỐC HUY", "email": "quochuy.cv@test.local", "phone": "0900 000 111",
                 "address": "Quận Hải Châu, Đà Nẵng", "linkedin": "linkedin.com/in/quochuy"}
                """);

        IdentityMasker masker = maskSource.load(app.id());
        String text = "Quốc Huy | " + accountEmail + " | 0900000111 | quochuy.cv@test.local | 0900 000 111 | "
                + "Quận Hải Châu, Đà Nẵng | linkedin.com/in/quochuy";

        assertThat(masker.mask(text)).isEqualTo(String.join(" | ", HIDDEN, HIDDEN, HIDDEN, HIDDEN, HIDDEN, HIDDEN, HIDDEN));
    }

    @Test
    void sourceWithoutParsedDataUsesAccountValuesOnly() {
        UUID jobId = job(company(), true);
        App app = application(jobId, "PENDING", "Lê Thị Lan", uniqueEmail("h16r-lan"), null);

        IdentityMasker masker = maskSource.load(app.id());

        assertThat(masker.mask("Chào Lê Thị Lan, Đà Nẵng")).isEqualTo("Chào " + HIDDEN + ", Đà Nẵng");
    }

    @Test
    void sourceIgnoresMissingContactFields() {
        UUID jobId = job(company(), true);
        App app = application(jobId, "PENDING", "Trần Bảo Ngọc", uniqueEmail("h16r-ngoc"), null);
        parsedContact(app.resumeId(), "{\"fullName\": null, \"email\": \"ngoc.cv@test.local\"}");

        IdentityMasker masker = maskSource.load(app.id());

        assertThat(masker.mask("Trần Bảo Ngọc - ngoc.cv@test.local")).isEqualTo(HIDDEN + " - " + HIDDEN);
    }

    @Test
    void sourceBatchKeepsValuesPerApplication() {
        UUID jobId = job(company(), true);
        App a = application(jobId, "PENDING", "Phạm Văn Khoa", uniqueEmail("h16r-khoa"), null);
        App b = application(jobId, "PENDING", "Đỗ Minh Tâm", uniqueEmail("h16r-tam"), null);
        UUID unknown = UUID.randomUUID();

        Map<UUID, IdentityMasker> maskers = maskSource.load(List.of(a.id(), b.id(), unknown));

        assertThat(maskers).containsOnlyKeys(a.id(), b.id(), unknown);
        assertThat(maskers.get(a.id()).mask("Phạm Văn Khoa, Đỗ Minh Tâm")).isEqualTo(HIDDEN + ", Đỗ Minh Tâm");
        assertThat(maskers.get(b.id()).mask("Phạm Văn Khoa, Đỗ Minh Tâm")).isEqualTo("Phạm Văn Khoa, " + HIDDEN);
        assertThat(maskers.get(unknown)).isSameAs(IdentityMasker.none());
        assertThat(maskSource.load(List.of())).isEmpty();
    }
}
