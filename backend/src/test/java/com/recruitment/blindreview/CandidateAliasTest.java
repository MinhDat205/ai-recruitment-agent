package com.recruitment.blindreview;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

// FR-H16 T1 - CandidateAlias (R-N1): "Ung vien #" + 8 hex cuoi cua id don, viet hoa; ham thuan, khong Spring context.
class CandidateAliasTest {

    @Test
    void usesLastEightHexCharactersUppercased() {
        assertThat(CandidateAlias.of(UUID.fromString("e8000000-0000-0000-0000-000000000001")))
                .isEqualTo("Ứng viên #00000001");
        assertThat(CandidateAlias.of(UUID.fromString("0f1e2d3c-4b5a-6978-8a9b-acbdcedfabcd")))
                .isEqualTo("Ứng viên #CEDFABCD");
    }

    @Test
    void matchesFixedFormat() {
        assertThat(CandidateAlias.of(UUID.randomUUID())).matches("Ứng viên #[0-9A-F]{8}");
    }

    @Test
    void sameIdGivesSameAlias() {
        UUID id = UUID.randomUUID();
        assertThat(CandidateAlias.of(id)).isEqualTo(CandidateAlias.of(UUID.fromString(id.toString())));
    }

    @Test
    void differentLastEightCharactersGiveDifferentAlias() {
        UUID a = UUID.fromString("11111111-2222-3333-4444-5555aaaaaaaa");
        UUID b = UUID.fromString("11111111-2222-3333-4444-5555aaaaaaab");
        assertThat(CandidateAlias.of(a)).isNotEqualTo(CandidateAlias.of(b));
    }

    @Test
    void ignoresCharactersBeforeLastEight() {
        // Gioi han da biet (Q8): chi 8 ky tu cuoi tao nhan - hai id chi khac phan dau cho cung nhan.
        UUID a = UUID.fromString("aaaaaaaa-2222-3333-4444-555512345678");
        UUID b = UUID.fromString("bbbbbbbb-2222-3333-4444-555512345678");
        assertThat(CandidateAlias.of(a)).isEqualTo(CandidateAlias.of(b));
    }
}
