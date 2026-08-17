package com.recruitment.scoring;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScoringRunRepository extends JpaRepository<ScoringRun, UUID> {

    // Dung boi ScoringRunScheduler (Dot 4) de quet cac luot cham cho xu ly - mau
    // ResumeRepository.findByParseStatus.
    List<ScoringRun> findByStatus(ScoringRunStatus status, Pageable pageable);

    // GET /api/hr/applications/{id}/scoring-runs (Dot 5) - lich su cac luot cham cua MOT don, moi
    // nhat truoc, khop dung thu tu ma idx_scoring_app(application_id, created_at DESC) da danh san.
    List<ScoringRun> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    // GET /api/hr/jobs/{jobId}/applications (Dot 5) - lay luot cham GAN NHAT cho MOI don trong
    // applicationIds bang MOT query duy nhat, tranh N+1 (goi rieng findByApplicationIdOrderBy...
    // roi .get(0) cho tung don trong vong lap se la N+1 khi danh sach dai). DISTINCT ON la cu phap
    // rieng cua Postgres, khop dung idx_scoring_app(application_id, created_at DESC) da co san -
    // moi application_id chi giu lai dong co created_at lon nhat.
    @Query(
            value =
                    """
                    SELECT DISTINCT ON (application_id)
                        application_id AS applicationId, id AS id, status AS status, finished_at AS finishedAt
                    FROM scoring_runs
                    WHERE application_id IN (:applicationIds)
                    ORDER BY application_id, created_at DESC
                    """,
            nativeQuery = true)
    List<LatestScoringRunView> findLatestByApplicationIdIn(@Param("applicationIds") Collection<UUID> applicationIds);

    // Dieu kien tien quyet #4 (Dot 2 ke hoach D2): dang co lot cham "thuc su dang chay" cho don
    // nay - PENDING (chua claim) hoac RUNNING ma finished_at con NULL (dang xu ly, chua cham xong
    // toan bo tieu chi). Mot lot RUNNING da co finished_at (cho D3 tong hop, xem Q1) KHONG tinh la
    // dang chay - HR duoc phep tao lot cham moi cho cung don (cau tra loi (i) cua Q1).
    boolean existsByApplicationIdAndStatusInAndFinishedAtIsNull(
            UUID applicationId, Collection<ScoringRunStatus> statuses);

    // Claim bang UPDATE co dieu kien, kiem tra rowcount ben ngoai (xem CLAUDE.md muc 3c) - KHONG
    // dung SELECT FOR UPDATE SKIP LOCKED vi no giu transaction mo trong luc cho LLM.
    // clearAutomatically = true bat buoc - cung ly do voi ResumeRepository.claimForProcessing.
    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE scoring_runs SET status = 'RUNNING', started_at = now() "
                    + "WHERE id = :id AND status = 'PENDING'",
            nativeQuery = true)
    int claimForProcessing(@Param("id") UUID id);

    // D3 (FR-H05, Dot 3 se dung): nhat cac luot da CHAM XONG toan bo tieu chi (D2 xong, xem
    // CLAUDE.md muc 2b) nhung CHUA duoc tong hop - dung dung dieu kien da chot trong ke hoach D3:
    // status='RUNNING' VA finished_at khac NULL VA total_score con NULL. Luot FAILED khong bao gio
    // duoc dong toi du co bao nhieu dong criterion_scores (khong nam trong dieu kien nay - status
    // khac RUNNING).
    List<ScoringRun> findByStatusAndFinishedAtIsNotNullAndTotalScoreIsNull(ScoringRunStatus status, Pageable pageable);

    // Ghi ket qua tong hop cua D3 (FR-H05): MOT UPDATE co dieu kien vua la buoc GHI vua la CHOT
    // CHAN duy nhat chong hai nhip poll chong nhau (Q3, ke hoach D3) - KHONG co buoc claim rieng
    // truoc do nhu claimForProcessing o tren. Ly do khac D2: claim cua D2 bao ve mot loi goi LLM
    // co the mat toi vai chuc giay TRUOC khi ghi (tranh goi LLM hai lan cho cung mot tieu chi); D3
    // khong goi gi cham ca - toan bo phep tinh la Java thuan, xac dinh (cung input tu
    // criterion_scores/rubric_snapshot da commit luon ra CUNG mot total_score, khong tac dung
    // phu). Neu hai nhip poll thuc su chong nhau (ly thuyet, vd @Scheduled doi sang thuc thi song
    // song trong tuong lai, hoac nhieu instance ung dung), dieu kien "total_score IS NULL" trong
    // WHERE dam bao chi UPDATE DAU TIEN thuc su ghi (rowcount=1) - UPDATE con lai la no-op an toan
    // (rowcount=0, khong ghi de, khong loi, vi ca hai deu se tinh ra CUNG mot gia tri neu co chay).
    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE scoring_runs SET status = 'DONE', total_score = :totalScore "
                    + "WHERE id = :id AND status = 'RUNNING' AND finished_at IS NOT NULL AND total_score IS NULL",
            nativeQuery = true)
    int finishAggregation(@Param("id") UUID id, @Param("totalScore") BigDecimal totalScore);

    // Dieu kien mo khoa an toan da chot o Q6 (ke hoach D2): rubric cua job nay chua tung co
    // criterion_scores nao (thuoc bat ky luot cham nao) VA khong con luot cham nao khac cua job
    // nay dang thuc su chay (PENDING, hoac RUNNING ma finished_at con NULL). Goi tu markFailed
    // (Dot 4) SAU KHI da set status=FAILED, finished_at=now() cho chinh luot dang fail trong cung
    // transaction, de no tu loai khoi ve thu hai cua dieu kien nay (Postgres thay duoc ghi cua
    // chinh transaction minh, khong can loai tru tuong minh id cua luot vua fail).
    @Query(
            value =
                    """
                    SELECT
                      NOT EXISTS (
                        SELECT 1 FROM criterion_scores cs
                        JOIN scoring_runs sr ON cs.scoring_run_id = sr.id
                        JOIN job_applications ja ON sr.application_id = ja.id
                        WHERE ja.job_id = :jobId
                      )
                      AND NOT EXISTS (
                        SELECT 1 FROM scoring_runs sr2
                        JOIN job_applications ja2 ON sr2.application_id = ja2.id
                        WHERE ja2.job_id = :jobId
                          AND sr2.status IN ('PENDING', 'RUNNING')
                          AND sr2.finished_at IS NULL
                      )
                    """,
            nativeQuery = true)
    boolean isSafeToUnlock(@Param("jobId") UUID jobId);
}
