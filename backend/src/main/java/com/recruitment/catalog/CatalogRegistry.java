package com.recruitment.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Danh muc nganh nghe/tinh thanh trong bo nho (FR-C05). Class THUAN, bat bien: dung tu danh sach
// CatalogEntry (luc chay: CatalogConfig nap MOT lan tu bang V8 qua CatalogJdbcLoader - cung loader
// V9 dung; test don vi: tu new voi fixture). Danh muc co dinh, chi doi qua migration moi - khong
// query DB moi request.
public final class CatalogRegistry {

    private final List<CatalogEntry> industries;
    private final List<CatalogEntry> provinces;
    private final Map<String, String> industryLabels;
    private final Map<String, String> provinceLabels;
    private final CatalogMatcher provinceMatcher;

    public CatalogRegistry(List<CatalogEntry> industries, List<CatalogEntry> provinces) {
        this.industries = List.copyOf(industries);
        this.provinces = List.copyOf(provinces);
        this.industryLabels = labelsByCode(industries);
        this.provinceLabels = labelsByCode(provinces);
        // Dung ngay luc khoi dong: du lieu danh muc sai (mot khoa tro hai ma) thi app phai do tu dau.
        CatalogMatcher.forIndustries(industries);
        this.provinceMatcher = CatalogMatcher.forProvinces(provinces);
    }

    private static Map<String, String> labelsByCode(List<CatalogEntry> entries) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (CatalogEntry entry : entries) {
            labels.put(entry.code(), entry.label());
        }
        return Map.copyOf(labels);
    }

    // Theo sort_order (thu tu CatalogJdbcLoader doc ra).
    public List<CatalogEntry> industries() {
        return industries;
    }

    public List<CatalogEntry> provinces() {
        return provinces;
    }

    public boolean isIndustry(String code) {
        return code != null && industryLabels.containsKey(code);
    }

    public boolean isProvince(String code) {
        return code != null && provinceLabels.containsKey(code);
    }

    // null neu code null hoac khong co trong danh muc.
    public String industryLabel(String code) {
        return code == null ? null : industryLabels.get(code);
    }

    public String provinceLabel(String code) {
        return code == null ? null : provinceLabels.get(code);
    }

    // R-C3 (khu vuc cua CV) - dung o dot sau.
    public String matchProvince(String text) {
        return provinceMatcher.match(text);
    }
}
