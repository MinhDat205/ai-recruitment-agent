package com.recruitment.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// FR-U16 T19 (R-A2) - resolver theo lo goi findOpenJobsByIdIn DUNG MOT lan, tap rong khong goi DB; thu tu xet cua
// FR-U08 R-D4 giu nguyen o ca hai method. Dieu kien "tin mo" theo CURRENT_DATE do T6 cua FR-U08 kiem tren Postgres that.
@ExtendWith(MockitoExtension.class)
class JobAvailabilityResolverTest {

    @Mock
    private JobRepository jobRepository;

    @Test
    void ofCollection_emptyInput_doesNotQueryRepository() {
        JobAvailabilityResolver resolver = new JobAvailabilityResolver(jobRepository);

        assertThat(resolver.of(List.of())).isEmpty();
        verifyNoInteractions(jobRepository);
    }

    @Test
    void ofCollection_fiveJobs_queriesOpenJobsOnceAndClassifiesEach() {
        Job open = job(JobStatus.OPEN, null);
        Job expired = job(JobStatus.OPEN, null);
        Job paused = job(JobStatus.PAUSED, null);
        Job closedDeleted = job(JobStatus.CLOSED, Instant.now());
        Job draft = job(JobStatus.DRAFT, null);
        when(jobRepository.findOpenJobsByIdIn(anyList())).thenReturn(List.of(open));
        JobAvailabilityResolver resolver = new JobAvailabilityResolver(jobRepository);

        Map<UUID, JobAvailability> result = resolver.of(List.of(open, expired, paused, closedDeleted, draft));

        verify(jobRepository, times(1)).findOpenJobsByIdIn(anyList());
        assertThat(result)
                .containsExactly(
                        Map.entry(open.getId(), JobAvailability.OPEN),
                        Map.entry(expired.getId(), JobAvailability.EXPIRED),
                        Map.entry(paused.getId(), JobAvailability.PAUSED),
                        Map.entry(closedDeleted.getId(), JobAvailability.UNAVAILABLE),
                        Map.entry(draft.getId(), JobAvailability.UNAVAILABLE));
    }

    @Test
    void ofSingle_deletedJob_returnsUnavailableWithoutQuery() {
        JobAvailabilityResolver resolver = new JobAvailabilityResolver(jobRepository);

        assertThat(resolver.of(job(JobStatus.OPEN, Instant.now()))).isEqualTo(JobAvailability.UNAVAILABLE);
        verifyNoInteractions(jobRepository);
    }

    @Test
    void ofSingle_openAndNotOpen_followsFrU08Order() {
        Job open = job(JobStatus.OPEN, null);
        Job expired = job(JobStatus.OPEN, null);
        Job closed = job(JobStatus.CLOSED, null);
        when(jobRepository.findOpenJobById(open.getId())).thenReturn(Optional.of(open));
        when(jobRepository.findOpenJobById(expired.getId())).thenReturn(Optional.empty());
        when(jobRepository.findOpenJobById(closed.getId())).thenReturn(Optional.empty());
        JobAvailabilityResolver resolver = new JobAvailabilityResolver(jobRepository);

        assertThat(resolver.of(open)).isEqualTo(JobAvailability.OPEN);
        assertThat(resolver.of(expired)).isEqualTo(JobAvailability.EXPIRED);
        assertThat(resolver.of(closed)).isEqualTo(JobAvailability.CLOSED);
    }

    private static Job job(JobStatus status, Instant deletedAt) {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setStatus(status);
        job.setDeletedAt(deletedAt);
        return job;
    }
}
