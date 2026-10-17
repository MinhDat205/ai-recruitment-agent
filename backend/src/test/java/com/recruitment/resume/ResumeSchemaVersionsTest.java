package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// FR-U12 T1 (R-S1) - lop test MOI; hanh vi cu van khoa gian tiep o ResumeReparseEndpointTest/ResumeParsePromptTest (a14),
// hai lop do KHONG sua.
class ResumeSchemaVersionsTest {

    @Test
    void of_cvBuilderV1_isSchemaTwo() {
        assertThat(ResumeSchemaVersions.of("cv-builder-v1")).isEqualTo(2);
        assertThat(ResumeSchemaVersions.of(ResumeSchemaVersions.CV_BUILDER_VERSION)).isEqualTo(2);
    }

    @Test
    void of_resumeParseVersions_unchanged() {
        assertThat(ResumeSchemaVersions.of("resume-parse-v1")).isEqualTo(1);
        assertThat(ResumeSchemaVersions.of("resume-parse-v2")).isEqualTo(2);
    }

    @Test
    void of_unknownOrNull_isNull() {
        assertThat(ResumeSchemaVersions.of("cv-builder-v2")).isNull();
        assertThat(ResumeSchemaVersions.of("cv-builder")).isNull();
        assertThat(ResumeSchemaVersions.of("CV-BUILDER-V1")).isNull();
        assertThat(ResumeSchemaVersions.of("resume-parse-vx")).isNull();
        assertThat(ResumeSchemaVersions.of(null)).isNull();
    }

    @Test
    void isV1_cvBuilderV1_isFalse() {
        assertThat(ResumeSchemaVersions.isV1("cv-builder-v1")).isFalse();
        assertThat(ResumeSchemaVersions.isV1("resume-parse-v1")).isTrue();
    }

    @Test
    void isCvBuilder_onlyExactCvBuilderV1() {
        assertThat(ResumeSchemaVersions.isCvBuilder("cv-builder-v1")).isTrue();
        assertThat(ResumeSchemaVersions.isCvBuilder("cv-builder-v2")).isFalse();
        assertThat(ResumeSchemaVersions.isCvBuilder("cv-builder")).isFalse();
        assertThat(ResumeSchemaVersions.isCvBuilder("resume-parse-v2")).isFalse();
        assertThat(ResumeSchemaVersions.isCvBuilder(null)).isFalse();
    }

    @Test
    void constants_haveSpecValues() {
        assertThat(ResumeSchemaVersions.CV_BUILDER_MODEL).isEqualTo("cv-builder");
        assertThat(ResumeSchemaVersions.CV_BUILDER_VERSION).isEqualTo("cv-builder-v1");
    }
}
