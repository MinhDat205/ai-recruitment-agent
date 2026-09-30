-- ---------------------------------------------------------------------------
-- db/seed/reset-demo-db.sql
--
-- !!! CANH BAO: SCRIPT XOA TOAN BO DU LIEU NGHIEP VU !!!
-- Chi chay tren DB dev cuc bo qua docker compose. KHONG co co che khoi phuc -
-- khong bao gio chay script nay tren moi truong khac ngoai dev cuc bo.
--
-- Muc dich: don sach du lieu tich luy qua nhieu dot test tay, chuan bi nen
-- trong truoc khi chay seed-demo-structural.sql (tang 1) roi
-- seed-demo-ai-output.sql (tang 2) cho nhanh chore/seed-demo.
--
-- Ba lop chot chan phai vuot qua truoc khi TRUNCATE chay:
--   1. Bien psql DEMO_SEED_CONFIRM phai duoc truyen dung gia tri yeu cau.
--   2. current_database() phai dung la 'recruitment' (ten DB dev khai trong
--      docker-compose.yml).
--   3. Tong so dong bang users khong duoc vuot 50 - chan truong hop lo tro
--      nham vao mot DB da co du lieu that quy mo lon.
--
-- CACH CHAY (PowerShell, tu thu muc goc repo):
--
--   docker compose cp db/seed/reset-demo-db.sql postgres:/tmp/reset-demo-db.sql
--   docker compose exec -T postgres psql -U recruitment -d recruitment `
--     -v DEMO_SEED_CONFIRM=YES_RESET_DEMO_DB -f /tmp/reset-demo-db.sql
--
-- KHONG truyen -v DEMO_SEED_CONFIRM se bi chan boi lop chot chan #1.
-- ---------------------------------------------------------------------------

\set ON_ERROR_STOP on

-- CHOT CHAN #1a: bien DEMO_SEED_CONFIRM phai duoc truyen (-v).
-- Khong dung DO $$ ... $$ o day: psql chi thay the bien :'ten' o tang lenh
-- SQL truoc khi gui cho server - no KHONG di vao ben trong mot chuoi
-- dollar-quoted ($$...$$) vi lexer cua psql coi ca khoi $$...$$ la MOT token
-- chuoi duy nhat. Dat :'DEMO_SEED_CONFIRM' ben trong DO $guard$...$guard$ se
-- khien Postgres nhan nguyen ky tu ":" va bao loi cu phap (da gap that khi
-- chay tren DB dev that). Vi vay kiem tra bang \if/\gset o tang psql, DO
-- block chi dung cho hai chot chan con lai (#2, #3) khong can bien psql.
\if :{?DEMO_SEED_CONFIRM}
\else
  \echo 'CHOT CHAN #1 THAT BAI: thieu bien DEMO_SEED_CONFIRM.'
  \echo 'Truyen: -v DEMO_SEED_CONFIRM=YES_RESET_DEMO_DB'
  \quit
\endif

-- CHOT CHAN #1b: bien DEMO_SEED_CONFIRM phai dung gia tri yeu cau.
-- \gset: neu CASE tra NULL (gia tri sai), psql se HUY bien confirm_ok thay vi
-- gan NULL - nen \if :{?confirm_ok} kiem duoc chinh xac "co dat hay khong".
SELECT CASE WHEN :'DEMO_SEED_CONFIRM' = 'YES_RESET_DEMO_DB' THEN TRUE ELSE NULL END AS confirm_ok \gset

\if :{?confirm_ok}
\else
  \echo 'CHOT CHAN #1 THAT BAI: DEMO_SEED_CONFIRM sai gia tri (can dung YES_RESET_DEMO_DB).'
  \quit
\endif

\echo 'Chot chan #1 (DEMO_SEED_CONFIRM) da qua.'

DO $guard2$
BEGIN
    IF current_database() <> 'recruitment' THEN
        RAISE EXCEPTION 'CHOT CHAN #2 THAT BAI: dang ket noi toi database "%", khong phai "recruitment". Dung lai.',
            current_database();
    END IF;
END
$guard2$;

DO $guard3$
DECLARE
    total_users INT;
BEGIN
    SELECT count(*) INTO total_users FROM users;
    IF total_users > 50 THEN
        RAISE EXCEPTION 'CHOT CHAN #3 THAT BAI: bang users dang co % dong (> 50) - trong nhu DB that, khong phai DB demo. Dung lai.',
            total_users;
    END IF;
END
$guard3$;

\echo 'Ca ba chot chan da qua - bat dau TRUNCATE...'

-- Liet ke tuong minh toan bo 21 bang nghiep vu, lay dung tu danh sach
-- CREATE TABLE trong V1__init_schema.sql + V5__/V6__ (khong doan so bang).
-- TRUNCATE CASCADE quet theo MOI FK tro toi bang bi truncate, bat ke FK khai
-- ON DELETE RESTRICT/CASCADE/SET NULL - liet ke day du de nguoi doc script
-- biet chinh xac pham vi anh huong, khong phai suy doan tu CASCADE.
TRUNCATE TABLE
    -- Nguoi dung & ho so
    users, candidate_profiles,
    -- Doanh nghiep & tin tuyen dung
    companies, jobs, interview_templates, rubrics, rubric_criteria,
    -- CV & du lieu AI trich xuat
    resumes, resume_parsed_data, job_embeddings, resume_reparse_requests,
    -- Don ung tuyen & pipeline HR
    job_applications, application_status_history, interview_invitations,
    -- Cham diem AI
    scoring_runs, criterion_scores, score_explanations, score_explanation_attempts,
    -- Goi y viec lam & cai thien CV (F1/F2)
    job_recommendations, cv_improvement_suggestions, cv_improvement_requests,
    -- Thong bao (E2)
    notifications
CASCADE;

\echo 'Da don sach. Tiep theo: chay seed-demo-structural.sql roi seed-demo-ai-output.sql.'
