package com.recruitment.catalog.dto;

import com.recruitment.catalog.CatalogEntry;
import com.recruitment.catalog.CatalogRegistry;
import java.util.List;

// CO Y chi co code + label: bi danh la chi tiet noi bo cua bo khop, khong tra ra ngoai (REQUIREMENT
// FR-C05 muc 4).
public record CatalogResponse(List<Item> industries, List<Item> provinces) {

    public record Item(String code, String label) {
    }

    public static CatalogResponse from(CatalogRegistry registry) {
        return new CatalogResponse(toItems(registry.industries()), toItems(registry.provinces()));
    }

    private static List<Item> toItems(List<CatalogEntry> entries) {
        return entries.stream().map(e -> new Item(e.code(), e.label())).toList();
    }
}
