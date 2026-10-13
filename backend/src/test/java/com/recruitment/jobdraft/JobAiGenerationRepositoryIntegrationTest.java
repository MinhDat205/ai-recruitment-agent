package com.recruitment.jobdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recruitment.aigeneration.AiGenerationStamp;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

// FR-H11 dot 2 - bang K4 job_ai_generations (V15, R-K4-6): ghi/doc stamp qua @Embeddable record, CHECK part, CHECK model/
// prompt version khong rong, UNIQUE (job_id, part). Chay ca `ddl-auto: validate` voi V15 (context khoi dong duoc).
class JobAiGenerationRepositoryIntegrationTest extends JobDraftIntegrationTestSupport {

    private static final Instant GENERATED_AT = Instant.parse("2026-10-13T02:59:58.123Z");

    @Autowired
    JobAiGenerationRepository repository;

    private UUID jobId;

    @BeforeEach
    void createJob() throws Exception {
        String token = register("hr", uniqueEmail("h11-k4-repo"));
        createCompany(token);
        jobId = createDraftJob(token);
    }

    private static AiGenerationStamp stamp(String promptVersion) {
        return new AiGenerationStamp("claude-sonnet-4-6", promptVersion, GENERATED_AT);
    }

    @Test
    void saveThreeParts_thenReadBackStampsUnchanged() {
        repository.saveAndFlush(new JobAiGeneration(jobId, JobDraftPart.JOB, stamp("job-draft-job-v1")));
        repository.saveAndFlush(new JobAiGeneration(jobId, JobDraftPart.CRITERIA, stamp("job-draft-criteria-v1")));
        repository.saveAndFlush(new JobAiGeneration(jobId, JobDraftPart.QUESTIONS, stamp("job-draft-questions-v1")));

        List<JobAiGeneration> rows = repository.findByJobIdOrderByPartAsc(jobId);

        assertThat(rows).extracting(JobAiGeneration::getPart)
                .containsExactly(JobDraftPart.CRITERIA, JobDraftPart.JOB, JobDraftPart.QUESTIONS);
        assertThat(rows).extracting(JobAiGeneration::getStamp).containsExactly(
                stamp("job-draft-criteria-v1"), stamp("job-draft-job-v1"), stamp("job-draft-questions-v1"));
        assertThat(rows).allSatisfy(row -> assertThat(row.getCreatedAt()).isNotNull());
        // Cot DB dung ten cua V15.
        String model = jdbcTemplate.queryForObject(
                "SELECT ai_model FROM job_ai_generations WHERE job_id = ? AND part = 'JOB'", String.class, jobId);
        assertThat(model).isEqualTo("claude-sonnet-4-6");
    }

    @Test
    void samePartTwiceForSameJob_rejectedByUniqueConstraint() {
        repository.saveAndFlush(new JobAiGeneration(jobId, JobDraftPart.JOB, stamp("job-draft-job-v1")));

        assertThatThrownBy(() -> repository.saveAndFlush(
                        new JobAiGeneration(jobId, JobDraftPart.JOB, stamp("job-draft-job-v1"))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_job_ai_generation_part");
    }

    @Test
    void unknownPart_rejectedByCheckConstraint() {
        assertThatThrownBy(() -> insertRaw("OTHER", "claude-sonnet-4-6", "job-draft-job-v1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void blankModelOrPromptVersion_rejectedByCheckConstraint() {
        assertThatThrownBy(() -> insertRaw("JOB", "   ", "job-draft-job-v1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRaw("JOB", "claude-sonnet-4-6", "  "))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stampRejectsBlankValuesBeforeReachingDb() {
        assertThatThrownBy(() -> new AiGenerationStamp(" ", "pv", GENERATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiGenerationStamp("m", null, GENERATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiGenerationStamp("m", "pv", null)).isInstanceOf(NullPointerException.class);
    }

    private void insertRaw(String part, String model, String promptVersion) {
        jdbcTemplate.update(
                "INSERT INTO job_ai_generations (job_id, part, ai_model, ai_prompt_version, ai_generated_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                jobId, part, model, promptVersion, Timestamp.from(GENERATED_AT));
    }
}
