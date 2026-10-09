package com.recruitment.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// FR-C06 T17 - test don vi MessageExcerpt (R-N2): nguong 120 code point (119 / 120 / 121), gop khoang trang,
// khong cat doi cap surrogate.
class MessageExcerptTest {

    private static final String EMOJI_1 = "😀"; // U+1F600, ngoai BMP (2 char, 1 code point)
    private static final String EMOJI_2 = "🚀"; // U+1F680

    @Test
    void textOf119And120CodePoints_isKeptAsIs() {
        assertThat(MessageExcerpt.of("a".repeat(119))).isEqualTo("a".repeat(119));
        assertThat(MessageExcerpt.of("a".repeat(120))).isEqualTo("a".repeat(120));
    }

    @Test
    void textOf121CodePoints_isCutTo120PlusEllipsis() {
        assertThat(MessageExcerpt.of("a".repeat(121))).isEqualTo("a".repeat(120) + "…");
    }

    @Test
    void whitespaceRunsCollapseToOneSpace_andEndsAreStripped() {
        assertThat(MessageExcerpt.of("a \n\n  b")).isEqualTo("a b");
        assertThat(MessageExcerpt.of("\r\n  Chào bạn,\t\thồ sơ đang xử lý.  \n")).isEqualTo("Chào bạn, hồ sơ đang xử lý.");
    }

    @Test
    void nullOrWhitespaceOnly_hasNoExcerpt() {
        assertThat(MessageExcerpt.of(null)).isNull();
        assertThat(MessageExcerpt.of("")).isNull();
        assertThat(MessageExcerpt.of("  \n\t ")).isNull();
    }

    @Test
    void cutCountsCodePoints_andNeverSplitsSurrogatePair() {
        // 119 chu cai + 2 emoji = 121 code point (123 char) -> giu 119 chu + emoji thu nhat NGUYEN VEN + "…".
        String text = "a".repeat(119) + EMOJI_1 + EMOJI_2;
        assertThat(MessageExcerpt.of(text)).isEqualTo("a".repeat(119) + EMOJI_1 + "…");

        // 118 chu cai + 2 emoji = 120 code point (122 char > 120 char) -> KHONG cat, vi dem theo code point.
        String exactly120 = "a".repeat(118) + EMOJI_1 + EMOJI_2;
        assertThat(MessageExcerpt.of(exactly120)).isEqualTo(exactly120);
    }

    @Test
    void lengthIsMeasuredAfterCollapsingWhitespace() {
        // 60 + 60 chu cai cach nhau boi nhieu xuong dong -> sau khi gop: 121 code point -> cat.
        String text = "a".repeat(60) + "\n\n\n" + "b".repeat(60);
        assertThat(MessageExcerpt.of(text)).isEqualTo("a".repeat(60) + " " + "b".repeat(59) + "…");
    }
}
