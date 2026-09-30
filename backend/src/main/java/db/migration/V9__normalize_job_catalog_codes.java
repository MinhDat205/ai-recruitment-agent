package db.migration;

import com.recruitment.catalog.CatalogJdbcLoader;
import com.recruitment.catalog.CatalogJdbcLoader.Catalog;
import com.recruitment.catalog.CatalogMatcher;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// FR-C05 R-D1..R-D3 - chuyen jobs.category/jobs.location cu sang category_code/location_code, DUNG
// MOT LAN. Java migration (khong phai SQL) vi bo khop chuoi chi duoc co MOT ban cai dat
// (CatalogMatcher, R-M5) - viet lai bang unaccent/ILIKE trong SQL se lech voi ban Java.
//
// - Flyway tu quet class nay trong package db.migration (flyway.locations = classpath:db/migration);
//   KHONG dang ky lam Spring bean.
// - Doc nhan/bi danh tu bang V8 qua JDBC cua Context - KHONG khai lai danh muc trong code nay.
// - Ap cho MOI job, ke ca da xoa mem. Khong doc/ghi cot cu ngoai viec doc de khop.
// - Chi log so luong, khong log noi dung.
//
// BAT BIEN: Java migration KHONG co checksum (BaseJavaMigration.getChecksum() tra null) - Flyway
// khong phat hien duoc neu class nay bi sua sau khi da ap. Khong sua file nay; can doi du lieu thi
// viet migration moi.
public class V9__normalize_job_catalog_codes extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V9__normalize_job_catalog_codes.class);

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        CatalogMatcher industries =
                CatalogMatcher.forIndustries(CatalogJdbcLoader.load(connection, Catalog.INDUSTRIES));
        CatalogMatcher provinces =
                CatalogMatcher.forProvinces(CatalogJdbcLoader.load(connection, Catalog.PROVINCES));

        int total = 0;
        int categoryMatched = 0;
        int categoryUnmatched = 0;
        int locationMatched = 0;
        int locationUnmatched = 0;

        // Tat trigger updated_at trong transaction cua migration (Flyway boc Java migration trong
        // transaction, canExecuteInTransaction mac dinh true): day la chuyen du lieu cua he thong,
        // khong phai HR sua tin - khong duoc lam moi Job hien "vua cap nhat" cho HR.
        try (Statement ddl = connection.createStatement()) {
            ddl.execute("ALTER TABLE jobs DISABLE TRIGGER trg_jobs_updated_at");
        }

        try (Statement select = connection.createStatement();
                ResultSet rs = select.executeQuery("SELECT id, category, location FROM jobs");
                PreparedStatement update = connection.prepareStatement(
                        "UPDATE jobs SET category_code = ?, location_code = ? WHERE id = ?")) {
            while (rs.next()) {
                total++;
                String category = rs.getString("category");
                String location = rs.getString("location");
                String categoryCode = industries.match(category);
                String locationCode = provinces.match(location);

                if (category != null) {
                    if (categoryCode != null) {
                        categoryMatched++;
                    } else {
                        categoryUnmatched++;
                    }
                }
                if (location != null) {
                    if (locationCode != null) {
                        locationMatched++;
                    } else {
                        locationUnmatched++;
                    }
                }
                if (categoryCode == null && locationCode == null) {
                    continue;
                }
                update.setString(1, categoryCode);
                update.setString(2, locationCode);
                update.setObject(3, rs.getObject("id"));
                update.addBatch();
            }
            update.executeBatch();
        }

        try (Statement ddl = connection.createStatement()) {
            ddl.execute("ALTER TABLE jobs ENABLE TRIGGER trg_jobs_updated_at");
        }

        log.info(
                "V9: {} job; nganh nghe khop {}, truot {}; tinh/thanh khop {}, truot {}",
                total,
                categoryMatched,
                categoryUnmatched,
                locationMatched,
                locationUnmatched);
    }
}
