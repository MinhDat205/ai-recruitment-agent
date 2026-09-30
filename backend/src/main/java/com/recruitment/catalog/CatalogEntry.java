package com.recruitment.catalog;

import java.util.List;

// Mot muc danh muc dua vao CatalogMatcher: ma on dinh, nhan hien thi, cac bi danh. Record thuan, khong
// phai entity - V9 dung JDBC tu dung danh sach nay tu bang, service luc chay dung tu repository.
public record CatalogEntry(String code, String label, List<String> aliases) {

    public CatalogEntry {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }
}
