package com.recruitment.job;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// FR-U16 R-A1, R-A2 - tinh trang tin (FR-U08 R-D4) tach khoi ApplicationService de FR-U08 va trang "Tin da luu" dung
// chung. Dieu kien "tin mo" CHI nam o hai cau SQL co san (findOpenJobById / findOpenJobsByIdIn, cung dieu kien voi
// trang tin cong khai, ke ca han nop theo CURRENT_DATE cua DB) - KHONG tu viet dieu kien ngay o Java.
@Component
public class JobAvailabilityResolver {

    private final JobRepository jobRepository;

    public JobAvailabilityResolver(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    // FR-U08 - mot tin. Tin xoa mem tra ngay, khong hoi DB (hanh vi cu cua ApplicationService.availabilityOf).
    public JobAvailability of(Job job) {
        if (job.getDeletedAt() != null) {
            return JobAvailability.UNAVAILABLE;
        }
        return classify(job, jobRepository.findOpenJobById(job.getId()).isPresent());
    }

    // FR-U16 - ca tap trong MOT cau findOpenJobsByIdIn (khong N+1). Tap rong -> khong goi DB. Ket qua giu thu tu dau vao.
    public Map<UUID, JobAvailability> of(Collection<Job> jobs) {
        if (jobs.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = jobs.stream().map(Job::getId).distinct().toList();
        Set<UUID> openIds =
                jobRepository.findOpenJobsByIdIn(ids).stream().map(Job::getId).collect(Collectors.toSet());
        Map<UUID, JobAvailability> result = new LinkedHashMap<>();
        for (Job job : jobs) {
            result.put(job.getId(), classify(job, openIds.contains(job.getId())));
        }
        return result;
    }

    // R-D4 FR-U08 - xet theo thu tu, dung o dong dau tien khop. Thu tu xet chi viet MOT lan, hai method tren dung chung.
    static JobAvailability classify(Job job, boolean open) {
        if (job.getDeletedAt() != null) {
            return JobAvailability.UNAVAILABLE;
        }
        if (open) {
            return JobAvailability.OPEN;
        }
        return switch (job.getStatus()) {
            // status OPEN, chua xoa nhung khong nam trong tap "tin mo" -> chi con ly do qua han nop.
            case OPEN -> JobAvailability.EXPIRED;
            case PAUSED -> JobAvailability.PAUSED;
            case CLOSED -> JobAvailability.CLOSED;
            case DRAFT -> JobAvailability.UNAVAILABLE;
        };
    }
}
