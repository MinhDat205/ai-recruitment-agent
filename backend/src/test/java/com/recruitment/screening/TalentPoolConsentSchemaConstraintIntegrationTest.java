package com.recruitment.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// FR-U09 T15 (muc 4.1) - CHECK chk_talent_pool_consent_state cua V14, insert thang bang JdbcTemplate (khong qua service).
// Moi don dung mot recruitment_cycle khac nhau de khong vuong uq_application_per_cycle. KHONG @Transactional: Postgres
// huy ca transaction sau cau lenh loi dau tien; moi cau tu commit, du lieu ngau nhien rieng, khong don (L2 cua FR-H10).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class TalentPoolConsentSchemaConstraintIntegrationTest {

    private static final OffsetDateTime CONSENTED_AT = OffsetDateTime.of(2026, 10, 5, 17, 35, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime LATER = CONSENTED_AT.plusHours(2);
    private static final OffsetDateTime EARLIER = CONSENTED_AT.minusSeconds(1);

    private static final String INSERT_WITH_CONSENT =
            """
            INSERT INTO job_applications
                (id, job_id, candidate_id, resume_id, recruitment_cycle, status, ai_consent, ai_consent_at,
                 talent_pool_consent, talent_pool_consent_at, talent_pool_consent_revoked_at)
            VALUES (?, ?, ?, ?, ?, 'PENDING', TRUE, now(), ?, ?, ?)
            """;

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

    private UUID jobId;
    private UUID candidateId;
    private UUID resumeId;
    private int nextCycle;

    @BeforeEach
    void createJobAndCandidate() {
        User hr = user(Role.HR);
        Company company = new Company();
        company.setOwnerId(hr.getId());
        company.setName("Cong ty Test " + UUID.randomUUID());
        company = companyRepository.save(company);

        Job job = new Job();
        job.setCompanyId(company.getId());
        job.setCreatedBy(hr.getId());
        job.setTitle("Nhan vien CSKH");
        job.setDescription("Mo ta cong viec");
        job.setStatus(JobStatus.OPEN);
        job.setRecruitmentCycle(1);
        jobId = jobRepository.save(job).getId();

        User candidate = user(Role.CANDIDATE);
        candidateId = candidate.getId();
        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        resumeId = resumeRepository.save(resume).getId();
        nextCycle = 1;
    }

    private User user(Role role) {
        User user = new User();
        user.setEmail("t15c-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        user.setRole(role);
        user.setFullName("Nguoi Dung Test");
        return userRepository.save(user);
    }

    private int insert(boolean consent, OffsetDateTime consentAt, OffsetDateTime revokedAt) {
        return jdbcTemplate.update(
                INSERT_WITH_CONSENT,
                UUID.randomUUID(), jobId, candidateId, resumeId, nextCycle++, consent, consentAt, revokedAt);
    }

    @Test
    void t15_threeValidStates_accepted() {
        assertThat(insert(false, null, null)).as("NOT_GIVEN").isEqualTo(1);
        assertThat(insert(true, CONSENTED_AT, null)).as("ACTIVE").isEqualTo(1);
        assertThat(insert(false, CONSENTED_AT, LATER)).as("REVOKED").isEqualTo(1);
        assertThat(insert(false, CONSENTED_AT, CONSENTED_AT)).as("REVOKED, rut cung thoi diem (bien >=)").isEqualTo(1);
    }

    @Test
    void t15_invalidCombinations_rejected() {
        assertThatThrownBy(() -> insert(true, null, null))
                .as("TRUE nhung khong co consent_at")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(true, CONSENTED_AT, LATER))
                .as("TRUE nhung da co revoked_at")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(false, CONSENTED_AT, null))
                .as("FALSE, co consent_at, khong revoked_at")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(false, null, LATER))
                .as("FALSE, khong consent_at nhung co revoked_at")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(false, CONSENTED_AT, EARLIER))
                .as("revoked_at truoc consent_at")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void t15_rowWithoutConsentColumns_defaultsToNotGiven() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO job_applications
                    (id, job_id, candidate_id, resume_id, recruitment_cycle, status, ai_consent, ai_consent_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING', TRUE, now())
                """,
                id, jobId, candidateId, resumeId, nextCycle++);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT talent_pool_consent, talent_pool_consent_at, talent_pool_consent_revoked_at
                FROM job_applications WHERE id = ?
                """,
                id);
        assertThat(row.get("talent_pool_consent")).isEqualTo(false);
        assertThat(row.get("talent_pool_consent_at")).isNull();
        assertThat(row.get("talent_pool_consent_revoked_at")).isNull();
    }
}
