package com.recruitment.interviewinvitation;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// FR-H12 T14 (REQUIREMENT muc 4.1, 7.1). Dung Flyway API truc tiep, khuon JobCatalogMigrationTest: can trang thai "DB o
// V15, co san giay moi dang cu (scheduled_at/location tren interview_invitations)" roi moi chay V16 - context Spring luon
// migrate het mot lan, khong chen du lieu giua V15 va V16 duoc.
@Testcontainers
class InterviewInvitationSlotMigrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    private static final OffsetDateTime SCHEDULE_WITH_LOCATION = OffsetDateTime.parse("2026-11-02T02:00:00Z");
    private static final OffsetDateTime SCHEDULE_NO_LOCATION = OffsetDateTime.parse("2026-11-03T03:30:45Z");
    private static final OffsetDateTime SENT_AT = OffsetDateTime.parse("2026-10-01T02:00:00Z");
    private static final OffsetDateTime CREATED_NO_SENT = OffsetDateTime.parse("2026-10-02T04:00:00Z");
    private static final OffsetDateTime OLDER_CREATED = OffsetDateTime.parse("2026-09-01T00:00:00Z");
    private static final OffsetDateTime NEWER_CREATED = OffsetDateTime.parse("2026-09-05T00:00:00Z");

    private static final UUID INVITATION_WITH_LOCATION = UUID.randomUUID();
    private static final UUID INVITATION_NO_LOCATION_NO_SENT = UUID.randomUUID();
    private static final UUID INVITATION_OLDER = UUID.randomUUID();
    private static final UUID INVITATION_NEWER = UUID.randomUUID();

    private static Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @BeforeAll
    static void migrateToV15_insertLegacyInvitations_thenMigrateV16() throws Exception {
        flyway("15").migrate();

        try (Connection c = connect()) {
            UUID hrId = UUID.randomUUID();
            UUID companyId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();
            UUID candidateId = UUID.randomUUID();
            UUID resumeId = UUID.randomUUID();
            try (Statement s = c.createStatement()) {
                s.execute("INSERT INTO users (id, email, password_hash, role, full_name) VALUES ('" + hrId
                        + "', 'hr-v16@test.local', 'x', 'HR', 'HR V16')");
                s.execute("INSERT INTO users (id, email, password_hash, role, full_name) VALUES ('" + candidateId
                        + "', 'uv-v16@test.local', 'x', 'CANDIDATE', 'UV V16')");
                s.execute("INSERT INTO companies (id, owner_id, name) VALUES ('" + companyId + "', '" + hrId
                        + "', 'Cong ty V16')");
                s.execute("INSERT INTO jobs (id, company_id, created_by, title, description) VALUES ('" + jobId
                        + "', '" + companyId + "', '" + hrId + "', 'Tin V16', 'Mo ta')");
                s.execute("INSERT INTO resumes (id, candidate_id, file_url, file_name, file_type) VALUES ('" + resumeId
                        + "', '" + candidateId + "', 'resumes/v16.pdf', 'cv.pdf', 'PDF')");
            }
            UUID appA = insertApplication(c, jobId, candidateId, resumeId, 1);
            UUID appB = insertApplication(c, jobId, candidateId, resumeId, 2);
            UUID appC = insertApplication(c, jobId, candidateId, resumeId, 3);

            insertLegacyInvitation(c, INVITATION_WITH_LOCATION, appA, SCHEDULE_WITH_LOCATION, "Tầng 3, 45 Bạch Đằng",
                    SENT_AT, SENT_AT);
            insertLegacyInvitation(c, INVITATION_NO_LOCATION_NO_SENT, appB, SCHEDULE_NO_LOCATION, null, null,
                    CREATED_NO_SENT);
            // API cu khong tao duoc hai giay moi cho mot don - du lieu ghi tay de kiem buoc 4 cua V16.
            insertLegacyInvitation(c, INVITATION_OLDER, appC, SCHEDULE_WITH_LOCATION, "Dia diem cu", OLDER_CREATED,
                    OLDER_CREATED);
            insertLegacyInvitation(c, INVITATION_NEWER, appC, SCHEDULE_NO_LOCATION, "Dia diem moi", NEWER_CREATED,
                    NEWER_CREATED);
        }

        flyway("latest").migrate();
    }

    private static UUID insertApplication(Connection c, UUID jobId, UUID candidateId, UUID resumeId, int cycle)
            throws Exception {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO job_applications (id, job_id, candidate_id, resume_id, recruitment_cycle, status, "
                        + "ai_consent, ai_consent_at) VALUES (?, ?, ?, ?, ?, 'INTERVIEW_INVITED', TRUE, now())")) {
            ps.setObject(1, id);
            ps.setObject(2, jobId);
            ps.setObject(3, candidateId);
            ps.setObject(4, resumeId);
            ps.setInt(5, cycle);
            ps.executeUpdate();
        }
        return id;
    }

    private static void insertLegacyInvitation(
            Connection c,
            UUID id,
            UUID applicationId,
            OffsetDateTime scheduledAt,
            String location,
            OffsetDateTime sentAt,
            OffsetDateTime createdAt)
            throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO interview_invitations (id, application_id, scheduled_at, location, subject, "
                        + "rendered_content, sent_at, created_at) VALUES (?, ?, ?, ?, 'Thu moi', 'Noi dung', ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, applicationId);
            ps.setObject(3, scheduledAt);
            ps.setString(4, location);
            ps.setObject(5, sentAt);
            ps.setObject(6, createdAt);
            ps.executeUpdate();
        }
    }

    private record SlotRow(UUID id, short displayOrder, OffsetDateTime scheduledAt, String location) {
    }

    private record InvitationRow(UUID confirmedSlotId, OffsetDateTime confirmedAt, OffsetDateTime supersededAt) {
    }

    private static List<SlotRow> slotsOf(UUID invitationId) throws Exception {
        List<SlotRow> rows = new ArrayList<>();
        try (Connection c = connect();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id, display_order, scheduled_at, location FROM interview_invitation_slots "
                                + "WHERE invitation_id = ? ORDER BY display_order")) {
            ps.setObject(1, invitationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new SlotRow(
                            rs.getObject("id", UUID.class),
                            rs.getShort("display_order"),
                            rs.getObject("scheduled_at", OffsetDateTime.class),
                            rs.getString("location")));
                }
            }
        }
        return rows;
    }

    private static InvitationRow invitation(UUID id) throws Exception {
        try (Connection c = connect();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT confirmed_slot_id, confirmed_at, superseded_at FROM interview_invitations WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return new InvitationRow(
                        rs.getObject("confirmed_slot_id", UUID.class),
                        rs.getObject("confirmed_at", OffsetDateTime.class),
                        rs.getObject("superseded_at", OffsetDateTime.class));
            }
        }
    }

    private static List<String> columnsOf(String table) throws Exception {
        List<String> columns = new ArrayList<>();
        try (Connection c = connect();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT column_name FROM information_schema.columns WHERE table_name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString(1));
                }
            }
        }
        return columns;
    }

    @Test
    void invitationWithLocation_becomesOneConfirmedSlotWithOldValues_confirmedAtIsSentAt() throws Exception {
        List<SlotRow> slots = slotsOf(INVITATION_WITH_LOCATION);

        assertThat(slots).hasSize(1);
        SlotRow slot = slots.get(0);
        assertThat(slot.displayOrder()).isEqualTo((short) 1);
        assertThat(slot.scheduledAt()).isEqualTo(SCHEDULE_WITH_LOCATION);
        assertThat(slot.location()).isEqualTo("Tầng 3, 45 Bạch Đằng");

        InvitationRow row = invitation(INVITATION_WITH_LOCATION);
        assertThat(row.confirmedSlotId()).isEqualTo(slot.id());
        assertThat(row.confirmedAt()).isEqualTo(SENT_AT);
        assertThat(row.supersededAt()).isNull();
    }

    // Gia tri cu giu NGUYEN (ke ca giay le 45 s) - V16 khong cat ve dau phut; location NULL giu NULL.
    @Test
    void invitationWithoutLocationAndSentAt_keepsNullLocationAndSeconds_confirmedAtIsCreatedAt() throws Exception {
        List<SlotRow> slots = slotsOf(INVITATION_NO_LOCATION_NO_SENT);

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).scheduledAt()).isEqualTo(SCHEDULE_NO_LOCATION);
        assertThat(slots.get(0).location()).isNull();

        InvitationRow row = invitation(INVITATION_NO_LOCATION_NO_SENT);
        assertThat(row.confirmedSlotId()).isEqualTo(slots.get(0).id());
        assertThat(row.confirmedAt()).isEqualTo(CREATED_NO_SENT);
    }

    @Test
    void applicationWithTwoInvitations_newestStaysActive_olderIsSuperseded() throws Exception {
        assertThat(invitation(INVITATION_NEWER).supersededAt()).isNull();
        assertThat(invitation(INVITATION_OLDER).supersededAt()).isNotNull();
        // Ca hai van duoc chuyen thanh 1 khung da chot.
        assertThat(slotsOf(INVITATION_OLDER)).hasSize(1);
        assertThat(invitation(INVITATION_OLDER).confirmedSlotId()).isEqualTo(slotsOf(INVITATION_OLDER).get(0).id());
        assertThat(slotsOf(INVITATION_NEWER)).extracting(SlotRow::location).containsExactly("Dia diem moi");
    }

    @Test
    void oldColumnsAreDropped_newColumnsExist() throws Exception {
        assertThat(columnsOf("interview_invitations"))
                .doesNotContain("scheduled_at", "location")
                .contains("confirmed_slot_id", "confirmed_at", "superseded_at");
        assertThat(columnsOf("notifications")).contains("email_body");
    }
}
