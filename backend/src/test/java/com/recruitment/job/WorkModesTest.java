package com.recruitment.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// FR-U13 T4b (O3) - hang so hinh thuc lam viec dung chung. Ca U07 co san list_invalidWorkMode_returns400
// (JobPublicIntegrationTest, gia tri KHONG_HOP_LE) giu nguyen, khong sua (L1); "OFFICE" la ca moi o day.
class WorkModesTest {

    @Test
    void codes_areExactlyThreeWorkModes() {
        assertThat(WorkModes.CODES).containsExactlyInAnyOrder("ONSITE", "HYBRID", "REMOTE");
    }

    @Test
    void labels_areExactlyTheThreeFrontendStrings() {
        assertThat(WorkModes.LABELS).hasSize(3);
        assertThat(WorkModes.LABELS).containsEntry("ONSITE", "Tại văn phòng");
        assertThat(WorkModes.LABELS).containsEntry("HYBRID", "Kết hợp");
        assertThat(WorkModes.LABELS).containsEntry("REMOTE", "Từ xa");
    }

    @Test
    void labels_keysMatchCodes() {
        assertThat(WorkModes.LABELS.keySet()).isEqualTo(WorkModes.CODES);
    }

    @Test
    void codes_doNotContainOffice_orLowercaseOrUnknownCodes() {
        assertThat(WorkModes.CODES).doesNotContain("OFFICE", "remote", "KHONG_HOP_LE", "");
        assertThat(WorkModes.LABELS).doesNotContainKey("OFFICE");
    }
}
