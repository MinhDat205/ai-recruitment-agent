-- Chot chan cuoi cho race dieu kien tien quyet #4 cua D2 (FR-H04): HR bam dup nut "Cham diem ho
-- so", hoac mo hai tab, co the tao hai lot cham song song cho CUNG mot don truoc khi service kip
-- kiem tra xong (SELECT ... roi moi INSERT - TOCTOU). Cung khuon voi uq_application_per_cycle da
-- xu ly dung tinh huong nay o C2 (xem V1, khoi 5. UNG TUYEN): rang buoc DB moi la chot chan that,
-- kiem tra o tang service (ScoringRunService.requireNoRunInProgress) chi la phan hoi som, sach cho
-- duong khong dua.
--
-- Da xac minh bang chay that tren Postgres 17 (khong chi suy luan): predicate khong goi ham nao
-- (khong now()) nen hop le voi index co dieu kien; mot lot da co finished_at (RUNNING cho D3, hoac
-- FAILED) bi loai khoi predicate nen KHONG bi chan - giu dung hop dong da chot o Q1 (HR duoc cham
-- lai don da cham xong).
--
-- He qua da biet, KHONG phai loi cua migration nay: mot lot RUNNING/finished_at con NULL bi "chet"
-- giua chung (JVM restart truoc khi orchestrator kip set finished_at - xem Q1(iii) trong ke hoach
-- D2) se chan CUNG voi index nay, o tang DB chu khong chi tang service, moi lot cham moi cho don
-- do. Day la phan mo rong tu nhien cua gap da ghi nhan tu truoc (ResumeParsingScheduler cung co gap
-- tuong tu voi PROCESSING), chua co co che heartbeat/lease/stale-claim reaper de tu phat hien va
-- don dep - thuoc pham vi chore/hardening, khong xu ly trong D2.
CREATE UNIQUE INDEX uq_scoring_run_in_progress
    ON scoring_runs(application_id)
    WHERE status IN ('PENDING', 'RUNNING') AND finished_at IS NULL;
