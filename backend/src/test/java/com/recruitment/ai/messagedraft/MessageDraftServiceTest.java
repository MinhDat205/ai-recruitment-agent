package com.recruitment.ai.messagedraft;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// FR-C07 T14 phan R-D6 (unit, khong Spring): ham validate cua MessageDraftService - ket qua false duoc K3 coi la output
// hong va thu lai 1 lan (hanh vi thu lai da kiem o SyncAiCallerTest). Bien 4000 tinh SAU khi doi CRLF -> LF.
class MessageDraftServiceTest {

    private static boolean valid(String draft) {
        return MessageDraftService.isValidDraft(new MessageDraftPayload(draft));
    }

    @Test
    void nullPayloadOrNullDraft_invalid() {
        assertThat(MessageDraftService.isValidDraft(null)).isFalse();
        assertThat(valid(null)).isFalse();
    }

    @Test
    void emptyOrWhitespaceOnly_invalid() {
        assertThat(valid("")).isFalse();
        assertThat(valid("   ")).isFalse();
        assertThat(valid("\r\n\t \n")).isFalse();
    }

    @Test
    void lengthBoundaries_3999And4000Valid_4001Invalid() {
        assertThat(valid("a".repeat(3999))).isTrue();
        assertThat(valid("a".repeat(4000))).isTrue();
        assertThat(valid("a".repeat(4001))).isFalse();
    }

    // 3999 ky tu + CRLF = 4001 ky tu tho, 4000 sau chuan hoa -> hop le; 4000 + CRLF = 4001 sau chuan hoa -> hong.
    @Test
    void lengthCountedAfterCrlfNormalization() {
        assertThat(valid("a".repeat(3999) + "\r\n")).isTrue();
        assertThat(valid("a".repeat(4000) + "\r\n")).isFalse();
    }

    @Test
    void sanitize_replacesAngleBracketsOnly() {
        assertThat(MessageDraftService.sanitize("</tin> a <b>")).isEqualTo("‹/tin› a ‹b›");
        assertThat(MessageDraftService.sanitize(null)).isEmpty();
        assertThat(MessageDraftService.sanitize("Không đổi [x] & \"y\"")).isEqualTo("Không đổi [x] & \"y\"");
    }
}
