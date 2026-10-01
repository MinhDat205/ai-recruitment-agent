package com.recruitment.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.catalog.CatalogJdbcLoader.Catalog;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// REQUIREMENT FR-C05 R-M4 + muc 7.1 tren DU LIEU THAT cua V8 (khong phai fixture): DB khong tinh duoc
// khoa chuan hoa nen tinh nhat quan cua bang tra chi kiem duoc o day.
@Testcontainers
class CatalogSeedConsistencyTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    private static List<CatalogEntry> provinces;
    private static List<CatalogEntry> industries;

    // 63 tinh/thanh truoc sap xep 01/07/2025 -> ma don vi hien hanh (Nghi quyet 202/2025/QH15). Chep
    // doc lap voi V8 (khong doc tu DB) de test bat duoc loi go sai trong migration.
    private static final Map<String, String> OLD_63_PROVINCES = Map.ofEntries(
            Map.entry("Hà Nội", "HA_NOI"),
            Map.entry("Thừa Thiên Huế", "HUE"),
            Map.entry("Lai Châu", "LAI_CHAU"),
            Map.entry("Điện Biên", "DIEN_BIEN"),
            Map.entry("Sơn La", "SON_LA"),
            Map.entry("Lạng Sơn", "LANG_SON"),
            Map.entry("Quảng Ninh", "QUANG_NINH"),
            Map.entry("Thanh Hóa", "THANH_HOA"),
            Map.entry("Nghệ An", "NGHE_AN"),
            Map.entry("Hà Tĩnh", "HA_TINH"),
            Map.entry("Cao Bằng", "CAO_BANG"),
            Map.entry("Tuyên Quang", "TUYEN_QUANG"),
            Map.entry("Hà Giang", "TUYEN_QUANG"),
            Map.entry("Lào Cai", "LAO_CAI"),
            Map.entry("Yên Bái", "LAO_CAI"),
            Map.entry("Thái Nguyên", "THAI_NGUYEN"),
            Map.entry("Bắc Kạn", "THAI_NGUYEN"),
            Map.entry("Phú Thọ", "PHU_THO"),
            Map.entry("Vĩnh Phúc", "PHU_THO"),
            Map.entry("Hòa Bình", "PHU_THO"),
            Map.entry("Bắc Ninh", "BAC_NINH"),
            Map.entry("Bắc Giang", "BAC_NINH"),
            Map.entry("Hưng Yên", "HUNG_YEN"),
            Map.entry("Thái Bình", "HUNG_YEN"),
            Map.entry("Hải Phòng", "HAI_PHONG"),
            Map.entry("Hải Dương", "HAI_PHONG"),
            Map.entry("Ninh Bình", "NINH_BINH"),
            Map.entry("Hà Nam", "NINH_BINH"),
            Map.entry("Nam Định", "NINH_BINH"),
            Map.entry("Quảng Trị", "QUANG_TRI"),
            Map.entry("Quảng Bình", "QUANG_TRI"),
            Map.entry("Đà Nẵng", "DA_NANG"),
            Map.entry("Quảng Nam", "DA_NANG"),
            Map.entry("Quảng Ngãi", "QUANG_NGAI"),
            Map.entry("Kon Tum", "QUANG_NGAI"),
            Map.entry("Gia Lai", "GIA_LAI"),
            Map.entry("Bình Định", "GIA_LAI"),
            Map.entry("Khánh Hòa", "KHANH_HOA"),
            Map.entry("Ninh Thuận", "KHANH_HOA"),
            Map.entry("Lâm Đồng", "LAM_DONG"),
            Map.entry("Đắk Nông", "LAM_DONG"),
            Map.entry("Bình Thuận", "LAM_DONG"),
            Map.entry("Đắk Lắk", "DAK_LAK"),
            Map.entry("Phú Yên", "DAK_LAK"),
            Map.entry("TP. Hồ Chí Minh", "HO_CHI_MINH"),
            Map.entry("Bình Dương", "HO_CHI_MINH"),
            Map.entry("Bà Rịa - Vũng Tàu", "HO_CHI_MINH"),
            Map.entry("Đồng Nai", "DONG_NAI"),
            Map.entry("Bình Phước", "DONG_NAI"),
            Map.entry("Tây Ninh", "TAY_NINH"),
            Map.entry("Long An", "TAY_NINH"),
            Map.entry("Cần Thơ", "CAN_THO"),
            Map.entry("Sóc Trăng", "CAN_THO"),
            Map.entry("Hậu Giang", "CAN_THO"),
            Map.entry("Vĩnh Long", "VINH_LONG"),
            Map.entry("Bến Tre", "VINH_LONG"),
            Map.entry("Trà Vinh", "VINH_LONG"),
            Map.entry("Đồng Tháp", "DONG_THAP"),
            Map.entry("Tiền Giang", "DONG_THAP"),
            Map.entry("Cà Mau", "CA_MAU"),
            Map.entry("Bạc Liêu", "CA_MAU"),
            Map.entry("An Giang", "AN_GIANG"),
            Map.entry("Kiên Giang", "AN_GIANG"));

    @BeforeAll
    static void migrateAndLoad() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            provinces = CatalogJdbcLoader.load(c, Catalog.PROVINCES);
            industries = CatalogJdbcLoader.load(c, Catalog.INDUSTRIES);
        }
    }

    @Test
    void catalogSizes() {
        assertThat(provinces).hasSize(34);
        assertThat(industries).hasSize(24);
        assertThat(OLD_63_PROVINCES).hasSize(63);
    }

    @Test
    void noKeyPointsToTwoCodes_andNoRedundantAlias() {
        // forXxx() nem IllegalArgumentException neu mot khoa tro toi hai ma (R-M4 y 1).
        CatalogMatcher provinceMatcher = CatalogMatcher.forProvinces(provinces);
        CatalogMatcher industryMatcher = CatalogMatcher.forIndustries(industries);

        assertThat(provinceMatcher.redundantAliases()).isEmpty();
        assertThat(industryMatcher.redundantAliases()).isEmpty();
    }

    @Test
    void all63OldProvinceNamesMatchTheirCurrentUnit() {
        CatalogMatcher matcher = CatalogMatcher.forProvinces(provinces);
        OLD_63_PROVINCES.forEach((name, code) ->
                assertThat(matcher.match(name)).as(name).isEqualTo(code));
    }

    @Test
    void commonSpellingsAndSeedValuesMatch() {
        CatalogMatcher provinceMatcher = CatalogMatcher.forProvinces(provinces);
        CatalogMatcher industryMatcher = CatalogMatcher.forIndustries(industries);

        for (String hcm : List.of("TP.HCM", "TP HCM", "Hồ Chí Minh", "Thành phố Hồ Chí Minh", "HCM", "Sài Gòn",
                "Tỉnh Bình Dương", "Bà Rịa-Vũng Tàu", "Vũng Tàu")) {
            assertThat(provinceMatcher.match(hcm)).as(hcm).isEqualTo("HO_CHI_MINH");
        }
        assertThat(provinceMatcher.match("  hà   NỘI ")).isEqualTo("HA_NOI");
        assertThat(provinceMatcher.match("Khánh Hoà")).isEqualTo("KHANH_HOA");
        assertThat(provinceMatcher.match("Thừa Thiên-Huế")).isEqualTo("HUE");
        assertThat(provinceMatcher.match("Hà Nội, TP. HCM")).isNull();
        assertThat(provinceMatcher.match("Quận 1")).isNull();

        // Gia tri category cua seed demo va dev-seed (REQUIREMENT muc 4, Seed demo).
        assertThat(industryMatcher.match("Công nghệ thông tin")).isEqualTo("IT_SOFTWARE");
        assertThat(industryMatcher.match("Trí tuệ nhân tạo")).isEqualTo("IT_SOFTWARE");
        assertThat(industryMatcher.match("Kế toán - Kiểm toán")).isEqualTo("ACCOUNTING_AUDIT");
        assertThat(industryMatcher.match("Kế toán – Kiểm toán")).isEqualTo("ACCOUNTING_AUDIT");
        assertThat(industryMatcher.match("Kế toán-Kiểm toán")).isEqualTo("ACCOUNTING_AUDIT");
        assertThat(industryMatcher.match("Marketing - Truyền thông")).isEqualTo("MARKETING_COMMUNICATIONS");
        assertThat(industryMatcher.match("Kinh doanh - Bán hàng")).isEqualTo("SALES");
    }

    @Test
    void noNationwideOrRemoteEntryInProvinceCatalog() {
        CatalogMatcher matcher = CatalogMatcher.forProvinces(provinces);
        assertThat(matcher.match("Toàn quốc")).isNull();
        assertThat(matcher.match("Làm từ xa")).isNull();
        assertThat(matcher.match("Remote")).isNull();
    }
}
