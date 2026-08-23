-- Cot moi phuc vu co che retry-with-backoff cho loi LLM tam thoi (timeout/429/5xx) va stale-claim
-- reaper (JVM restart giua chung) - chore/hardening Dot 4. Xem plan chi tiet: attempt_count dem so
-- lan da thu (dat ten trung voi score_explanation_attempts.attempt_count, V5, cho nhat quan thuat
-- ngu trong du an); next_attempt_at la moc backoff, NULL = san sang ngay, scheduler chi claim khi
-- da qua moc nay hoac con NULL.
--
-- resumes con them claimed_at: bang nay TRUOC GIO chi co uploaded_at (xem comment trong
-- Resume.java "Bang resumes CHI co uploaded_at, khong co updated_at"), khong co cot thoi gian nao
-- de biet mot ban ghi PROCESSING da claim tu luc nao - can thiet cho stale-claim reaper. scoring_runs
-- KHONG can cot claim-time moi: started_at (co san tu D2, ghi luc claim() PENDING->RUNNING) da dung
-- dung vai tro do.
ALTER TABLE resumes
    ADD COLUMN claimed_at      TIMESTAMPTZ,
    ADD COLUMN attempt_count   INT NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMPTZ;

-- Backfill BAT BUOC: sau ALTER TABLE, moi ban ghi dang PROCESSING TU TRUOC migration nay deu co
-- claimed_at = NULL. Dieu kien "claimed_at < threshold" cua reaper KHONG khop NULL trong SQL, nen
-- chinh cac ban ghi ket san - ly do tinh nang nay ton tai - se ket VINH VIEN neu khong backfill.
-- Dung uploaded_at chu KHONG dung now(): now() lam mot ban ghi da ket tu hom qua bi coi la "vua
-- claim", phai cho them stale-timeout-ms nua moi duoc cuu.
UPDATE resumes SET claimed_at = uploaded_at WHERE parse_status = 'PROCESSING' AND claimed_at IS NULL;

ALTER TABLE scoring_runs
    ADD COLUMN attempt_count   INT NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMPTZ;
