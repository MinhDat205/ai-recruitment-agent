package com.recruitment.interviewinvitation;

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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// FR-H12 T15, T16 (REQUIREMENT muc 4.1, 7.1) - rang buoc DB cua V16, insert thang bang JdbcTemplate (khong qua service).
// KHONG @Transactional: Postgres huy ca transaction sau cau lenh loi dau tien; moi cau tu commit, du lieu ngau nhien rieng,
// khong don - khuon TalentPoolConsentSchemaConstraintIntegrationTest. Moi don mot recruitment_cycle rieng
// (uq_application_per_cycle).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class InterviewInvitationSchemaConstraintIntegrationTest {

    private static final OffsetDateTime SENT_AT = OffsetDateTime.of(2026, 10, 6, 2, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime SLOT_1 = OffsetDateTime.of(2026, 11, 2, 2, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime SLOT_2 = OffsetDateTime.of(2026, 11, 3, 2, 0, 0, 0, ZoneOffset.UTC);

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
        user.setEmail("t15i-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        user.setRole(role);
        user.setFullName("Nguoi Dung Test");
        return userRepository.save(user);
    }

    private UUID application() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO job_applications
                    (id, job_id, candidate_id, resume_id, recruitment_cycle, status, ai_consent, ai_consent_at)
                VALUES (?, ?, ?, ?, ?, 'INTERVIEW_INVITED', TRUE, now())
                """,
                id, jobId, candidateId, resumeId, nextCycle++);
        return id;
    }

    private UUID invitation(UUID applicationId, OffsetDateTime supersededAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO interview_invitations (id, application_id, subject, rendered_content, sent_at, superseded_at)
                VALUES (?, ?, 'Thu moi', 'Noi dung', ?, ?)
                """,
                id, applicationId, SENT_AT, supersededAt);
        return id;
    }

    private UUID slot(UUID invitationId, int displayOrder, OffsetDateTime scheduledAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO interview_invitation_slots (id, invitation_id, display_order, scheduled_at, location)
                VALUES (?, ?, ?, ?, 'Tang 3')
                """,
                id, invitationId, displayOrder, scheduledAt);
        return id;
    }

    private int confirm(UUID invitationId, UUID slotId, OffsetDateTime confirmedAt) {
        return jdbcTemplate.update(
                "UPDATE interview_invitations SET confirmed_slot_id = ?, confirmed_at = ? WHERE id = ?",
                slotId, confirmedAt, invitationId);
    }

    // ---- T15 ----

    @Test
    void t15_sameScheduledAtTwiceInOneInvitation_rejected_butAllowedAcrossInvitations() {
        UUID first = invitation(application(), null);
        slot(first, 1, SLOT_1);

        assertThatThrownBy(() -> slot(first, 2, SLOT_1)).isInstanceOf(DataIntegrityViolationException.class);

        UUID second = invitation(application(), null);
        assertThat(slot(second, 1, SLOT_1)).isNotNull();
    }

    @Test
    void t15_displayOrderBounds_zeroAndSixRejected_oneAndFiveAccepted() {
        UUID inv = invitation(application(), null);

        assertThatThrownBy(() -> slot(inv, 0, SLOT_1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> slot(inv, 6, SLOT_1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(slot(inv, 1, SLOT_1)).isNotNull();
        assertThat(slot(inv, 5, SLOT_2)).isNotNull();
    }

    @Test
    void t15_sameDisplayOrderTwiceInOneInvitation_rejected() {
        UUID inv = invitation(application(), null);
        slot(inv, 1, SLOT_1);

        assertThatThrownBy(() -> slot(inv, 1, SLOT_2)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void t15_secondActiveInvitationForSameApplication_rejected_supersededAllowed() {
        UUID app = application();
        invitation(app, null);

        assertThatThrownBy(() -> invitation(app, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(invitation(app, SENT_AT)).isNotNull();
    }

    @Test
    void t15_confirmedSlotMustBelongToSameInvitation() {
        UUID inv = invitation(application(), null);
        UUID otherInv = invitation(application(), null);
        UUID ownSlot = slot(inv, 1, SLOT_1);
        UUID otherSlot = slot(otherInv, 1, SLOT_1);

        assertThatThrownBy(() -> confirm(inv, otherSlot, SENT_AT)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(confirm(inv, ownSlot, SENT_AT)).isEqualTo(1);
    }

    @Test
    void t15_confirmedSlotAndConfirmedAtMustBeSetTogether() {
        UUID inv = invitation(application(), null);
        UUID ownSlot = slot(inv, 1, SLOT_1);

        assertThatThrownBy(() -> confirm(inv, ownSlot, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> confirm(inv, null, SENT_AT)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(confirm(inv, ownSlot, SENT_AT)).isEqualTo(1);
    }

    // ---- T16 ----

    // Xoa don -> CASCADE xoa giay moi -> CASCADE xoa khung; FK nguoc fk_invitation_confirmed_slot (NO ACTION) chi kiem o
    // cuoi cau lenh, luc dong giay moi tham chieu da bi xoa (muc 12 L1). Cung duong voi reset-test-data.sql.
    @Test
    void t16_deletingApplicationWithConfirmedInvitation_cascadesToSlots() {
        UUID app = application();
        UUID inv = invitation(app, null);
        UUID ownSlot = slot(inv, 1, SLOT_1);
        confirm(inv, ownSlot, SENT_AT);

        int deleted = jdbcTemplate.update("DELETE FROM job_applications WHERE id = ?", app);

        assertThat(deleted).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM interview_invitations WHERE id = ?", Integer.class, inv))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM interview_invitation_slots WHERE invitation_id = ?", Integer.class, inv))
                .isZero();
    }

    @Test
    void t16_deletingInvitationDirectly_cascadesToSlots_likeResetTestData() {
        UUID app = application();
        UUID inv = invitation(app, null);
        UUID ownSlot = slot(inv, 1, SLOT_1);
        slot(inv, 2, SLOT_2);
        confirm(inv, ownSlot, SENT_AT);

        int deleted = jdbcTemplate.update("DELETE FROM interview_invitations WHERE application_id = ?", app);

        assertThat(deleted).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM interview_invitation_slots WHERE invitation_id = ?", Integer.class, inv))
                .isZero();
    }
}
