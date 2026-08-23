package com.recruitment.scoring;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScoringRunRepository extends JpaRepository<ScoringRun, UUID> {

    // Dot 4f (chore/hardening) - thay the findByStatus(PENDING, pageable) cu: them dieu kien
    // next_attempt_at (bo qua luot con dang cho backoff) VA khoa cuoi ", id", mau
    // ResumeRepository.findReadyForProcessing (ResumeRepository.findByParseStatus cu da bi xoa
    // cung ly do o Dot 4f).
    @Query(
            value = "SELECT * FROM scoring_runs WHERE status = 'PENDING' "
                    + "AND (next_attempt_at IS NULL OR next_attempt_at <= now()) "
                    + "ORDER BY created_at, id LIMIT :batchSize",
            nativeQuery = true)
    List<ScoringRun> findReadyForProcessing(@Param("batchSize") int batchSize);

    // GET /api/hr/applications/{id}/scoring-runs (Dot 5) - lich su cac luot cham cua MOT don, moi
    // nhat truoc, khop dung thu tu ma idx_scoring_app(application_id, created_at DESC) da danh san.
    // Da them khoa cuoi ", id DESC" (Dot 2, chore/hardening) - derived query goc (ORDER BY
    // created_at DESC KHONG khoa cuoi) da xac nhan thuc nghiem tren Postgres that: hai luot cham
    // CUNG created_at (cung transaction, xem CLAUDE.md muc 3c ve now() transaction-scoped) co the
    // doi thu tu giua hai lan doc du CUNG mot ke hoach truy van. Ten method GIU NGUYEN (khong doi
    // sang OrderByCreatedAtDescIdDesc) de khong keo theo moi call site chi vi mo ta ten khong con
    // chinh xac 100% - dung @Query JPQL tuong minh thay cho derived query.
    @Query("SELECT s FROM ScoringRun s WHERE s.applicationId = :applicationId ORDER BY s.createdAt DESC, s.id DESC")
    List<ScoringRun> findByApplicationIdOrderByCreatedAtDesc(@Param("applicationId") UUID applicationId);

    // GET /api/hr/jobs/{jobId}/applications (Dot 5) - lay luot cham GAN NHAT cho MOI don trong
    // applicationIds bang MOT query duy nhat, tranh N+1 (goi rieng findByApplicationIdOrderBy...
    // roi .get(0) cho tung don trong vong lap se la N+1 khi danh sach dai). DISTINCT ON la cu phap
    // rieng cua Postgres, khop dung idx_scoring_app(application_id, created_at DESC) da co san -
    // moi application_id chi giu lai dong co created_at lon nhat.
    // Da them khoa cuoi ", id DESC" (Dot 2, chore/hardening) - cung ly do voi
    // findByApplicationIdOrderByCreatedAtDesc: hai luot CUNG created_at (cung transaction) doi thu
    // tu vat ly giua hai lan doc du khong ORDER BY thay doi.
    @Query(
            value =
                    """
                    SELECT DISTINCT ON (application_id)
                        application_id AS applicationId, id AS id, status AS status, finished_at AS finishedAt
                    FROM scoring_runs
                    WHERE application_id IN (:applicationIds)
                    ORDER BY application_id, created_at DESC, id DESC
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
    // Dot 4f: them dieu kien next_attempt_at - dong bo voi findReadyForProcessing.
    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE scoring_runs SET status = 'RUNNING', started_at = now() "
                    + "WHERE id = :id AND status = 'PENDING' "
                    + "AND (next_attempt_at IS NULL OR next_attempt_at <= now())",
            nativeQuery = true)
    int claimForProcessing(@Param("id") UUID id);

    // Dot 4e (chore/hardening) - markFailed (ScoringRunStateService) doi tu findById+save khong
    // dieu kien sang UPDATE co dieu kien status='RUNNING'. Ly do (Viec 3, plan Dot 4): stale-claim
    // reaper (Dot 4h) tao kha nang mot luot bi coi la "ket", reap ve PENDING roi duoc claim lai boi
    // worker khac (status luc do la RUNNING CUA LAN CLAIM MOI) trong khi worker goc (zombie) van
    // song va cuoi cung goi markFailed - dieu kien nay chan duoc truong hop worker goc ghi de len
    // dung luot ma worker moi dang xu ly (rowcount=0 vi status khong con la RUNNING cua lan claim cu
    // - that ra van la 'RUNNING' nhung ve mat gia tri thi giong nhau, XEM GIOI HAN DA BIET duoi day).
    // GIOI HAN DA BIET (ghi ROADMAP, KHONG chua o nhanh nay): dieu kien status='RUNNING' don thuan
    // KHONG phan biet duoc hai worker cung thay 'RUNNING' tu CUNG mot lan claim (worker zombie song
    // sot qua het stale-timeout-ms, luot da bi reap va claim lai - ca hai deu thay RUNNING). Chan
    // triet de doi mot cot version tang theo tung lan claim (vd so khop them started_at), ngoai
    // pham vi nhanh nay.
    // COALESCE(finished_at, :now) giu dung guard cu cua markFailed: chi set finished_at khi dang
    // NULL, KHONG ghi de mot moc da co san (xem comment ScoringRunStateService.markFailed cu).
    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE scoring_runs SET status = 'FAILED', error_message = :errorMessage, "
                    + "finished_at = COALESCE(finished_at, :now) WHERE id = :id AND status = 'RUNNING'",
            nativeQuery = true)
    int markFailedIfRunning(
            @Param("id") UUID id, @Param("errorMessage") String errorMessage, @Param("now") Instant now);

    // Dot 4e - loi LLM TAM THOI, con lan thu: quay ve PENDING de scheduler nhat lai sau backoff.
    // attempt_count dong vai tro optimistic lock, dung khuon voi ResumeRepository.markTemporaryFailure
    // (xem ScoringRunStateService.markTemporaryFailure ve ly do KHONG goi markFailed o day).
    // "AND finished_at IS NULL" them o Dot 4h (phat hien khi code reaper, KHONG co truoc do): ca hai
    // call site cua markTemporaryFailure (ScoringRunOrchestrator.doProcess VA reaper moi) deu chi
    // duoc phep tac dong len mot luot CHUA cham xong toan bo tieu chi. Voi call site D2 dieu nay vo
    // hai (finished_at luon dang NULL tai thoi diem do). Nhung reaper (Dot 4h) doc danh sach "ket"
    // qua mot @Query rieng (finished_at IS NULL tai thoi diem DOC) roi moi ghi - GIUA hai buoc do,
    // worker goc co the cham xong that su (goi markFinished(), set finished_at) TRUOC khi reaper
    // kip ghi. Thieu dieu kien nay, UPDATE van khop (status van la RUNNING, attempt_count khong doi)
    // va se keo NHAM mot luot DA XONG, dang cho D3 tong hop, quay ve PENDING - mat du lieu that.
    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE scoring_runs SET status = 'PENDING', attempt_count = :nextAttempt, "
                    + "error_message = :errorMessage, next_attempt_at = :nextAttemptAt "
                    + "WHERE id = :id AND status = 'RUNNING' AND finished_at IS NULL AND attempt_count = :expectedCount",
            nativeQuery = true)
    int markTemporaryFailure(
            @Param("id") UUID id,
            @Param("expectedCount") int expectedCount,
            @Param("nextAttempt") int nextAttempt,
            @Param("errorMessage") String errorMessage,
            @Param("nextAttemptAt") Instant nextAttemptAt);

    // Dot 4e - loi LLM TAM THOI nhung DA HET so lan thu (nextAttempt >= max-attempts): FAILED han,
    // khong con next_attempt_at. Tach rieng voi markFailedIfRunning vi nhanh nay CAN dieu kien
    // attempt_count (optimistic lock) VA can ghi attempt_count cuoi cung cho dung so lan da thu that.
    // "AND finished_at IS NULL" them o Dot 4h - cung ly do voi markTemporaryFailure o tren.
    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE scoring_runs SET status = 'FAILED', attempt_count = :nextAttempt, "
                    + "error_message = :errorMessage, finished_at = :now, next_attempt_at = NULL "
                    + "WHERE id = :id AND status = 'RUNNING' AND finished_at IS NULL AND attempt_count = :expectedCount",
            nativeQuery = true)
    int markRetryExhausted(
            @Param("id") UUID id,
            @Param("expectedCount") int expectedCount,
            @Param("nextAttempt") int nextAttempt,
            @Param("errorMessage") String errorMessage,
            @Param("now") Instant now);

    // Dot 4h (chore/hardening) - danh sach ID cho stale-claim reaper: mot luot RUNNING claim qua lau
    // (started_at truoc nguong stale-timeout-ms) MA CHUA cham xong (finished_at con NULL - loai tru
    // luot da xong dang cho D3, dung tien le findByStatusAndFinishedAtIsNotNullAndTotalScoreIsNull
    // o tren nhung dao nguoc dieu kien finished_at) nghia la JVM co the da restart giua chung. Tra
    // List<UUID>, khong tra entity - cung ly do voi ResumeRepository.findIdsByParseStatusAndClaimedAtBefore.
    @Query("SELECT s.id FROM ScoringRun s WHERE s.status = :status AND s.finishedAt IS NULL AND s.startedAt < :threshold")
    List<UUID> findIdsByStatusAndFinishedAtIsNullAndStartedAtBefore(
            @Param("status") ScoringRunStatus status, @Param("threshold") Instant threshold);

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

    // D4 (FR-H05, mo rong GET /api/hr/jobs/{jobId}/applications) - nguon diem cho xep hang la lot
    // DONE MOI NHAT cua moi don (Q5, ke hoach D3), KHONG PHAI lot moi nhat bat ke trang thai (do la
    // findLatestByApplicationIdIn o tren, chi phuc vu hien thi TIEN DO). Mot don co the co nhieu
    // lot (HR duoc cham lai) - DISTINCT ON dam bao chi lay dung MOT dong (lot DONE gan created_at
    // nhat) cho moi application_id, khop dung idx_scoring_app(application_id, created_at DESC) da
    // co san. Don khong co lot DONE nao (chua cham, chi co lot FAILED, hoac dang cham do) khong
    // xuat hien trong ket qua - tang goi (ApplicationOwnerService) coi la totalScore=null.
    // Da them khoa cuoi ", id DESC" (Dot 2, chore/hardening) - cung ly do voi
    // findByApplicationIdOrderByCreatedAtDesc/findLatestByApplicationIdIn.
    @Query(
            value =
                    """
                    SELECT DISTINCT ON (application_id)
                        application_id AS applicationId, id AS id, total_score AS totalScore
                    FROM scoring_runs
                    WHERE application_id IN (:applicationIds) AND status = 'DONE'
                    ORDER BY application_id, created_at DESC, id DESC
                    """,
            nativeQuery = true)
    List<LatestDoneScoringRunView> findLatestDoneByApplicationIdIn(
            @Param("applicationIds") Collection<UUID> applicationIds);

    // D4 (FR-H06, Dot 3) - nhat cac luot san sang de ScoreExplanationOrchestrator xu ly. Ba dieu
    // kien: status='DONE' (D3 da tong hop xong, xem CLAUDE.md muc 2b), CHUA co score_explanations
    // (chua sinh bao cao), CHUA vuot nguong thu (score_explanation_attempts.attempt_count con duoi
    // maxAttempts, hoac chua tung thu lan nao). DAT O DAY (khong phai ScoreExplanationRepository) vi
    // hai ly do: (1) kieu tra ve la List<ScoringRun> - entity ma repository nay so huu, con
    // ScoreExplanationRepository la JpaRepository<ScoreExplanation, UUID>, tra ve mot entity khac
    // se gay ap luc len co che mapping; (2) bang dan dat (driving table) cua truy van la
    // scoring_runs - hai bang con lai chi duoc tham chieu qua NOT EXISTS - dung tien le da co ngay
    // trong file nay (findByStatusAndFinishedAtIsNotNullAndTotalScoreIsNull, D3) da dat "truy van
    // nhat luot san sang cho mot giai doan xu ly khac" o day, du muc dich phuc vu D4 chu khong phai
    // vong doi rieng cua scoring_runs.
    //
    // KHONG can migration index moi (da xac minh bang chay that EXPLAIN tren Postgres 17, khong chi
    // suy luan): dieu kien dau dung DUNG predicate cua idx_scoring_total (V1, "WHERE status =
    // 'DONE'") - Postgres chon Index Scan tren no du khong ORDER BY total_score; hai dieu kien NOT
    // EXISTS con lai so khop scoring_run_id, cot da co UNIQUE index tu dong (score_explanations, V1;
    // score_explanation_attempts, V5) - Postgres chon Index Scan/Index Only Scan tren ca hai. Plan
    // that (EXPLAIN COSTS OFF, enable_seqscan=off de buoc lo moi phuong an):
    //   Nested Loop Anti Join
    //     -> Nested Loop Anti Join
    //          -> Index Scan using idx_scoring_total on scoring_runs sr
    //          -> Index Only Scan using score_explanations_scoring_run_id_key on score_explanations
    //     -> Index Scan using score_explanation_attempts_scoring_run_id_key on score_explanation_attempts
    // Khong Seq Scan nao trong plan - ca ba dieu kien deu co index dung duoc, KHONG can V6.
    @Query(
            value =
                    """
                    SELECT sr.* FROM scoring_runs sr
                    WHERE sr.status = 'DONE'
                      AND NOT EXISTS (SELECT 1 FROM score_explanations se WHERE se.scoring_run_id = sr.id)
                      AND NOT EXISTS (
                            SELECT 1 FROM score_explanation_attempts a
                            WHERE a.scoring_run_id = sr.id AND a.attempt_count >= :maxAttempts
                          )
                    ORDER BY sr.created_at
                    LIMIT :batchSize
                    """,
            nativeQuery = true)
    List<ScoringRun> findRunsReadyForExplanation(
            @Param("maxAttempts") int maxAttempts, @Param("batchSize") int batchSize);
}
