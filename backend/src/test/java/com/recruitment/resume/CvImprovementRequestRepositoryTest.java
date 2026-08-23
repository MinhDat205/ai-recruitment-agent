package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// @Transactional o MUC METHOD cho MOI test trong file nay: can thiet cho claimForProcessing
// (@Modifying can transaction dang mo de thuc thi), can thiet de KHONG COMMIT VINH VIEN du lieu lam
// nhiem cac test khac trong CUNG mot Testcontainers dung chung (Spring tu ROLLBACK test
// @Transactional sau khi chay xong, bat ke test do co assertThrows hay khong), va can thiet cho test
// cuoi file (co y seed nhieu hang trong CUNG mot transaction de ep requested_at trung tuyet doi - xem
// comment tai test do). Cac test kiem vi pham UNIQUE (3, 4) dung saveAndFlush() de buoc rang buoc
// noi len NGAY tai thoi diem flush, khong can cho den commit - mau ApplicationService.apply() bat
// DataIntegrityViolationException cua uq_application_per_cycle trong pham vi mot @Transactional,
// tien le da co san trong du an.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class CvImprovementRequestRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private CvImprovementRequestRepository cvImprovementRequestRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID createResume() {
        User candidate = new User();
        candidate.setEmail("cand-" + UUID.randomUUID() + "@example.com");
        candidate.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        candidate.setRole(Role.CANDIDATE);
        candidate.setFullName("Ung Vien Test");
        candidate = userRepository.save(candidate);

        Resume resume = new Resume();
        resume.setCandidateId(candidate.getId());
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        return resumeRepository.save(resume).getId();
    }

    private UUID createRequest(UUID resumeId, CvImprovementRequestStatus status) {
        CvImprovementRequest request = new CvImprovementRequest();
        request.setResumeId(resumeId);
        request.setStatus(status);
        return cvImprovementRequestRepository.save(request).getId();
    }

    // ---- claimForProcessing ----

    @Test
    @Transactional
    void claimForProcessing_pendingRequest_returnsOneAndSetsRunning() {
        UUID resumeId = createResume();
        UUID requestId = createRequest(resumeId, CvImprovementRequestStatus.PENDING);

        int claimed = cvImprovementRequestRepository.claimForProcessing(requestId);

        assertThat(claimed).isEqualTo(1);
        entityManager.clear();
        CvImprovementRequest reloaded = cvImprovementRequestRepository.findById(requestId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CvImprovementRequestStatus.RUNNING);
    }

    @Test
    @Transactional
    void claimForProcessing_alreadyRunning_returnsZero() {
        UUID resumeId = createResume();
        UUID requestId = createRequest(resumeId, CvImprovementRequestStatus.RUNNING);

        int claimed = cvImprovementRequestRepository.claimForProcessing(requestId);

        assertThat(claimed).isEqualTo(0);
    }

    // ---- uq_cv_improvement_request_active ----

    @Test
    @Transactional
    void insertSecondActiveRequestForSameResume_violatesUniqueIndex() {
        UUID resumeId = createResume();
        CvImprovementRequest first = new CvImprovementRequest();
        first.setResumeId(resumeId);
        first.setStatus(CvImprovementRequestStatus.PENDING);
        cvImprovementRequestRepository.saveAndFlush(first);

        CvImprovementRequest second = new CvImprovementRequest();
        second.setResumeId(resumeId);
        second.setStatus(CvImprovementRequestStatus.PENDING);

        assertThrows(
                DataIntegrityViolationException.class, () -> cvImprovementRequestRepository.saveAndFlush(second));
    }

    // Chung minh menh de WHERE status IN ('PENDING','RUNNING') cua uq_cv_improvement_request_active
    // hoat dong dung: mot request da FAILED KHONG con nam trong pham vi unique index, nen ung vien
    // bam lai duoc. Thieu test nay se khong ai phat hien neu WHERE bi viet thieu hoac viet sai.
    @Test
    @Transactional
    void insertRequestAfterPreviousFailed_succeeds() {
        UUID resumeId = createResume();
        CvImprovementRequest failed = new CvImprovementRequest();
        failed.setResumeId(resumeId);
        failed.setStatus(CvImprovementRequestStatus.FAILED);
        cvImprovementRequestRepository.saveAndFlush(failed);

        CvImprovementRequest retry = new CvImprovementRequest();
        retry.setResumeId(resumeId);
        retry.setStatus(CvImprovementRequestStatus.PENDING);

        CvImprovementRequest saved = cvImprovementRequestRepository.saveAndFlush(retry);

        assertThat(saved.getId()).isNotNull();
    }

    // ---- findByStatusOrderByRequestedAtAscIdAsc (chong starvation) ----

    // @Transactional o MUC METHOD - CO Y seed ca 3 request trong CUNG mot transaction de ep
    // requested_at (DEFAULT now() cua Postgres) trung TUYET DOI cho ca 3 hang (CLAUDE.md muc 3c) -
    // day la kich ban TE NHAT thuc te co the xay ra (nhieu candidate bam xin goi y gan nhau trong
    // cung mot vong poll). Neu query thieu khoa cuoi IdAsc, Postgres tra ve thu tu KHONG XAC DINH
    // khi requested_at trung nhau, ket hop LIMIT cua Pageable se gay starvation (mot request PENDING
    // co the khong bao gio duoc chon khi hang doi dai hon batchSize). KHONG tu doan thu tu bang Java
    // (UUID.compareTo() cua Java va thu tu byte cua kieu uuid trong Postgres co the KHONG khop nhau
    // cho UUID ngau nhien) - lay "thu tu that" bang chinh cau query voi trang lon hon roi doi chieu
    // voi ket qua trang nho hon, goi lai lan hai de chung minh tinh xac dinh (determinism). Moi test
    // khac trong file nay deu @Transactional (tu rollback), nen bang nay luon rong truoc khi test nay
    // chay, bat ke thu tu JUnit chon chay truoc/sau.
    @Test
    @Transactional
    void findByStatusOrderByRequestedAtAscIdAsc_returnsOldestFirst() {
        UUID resumeId1 = createResume();
        UUID resumeId2 = createResume();
        UUID resumeId3 = createResume();
        createRequest(resumeId1, CvImprovementRequestStatus.PENDING);
        createRequest(resumeId2, CvImprovementRequestStatus.PENDING);
        createRequest(resumeId3, CvImprovementRequestStatus.PENDING);

        List<CvImprovementRequest> fullOrder = cvImprovementRequestRepository
                .findByStatusOrderByRequestedAtAscIdAsc(CvImprovementRequestStatus.PENDING, PageRequest.of(0, 10));
        assertThat(fullOrder).hasSize(3);
        List<UUID> expectedFirstTwo =
                fullOrder.stream().map(CvImprovementRequest::getId).toList().subList(0, 2);

        List<CvImprovementRequest> pageOne = cvImprovementRequestRepository
                .findByStatusOrderByRequestedAtAscIdAsc(CvImprovementRequestStatus.PENDING, PageRequest.of(0, 2));
        List<CvImprovementRequest> pageOneAgain = cvImprovementRequestRepository
                .findByStatusOrderByRequestedAtAscIdAsc(CvImprovementRequestStatus.PENDING, PageRequest.of(0, 2));

        assertThat(pageOne.stream().map(CvImprovementRequest::getId).toList()).isEqualTo(expectedFirstTwo);
        assertThat(pageOneAgain.stream().map(CvImprovementRequest::getId).toList()).isEqualTo(expectedFirstTwo);
    }
}
