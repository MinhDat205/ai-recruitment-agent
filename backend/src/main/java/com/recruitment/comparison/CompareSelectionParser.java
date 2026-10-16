package com.recruitment.comparison;

import com.recruitment.common.exception.InvalidCompareSelectionException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// FR-H14 R-S1, R-S2 - tu parse tham so ids (chuoi phan tach dau phay) thay vi @RequestParam List<UUID>: UUID sai se ra
// 400 mac dinh cua Spring, khong co ma/cau tieng Viet cua du an (a31). Thu tu kiem theo R-S7: dinh dang (R-S2) ->
// trung (R-S1, so SAU khi parse UUID nen khac hoa/thuong van la trung) -> so luong (R-S1). Giu nguyen thu tu ids
// (R-O1). Lop thuan, khong can Spring.
final class CompareSelectionParser {

    static final int MIN_COMPARE = 2;
    static final int MAX_COMPARE = 3;

    private CompareSelectionParser() {
    }

    static List<UUID> parse(String idsParam) {
        if (idsParam == null || idsParam.isBlank()) {
            throw InvalidCompareSelectionException.malformed();
        }
        // limit -1: giu phan tu rong o cuoi ("a,b," -> ["a", "b", ""]) de bat duoc dau phay thua (R-S2).
        String[] parts = idsParam.split(",", -1);
        List<UUID> ids = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.strip();
            if (trimmed.isEmpty()) {
                throw InvalidCompareSelectionException.malformed();
            }
            try {
                ids.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException e) {
                throw InvalidCompareSelectionException.malformed();
            }
        }

        Set<UUID> seen = new HashSet<>();
        for (UUID id : ids) {
            if (!seen.add(id)) {
                throw InvalidCompareSelectionException.duplicate();
            }
        }

        if (ids.size() < MIN_COMPARE || ids.size() > MAX_COMPARE) {
            throw InvalidCompareSelectionException.wrongCount();
        }
        return List.copyOf(ids);
    }
}
