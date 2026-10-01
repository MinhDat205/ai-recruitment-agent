package com.recruitment.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// REQUIREMENT FR-C05 muc 7.2 (R-D1..R-D3). Dung Flyway API truc tiep (khong Spring context): can dung
// trang thai "DB da o V8, co san job cu" roi moi chay V9 - context Spring luon migrate het mot lan,
// khong chen du lieu giua V8 va V9 duoc.
@Testcontainers
class JobCatalogMigrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    private static final OffsetDateTime OLD_UPDATED_AT = OffsetDateTime.parse("2020-01-01T00:00:00Z");

    private static final UUID JOB_BOTH_MATCH = UUID.randomUUID();
    private static final UUID JOB_OLD_PROVINCE_DASH = UUID.randomUUID();
    private static final UUID JOB_UNMATCHED = UUID.randomUUID();
    private static final UUID JOB_PARTIAL = UUID.randomUUID();
    private static final UUID JOB_SOFT_DELETED = UUID.randomUUID();
    private static final UUID JOB_EMPTY = UUID.randomUUID();

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
    static void migrateToV8_insertLegacyJobs_thenMigrateV9() throws Exception {
        flyway("8").migrate();

        try (Connection c = connect()) {
            UUID userId = UUID.randomUUID();
            UUID companyId = UUID.randomUUID();
            try (Statement s = c.createStatement()) {
                s.execute("INSERT INTO users (id, email, password_hash, role, full_name) VALUES ('" + userId
                        + "', 'hr-v9@test.local', 'x', 'HR', 'HR V9')");
                s.execute("INSERT INTO companies (id, owner_id, name) VALUES ('" + companyId + "', '" + userId
                        + "', 'Cong ty V9')");
            }
            insertJob(c, companyId, userId, JOB_BOTH_MATCH, "Công nghệ thông tin", "TP. Hồ Chí Minh", false);
            insertJob(c, companyId, userId, JOB_OLD_PROVINCE_DASH, "Kế toán – Kiểm toán", "Bình Dương", false);
            insertJob(c, companyId, userId, JOB_UNMATCHED, "Ngành lạ", "Quận 1, HCM và Bình Dương", false);
            insertJob(c, companyId, userId, JOB_PARTIAL, "Kinh doanh - Bán hàng", "Nhiều nơi", false);
            insertJob(c, companyId, userId, JOB_SOFT_DELETED, "Trí tuệ nhân tạo", "Hà Nội", true);
            insertJob(c, companyId, userId, JOB_EMPTY, null, null, false);
        }

        flyway("latest").migrate();
    }

    // updated_at dat TUONG MINH trong INSERT (trigger set_updated_at chi chay BEFORE UPDATE) de kiem
    // V9 khong lam doi no.
    private static void insertJob(
            Connection c, UUID companyId, UUID userId, UUID id, String category, String location, boolean deleted)
            throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO jobs (id, company_id, created_by, title, description, category, location, "
                        + "updated_at, deleted_at) VALUES (?, ?, ?, 'Tin', 'Mo ta', ?, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, companyId);
            ps.setObject(3, userId);
            ps.setString(4, category);
            ps.setString(5, location);
            ps.setObject(6, OLD_UPDATED_AT);
            ps.setObject(7, deleted ? OLD_UPDATED_AT : null);
            ps.executeUpdate();
        }
    }

    private record JobRow(String categoryCode, String locationCode, byte[] categoryBytes, byte[] locationBytes,
            OffsetDateTime updatedAt) {
    }

    private static JobRow load(UUID id) throws Exception {
        try (Connection c = connect();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT category_code, location_code, convert_to(category, 'UTF8') AS category_bytes, "
                                + "convert_to(location, 'UTF8') AS location_bytes, updated_at FROM jobs WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return new JobRow(
                        rs.getString("category_code"),
                        rs.getString("location_code"),
                        rs.getBytes("category_bytes"),
                        rs.getBytes("location_bytes"),
                        rs.getObject("updated_at", OffsetDateTime.class));
            }
        }
    }

    private static byte[] utf8(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void v9IsAppliedAsJavaMigration() throws Exception {
        try (Connection c = connect();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(
                        "SELECT type, success, checksum FROM flyway_schema_history WHERE version = '9'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("type")).isEqualTo("JDBC");
            assertThat(rs.getBoolean("success")).isTrue();
            // Java migration khong co checksum -> V9 phai coi la bat bien (xem comment trong V9).
            assertThat(rs.getObject("checksum")).isNull();
        }
    }

    @Test
    void matchedJob_getsBothCodes_legacyUntouched() throws Exception {
        JobRow row = load(JOB_BOTH_MATCH);
        assertThat(row.categoryCode()).isEqualTo("IT_SOFTWARE");
        assertThat(row.locationCode()).isEqualTo("HO_CHI_MINH");
        assertThat(row.categoryBytes()).isEqualTo(utf8("Công nghệ thông tin"));
        assertThat(row.locationBytes()).isEqualTo(utf8("TP. Hồ Chí Minh"));
    }

    @Test
    void oldProvinceNameAndLongDash_matchViaAliasAndNormalization() throws Exception {
        JobRow row = load(JOB_OLD_PROVINCE_DASH);
        assertThat(row.categoryCode()).isEqualTo("ACCOUNTING_AUDIT");
        assertThat(row.locationCode()).isEqualTo("HO_CHI_MINH");
        assertThat(row.categoryBytes()).isEqualTo(utf8("Kế toán – Kiểm toán"));
        assertThat(row.locationBytes()).isEqualTo(utf8("Bình Dương"));
    }

    @Test
    void unmatchedJob_keepsNullCodes_legacyUntouched() throws Exception {
        JobRow row = load(JOB_UNMATCHED);
        assertThat(row.categoryCode()).isNull();
        assertThat(row.locationCode()).isNull();
        assertThat(row.categoryBytes()).isEqualTo(utf8("Ngành lạ"));
        assertThat(row.locationBytes()).isEqualTo(utf8("Quận 1, HCM và Bình Dương"));
    }

    @Test
    void partiallyMatchedJob_getsOnlyMatchedCode() throws Exception {
        JobRow row = load(JOB_PARTIAL);
        assertThat(row.categoryCode()).isEqualTo("SALES");
        assertThat(row.locationCode()).isNull();
        assertThat(row.locationBytes()).isEqualTo(utf8("Nhiều nơi"));
    }

    @Test
    void softDeletedJob_isAlsoMigrated() throws Exception {
        JobRow row = load(JOB_SOFT_DELETED);
        assertThat(row.categoryCode()).isEqualTo("IT_SOFTWARE");
        assertThat(row.locationCode()).isEqualTo("HA_NOI");
        assertThat(row.categoryBytes()).isEqualTo(utf8("Trí tuệ nhân tạo"));
    }

    @Test
    void jobWithoutLegacyValues_staysEmpty() throws Exception {
        JobRow row = load(JOB_EMPTY);
        assertThat(row.categoryCode()).isNull();
        assertThat(row.locationCode()).isNull();
        assertThat(row.categoryBytes()).isNull();
        assertThat(row.locationBytes()).isNull();
    }

    @Test
    void v9DoesNotTouchUpdatedAt() throws Exception {
        for (UUID id : new UUID[] {JOB_BOTH_MATCH, JOB_OLD_PROVINCE_DASH, JOB_UNMATCHED, JOB_SOFT_DELETED}) {
            assertThat(load(id).updatedAt().toInstant()).isEqualTo(OLD_UPDATED_AT.toInstant());
        }
    }

    @Test
    void updatedAtTriggerIsReenabledAfterV9() throws Exception {
        UUID id = JOB_EMPTY;
        try (Connection c = connect();
                PreparedStatement ps = c.prepareStatement("UPDATE jobs SET title = 'Tin moi' WHERE id = ?")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
        assertThat(load(id).updatedAt().toInstant()).isAfter(OLD_UPDATED_AT.toInstant());
    }
}
