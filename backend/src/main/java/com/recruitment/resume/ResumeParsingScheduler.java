package com.recruitment.resume;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Tat trong test qua app.resume-parsing.enabled=false (application-test.yml) - luc do bean nay
// KHONG ton tai trong context, tranh scheduler tu tick gay nhieu cac @SpringBootTest khac dung
// chung context cache. Test goi thang orchestrator.processOne(id), khong cho @Scheduled tick.
@Component
@ConditionalOnProperty(name = "app.resume-parsing.enabled", havingValue = "true", matchIfMissing = true)
public class ResumeParsingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ResumeParsingScheduler.class);

    private final ResumeRepository resumeRepository;
    private final ResumeParsingOrchestrator orchestrator;
    private final int batchSize;

    public ResumeParsingScheduler(
            ResumeRepository resumeRepository,
            ResumeParsingOrchestrator orchestrator,
            @Value("${app.resume-parsing.batch-size:10}") int batchSize) {
        this.resumeRepository = resumeRepository;
        this.orchestrator = orchestrator;
        this.batchSize = batchSize;
    }

    // KHONG @Transactional - xem CLAUDE.md muc 3c. orchestrator.processOne() tu xu ly loi cua
    // chinh no (markFailed ben trong), nhung van bat them o day de mot resume loi khong lam gian
    // doan viec quet cac resume con lai trong cung dot.
    @Scheduled(fixedDelayString = "${app.resume-parsing.poll-interval-ms:5000}")
    public void pollPendingResumes() {
        List<Resume> pending = resumeRepository.findByParseStatus(ParseStatus.PENDING, PageRequest.of(0, batchSize));
        for (Resume resume : pending) {
            try {
                orchestrator.processOne(resume.getId());
            } catch (RuntimeException e) {
                log.debug("Loi khong bat duoc trong vong quet: resumeId={}", resume.getId(), e);
            }
        }
    }
}
