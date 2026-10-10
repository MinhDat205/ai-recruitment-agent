-- ---------------------------------------------------------------------------
-- db/seed/reset-test-data.sql
--
-- !!! SCRIPT XOA DU LIEU - CHI DUNG TREN DB DEV CUC BO !!!
--
-- Xoa CHI du lieu cua bo test (seed-test.sql): 5 user test (tien to UUID
-- e0000000-) va cong ty cua HR test, KE CA dong phat sinh qua UI sau khi nap
-- (don ung tuyen moi, luot cham, giai thich, goi y cai thien CV, yeu cau trich
-- xuat lai, embedding tin, thong bao...). KHONG dung toi du lieu demo, voi mot
-- ngoai le co chu dich: neu ung vien DEMO tu nop don vao tin cua cong ty test
-- qua UI, don do (va thong bao gan voi no) cung bi xoa - no phat sinh tu du lieu
-- test, khong co trong bo demo goc.
--
-- Dung khi can dua bo test ve trang thai goc: seed-test.sql dung ON CONFLICT DO
-- NOTHING nen chay lai KHONG khoi phuc dong da bi sua qua UI.
--
-- KHONG xoa file CV tren dia (backend/uploads/resumes/) - SQL khong lam duoc;
-- file thua vo hai, install-test-files.ps1 ghi de khi cai lai.
--
-- Hai lop chot chan:
--   1. Bien psql TEST_SEED_CONFIRM phai dung gia tri YES_RESET_TEST_DATA.
--   2. current_database() phai la 'recruitment'.
--
-- CACH CHAY (PowerShell, tu thu muc goc repo):
--   docker compose cp db/seed/reset-test-data.sql postgres:/tmp/reset-test-data.sql
--   docker compose exec -T postgres psql -U recruitment -d recruitment `
--     -v TEST_SEED_CONFIRM=YES_RESET_TEST_DATA -f /tmp/reset-test-data.sql
-- ---------------------------------------------------------------------------

\set ON_ERROR_STOP on

-- Chot chan #1 o tang psql (bien :'x' khong duoc thay ben trong khoi $$...$$ -
-- xem giai thich trong reset-demo-db.sql).
\if :{?TEST_SEED_CONFIRM}
\else
  \echo 'CHOT CHAN #1 THAT BAI: thieu bien TEST_SEED_CONFIRM.'
  \echo 'Truyen: -v TEST_SEED_CONFIRM=YES_RESET_TEST_DATA'
  \quit
\endif

SELECT CASE WHEN :'TEST_SEED_CONFIRM' = 'YES_RESET_TEST_DATA' THEN TRUE ELSE NULL END AS confirm_ok \gset

\if :{?confirm_ok}
\else
  \echo 'CHOT CHAN #1 THAT BAI: TEST_SEED_CONFIRM sai gia tri (can dung YES_RESET_TEST_DATA).'
  \quit
\endif

DO $guard2$
BEGIN
    IF current_database() <> 'recruitment' THEN
        RAISE EXCEPTION 'CHOT CHAN #2 THAT BAI: dang ket noi toi database "%", khong phai "recruitment". Dung lai.',
            current_database();
    END IF;
END
$guard2$;

\echo 'Hai chot chan da qua - bat dau xoa du lieu test...'

BEGIN;

-- Pham vi xoa, tinh MOT lan vao bang tam roi xoa theo thu tu khoa ngoai (con truoc cha).
CREATE TEMP TABLE t_users ON COMMIT DROP AS
    SELECT id FROM users WHERE id IN (
        'e0000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000002',
        'e0000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000004',
        'e0000000-0000-0000-0000-000000000005');
CREATE TEMP TABLE t_companies ON COMMIT DROP AS
    SELECT id FROM companies WHERE owner_id IN (SELECT id FROM t_users);
CREATE TEMP TABLE t_jobs ON COMMIT DROP AS
    SELECT id FROM jobs WHERE company_id IN (SELECT id FROM t_companies);
CREATE TEMP TABLE t_apps ON COMMIT DROP AS
    SELECT id FROM job_applications
    WHERE candidate_id IN (SELECT id FROM t_users) OR job_id IN (SELECT id FROM t_jobs);
CREATE TEMP TABLE t_runs ON COMMIT DROP AS
    SELECT id FROM scoring_runs WHERE application_id IN (SELECT id FROM t_apps);
CREATE TEMP TABLE t_resumes ON COMMIT DROP AS
    SELECT id FROM resumes WHERE candidate_id IN (SELECT id FROM t_users);

-- 1. Thong bao: cua user test, va moi thong bao tro toi don bi xoa (vd thong bao
--    gui cho ung vien demo ve don cua ho vao tin test).
DELETE FROM notifications
WHERE user_id IN (SELECT id FROM t_users)
   OR (entity_type = 'APPLICATION' AND entity_id IN (SELECT id FROM t_apps));

-- 2. Cham diem (con cua scoring_runs)
DELETE FROM score_explanation_attempts WHERE scoring_run_id IN (SELECT id FROM t_runs);
DELETE FROM score_explanations         WHERE scoring_run_id IN (SELECT id FROM t_runs);
DELETE FROM criterion_scores           WHERE scoring_run_id IN (SELECT id FROM t_runs);
DELETE FROM scoring_runs               WHERE id IN (SELECT id FROM t_runs);

-- 3. Con cua job_applications, roi chinh don
DELETE FROM application_messages       WHERE application_id IN (SELECT id FROM t_apps);
DELETE FROM interview_invitations      WHERE application_id IN (SELECT id FROM t_apps);
DELETE FROM application_status_history WHERE application_id IN (SELECT id FROM t_apps);
DELETE FROM job_applications           WHERE id IN (SELECT id FROM t_apps);

-- 4. CV va du lieu phu thuoc CV
DELETE FROM cv_improvement_suggestions WHERE resume_id IN (SELECT id FROM t_resumes);
DELETE FROM cv_improvement_requests    WHERE resume_id IN (SELECT id FROM t_resumes);
DELETE FROM resume_reparse_requests    WHERE resume_id IN (SELECT id FROM t_resumes);
DELETE FROM resume_parsed_data         WHERE resume_id IN (SELECT id FROM t_resumes);
DELETE FROM resumes                    WHERE id IN (SELECT id FROM t_resumes);

-- 5. Tin tuyen dung cua cong ty test va con cua no
DELETE FROM job_embeddings      WHERE job_id IN (SELECT id FROM t_jobs);
DELETE FROM interview_templates WHERE job_id IN (SELECT id FROM t_jobs);
DELETE FROM rubric_criteria     WHERE rubric_id IN (SELECT id FROM rubrics WHERE job_id IN (SELECT id FROM t_jobs));
DELETE FROM rubrics             WHERE job_id IN (SELECT id FROM t_jobs);
DELETE FROM jobs                WHERE id IN (SELECT id FROM t_jobs);

-- 6. Cong ty, ho so nghe nghiep, user
DELETE FROM companies          WHERE id IN (SELECT id FROM t_companies);
DELETE FROM candidate_profiles WHERE user_id IN (SELECT id FROM t_users);
DELETE FROM users              WHERE id IN (SELECT id FROM t_users);

COMMIT;

\echo 'Da xoa du lieu test. Nap lai: seed-test.sql roi install-test-files.ps1.'

SELECT count(*) AS user_test_con_lai FROM users WHERE id::text LIKE 'e0000000-%';
