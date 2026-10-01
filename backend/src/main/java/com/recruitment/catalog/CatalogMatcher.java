package com.recruitment.catalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

// Bo khop chuoi -> ma danh muc (FR-C05 R-M3, R-M4, R-M5). Class Java THUAN, khong Spring: V9 (Java
// migration, khong co Spring context) va service luc chay dung CHUNG class nay.
//
// Khop = tra khoa da chuan hoa trong bang {nhan U bi danh -> ma}. Truot -> null. KHONG so gan dung,
// khong LIKE '%...%', khong tach theo dau phay: "Quan 1, Ho Chi Minh" -> null la DUNG, khong phai loi.
//
// Nhan va bi danh di qua DUNG ham chuan hoa cua dau vao (R-M3) - voi tinh/thanh la ca R-M2, nen nhan
// "TP. Ho Chi Minh" cho khoa "ho chi minh" va khop duoc "Thanh pho Ho Chi Minh".
public final class CatalogMatcher {

    private final UnaryOperator<String> keyFunction;
    private final Map<String, String> codeByKey;
    private final List<String> redundantAliases;

    private CatalogMatcher(List<CatalogEntry> entries, UnaryOperator<String> keyFunction) {
        this.keyFunction = keyFunction;
        Map<String, String> map = new HashMap<>();
        List<String> redundant = new ArrayList<>();
        // Nhan truoc, bi danh sau: bi danh trung khoa voi nhan CUNG ma bi bao la "thua" (khong phai
        // nguoc lai).
        for (CatalogEntry entry : entries) {
            put(map, keyFunction.apply(entry.label()), entry.code(), entry.label(), null);
        }
        for (CatalogEntry entry : entries) {
            for (String alias : entry.aliases()) {
                put(map, keyFunction.apply(alias), entry.code(), alias, redundant);
            }
        }
        this.codeByKey = Map.copyOf(map);
        this.redundantAliases = List.copyOf(redundant);
    }

    public static CatalogMatcher forIndustries(List<CatalogEntry> entries) {
        return new CatalogMatcher(entries, CatalogTextNormalizer::normalize);
    }

    public static CatalogMatcher forProvinces(List<CatalogEntry> entries) {
        return new CatalogMatcher(entries, CatalogTextNormalizer::normalizeProvince);
    }

    // R-M4 y 1: mot khoa tro toi HAI ma khac nhau -> nem ngay luc dung bang tra. Du lieu danh muc sai
    // thi V9/khoi dong phai do, khong duoc am tham chon mot ma.
    private static void put(Map<String, String> map, String key, String code, String source, List<String> redundant) {
        if (key == null) {
            throw new IllegalArgumentException("Nhan/bi danh rong sau chuan hoa: ma " + code);
        }
        String existing = map.putIfAbsent(key, code);
        if (existing == null) {
            return;
        }
        if (!existing.equals(code)) {
            throw new IllegalArgumentException(
                    "Khoa '" + key + "' tro toi hai ma khac nhau: " + existing + " va " + code + " (tu '" + source + "')");
        }
        // R-M4 y 2: trung khoa CUNG ma. Nhan trung nhan (redundant == null) khong the xay ra vi nhan
        // UNIQUE o DB, nen chi ghi nhan bi danh thua.
        if (redundant != null) {
            redundant.add(source);
        }
    }

    // Tra ma cho dau vao, hoac null neu khong khop (ke ca dau vao null/rong).
    public String match(String input) {
        String key = keyFunction.apply(input);
        return key == null ? null : codeByKey.get(key);
    }

    // Bi danh thua theo R-M4 (trung khoa voi nhan/bi danh khac cung ma) - test doi rong de bang gon.
    public List<String> redundantAliases() {
        return redundantAliases;
    }
}
