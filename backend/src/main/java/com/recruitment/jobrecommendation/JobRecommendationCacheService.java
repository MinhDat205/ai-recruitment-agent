package com.recruitment.jobrecommendation;

import com.recruitment.job.JobEmbeddingRepository;
import com.recruitment.job.JobMatchView;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeParsedDataRepository;
import com.recruitment.resume.ResumeRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// @Service THUONG voi @Transactional TRUC TIEP tren refreshOne, KHONG tach orchestrator/state-service
// nhu JobEmbeddingOrchestrator (Dot 3)/ResumeEmbeddingOrchestrator (Dot 4). Ly do tach o hai dot
// truoc la TRANH giu transaction quanh loi goi EmbeddingModel (co the mat vai giay, can pool ket
// noi) - o day KHONG co loi goi AI nao ca, toan bo refreshOne chi la vai cau SQL doc/ghi nhanh
// (khong goi EmbeddingModel, chi doc vector DA CO SAN tu job_embeddings/resume_parsed_data), giu
// chung trong MOT transaction ngan la an toan va don gian hon - dung tinh than "khong them
// abstraction ngoai yeu cau" (xem Plan Mode F1 muc E).
@Service
public class JobRecommendationCacheService {

    // So lieu thuc nghiem that (2 CV x 7 job, Postgres that, OpenAI text-embedding-3-small that,
    // chay ngoai phien implement): 3 job cung nganh IT roi vao [0.4307, 0.6219], job khac nganh gan
    // nhat (ke toan) roi vao [0.3553, 0.3658], marketing thap nhat [0.2429, 0.2542]. Khoang trong tu
    // nhien nam giua 0.43 va 0.38 - chon 0.40 cho bien an toan 0.045 so voi job khac nganh gan nhat.
    // Ghi chu: "Thuc tap sinh Tri tue nhan tao" (cung nhom IT nhung mo ta thien nghien cuu) bi loai
    // o 0.3405-0.3819 - hanh vi hop ly (CV mau thien backend), khong phai loi nguong.
    static final double MIN_SIMILARITY_SCORE = 0.40;

    // So job goi y toi da cho moi candidate - gioi han cache, endpoint doc khong phan trang them
    // (Plan Mode F1 muc F).
    static final int TOP_N = 10;

    private final ResumeRepository resumeRepository;
    private final ResumeParsedDataRepository resumeParsedDataRepository;
    private final JobEmbeddingRepository jobEmbeddingRepository;
    private final JobRecommendationRepository jobRecommendationRepository;

    public JobRecommendationCacheService(
            ResumeRepository resumeRepository,
            ResumeParsedDataRepository resumeParsedDataRepository,
            JobEmbeddingRepository jobEmbeddingRepository,
            JobRecommendationRepository jobRecommendationRepository) {
        this.resumeRepository = resumeRepository;
        this.resumeParsedDataRepository = resumeParsedDataRepository;
        this.jobEmbeddingRepository = jobEmbeddingRepository;
        this.jobRecommendationRepository = jobRecommendationRepository;
    }

    @Transactional
    public void refreshOne(UUID candidateId) {
        Resume resume = resumeRepository.findByCandidateIdAndIsPrimaryTrue(candidateId).orElse(null);
        if (resume == null) {
            return;
        }
        String queryVector =
                resumeParsedDataRepository.findEmbeddingTextByResumeId(resume.getId()).orElse(null);
        if (queryVector == null) {
            return;
        }

        List<JobMatchView> matches =
                jobEmbeddingRepository.findTopMatchingJobs(queryVector, MIN_SIMILARITY_SCORE, TOP_N);

        // Xoa-roi-chen (khong UPSERT tung dong): tu dong don job da rot khoi top-N hoac vua
        // dong/het han - khong can logic rieng "tim dong nao da loi thoi" (Plan Mode F1 muc E).
        //
        // flush() bat buoc giua delete va saveAll. deleteByCandidateId la derived delete method
        // (khong phai bulk JPQL DELETE) - Spring Data thuc hien bang SELECT roi
        // entityManager.remove() tung dong, DELETE SQL that bi HOAN toi luc flush/commit. saveAll
        // cung chi persist() vao persistence context, INSERT SQL cung hoan. Neu khong flush() o
        // day, ca hai deu don toi luc transaction commit - Hibernate sap xep thao tac theo thu tu
        // co dinh (INSERT truoc DELETE), nen INSERT ban ghi moi chay TRUOC khi DELETE ban ghi cu
        // thuc su xoa, va neu candidate nay da co cache tu lan refresh truoc (dung kich ban
        // scheduler chay dinh ky - Plan Mode F1 muc E), ban ghi moi trung uq_reco voi ban ghi cu
        // chua kip xoa. Loi nay KHONG lo o lan refreshOne dau tien cho mot candidate (chua co gi
        // de xoa) - chi lo tu lan refresh thu hai tro di, va chi lo khi chay full suite (test
        // rieng khong du du lieu cache cu de trigger).
        jobRecommendationRepository.deleteByCandidateId(candidateId);
        jobRecommendationRepository.flush();
        List<JobRecommendation> recommendations =
                matches.stream().map(match -> toRecommendation(candidateId, resume.getId(), match)).toList();
        jobRecommendationRepository.saveAll(recommendations);
    }

    private JobRecommendation toRecommendation(UUID candidateId, UUID resumeId, JobMatchView match) {
        JobRecommendation recommendation = new JobRecommendation();
        recommendation.setCandidateId(candidateId);
        recommendation.setJobId(match.getJobId());
        recommendation.setResumeId(resumeId);
        recommendation.setSimilarityScore(match.getSimilarityScore());
        return recommendation;
    }
}
