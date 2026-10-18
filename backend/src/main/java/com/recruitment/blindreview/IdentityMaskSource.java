package com.recruitment.blindreview;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

// FR-H16 R-M1 - nap gia tri danh tinh cua tap don: users.full_name/email/phone cua ung vien + contact.fullName/email/
// phone/address/linkedin trong CV da trich xuat cua don. Khong them nguon khac. Hai cau cho ca tap (khong N+1); tap rong
// thi khong chay cau nao. Noi goi chi truyen id cua don DANG AN DANH (lay tu BlindReviewResolver).
@Component
public class IdentityMaskSource {

    private final JobBlindReviewChangeRepository repository;

    public IdentityMaskSource(JobBlindReviewChangeRepository repository) {
        this.repository = repository;
    }

    public IdentityMasker load(UUID applicationId) {
        return load(Set.of(applicationId)).get(applicationId);
    }

    // Moi id trong tap deu co trong ket qua (id khong co gia tri nao -> IdentityMasker.none()).
    public Map<UUID, IdentityMasker> load(Collection<UUID> applicationIds) {
        if (applicationIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> ids = Set.copyOf(applicationIds);
        Map<UUID, List<String>> values = new HashMap<>();
        for (AccountIdentityRow row : repository.findAccountIdentities(ids)) {
            List<String> list = values.computeIfAbsent(row.getApplicationId(), k -> new ArrayList<>());
            list.add(row.getFullName());
            list.add(row.getEmail());
            list.add(row.getPhone());
        }
        for (ParsedContactRow row : repository.findParsedContacts(ids)) {
            List<String> list = values.computeIfAbsent(row.getApplicationId(), k -> new ArrayList<>());
            list.add(row.getFullName());
            list.add(row.getEmail());
            list.add(row.getPhone());
            list.add(row.getAddress());
            list.add(row.getLinkedin());
        }
        Map<UUID, IdentityMasker> result = new HashMap<>();
        for (UUID id : ids) {
            result.put(id, IdentityMasker.of(values.getOrDefault(id, List.of())));
        }
        return Map.copyOf(result);
    }
}
