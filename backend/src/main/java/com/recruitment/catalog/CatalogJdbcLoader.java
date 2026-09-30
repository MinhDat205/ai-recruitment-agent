package com.recruitment.catalog;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Doc danh muc (nhan + bi danh) tu bang V8 bang JDBC thuan - dung cho V9 (Java migration, chi co
// Connection cua Flyway Context, khong co Spring/JPA) va test nhat quan R-M4 tren du lieu that.
public final class CatalogJdbcLoader {

    public enum Catalog {
        INDUSTRIES("catalog_industries", "catalog_industry_aliases"),
        PROVINCES("catalog_provinces", "catalog_province_aliases");

        private final String table;
        private final String aliasTable;

        Catalog(String table, String aliasTable) {
            this.table = table;
            this.aliasTable = aliasTable;
        }
    }

    private CatalogJdbcLoader() {
    }

    // Ten bang lay tu enum (hang so), khong tu input, nen ghep chuoi SQL an toan.
    public static List<CatalogEntry> load(Connection connection, Catalog catalog) throws SQLException {
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT code, label FROM " + catalog.table + " ORDER BY sort_order")) {
            while (rs.next()) {
                labels.put(rs.getString("code"), rs.getString("label"));
                aliases.put(rs.getString("code"), new ArrayList<>());
            }
        }
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT code, alias_text FROM " + catalog.aliasTable + " ORDER BY alias_text")) {
            while (rs.next()) {
                aliases.get(rs.getString("code")).add(rs.getString("alias_text"));
            }
        }
        List<CatalogEntry> entries = new ArrayList<>();
        labels.forEach((code, label) -> entries.add(new CatalogEntry(code, label, aliases.get(code))));
        return entries;
    }
}
