-- Theo doi so lan thu tong hop bao cao giai thich (FR-H06, D4) that bai cho MOT luot cham, de dung
-- retry sau mot so lan huu han - moi lan thu la MOT lan goi LLM that (khong mien phi nhu D3, xem
-- ScoreAggregator), khong the chap nhan retry vo han moi 5 giay nhu ban nhap dau tien cua ke hoach
-- D4 da lo de xuat (Q5, dot duyet lai).
--
-- Vi sao TACH BANG RIENG, khong them cot trang thai/loi vao chinh score_explanations: bang do (V1)
-- duoc thiet ke voi dung MOT nghia - "bao cao that, ghi DUNG MOT lan khi da xong" (summary/model/
-- prompt_version deu NOT NULL, khong co cot status/error). De ghi duoc "da thu va that bai" vao
-- chinh bang do se phai noi long NOT NULL cua summary/model/prompt_version, bien mot bang von chi
-- chua bao cao that thanh bang lan hai loai dong (bao cao that vs ban ghi loi) - dung thu rui ro da
-- tu canh bao khi can nhac phuong an: "khong co cho phan biet bao cao that voi ban ghi loi". Mot
-- bang phu, doc lap hoan toan voi score_explanations, giu nguyen ven "y nghia mot cot" cua bang goc,
-- va de xoa/reset thu cong (xoa dong o bang nay de ep thu lai) ma khong dung vao du lieu bao cao
-- that.
--
-- Nguong dung: app.explanation.max-attempts (mac dinh 3, Dot 3). Moi lan attempt_count tang ung voi
-- MOT lan goi ScoreExplanationOrchestrator.processOne(); ben trong do ScoreExplanationService da tu
-- thu lai 1 lan khi JSON/validate hong (mau CriterionScoringService D2) - tuc toi da 3 x 2 = 6 lan
-- goi LLM that su truoc khi lot cham do bi loai vinh vien khoi vong quet cua scheduler (Dot 3). Dung
-- vinh vien la chap nhan duoc (giong cac khoan no "khong co reaper" da ghi trong ROADMAP) - van hanh
-- reset bang DELETE thu cong dong tuong ung neu muon thu lai.
--
-- id + scoring_run_id UNIQUE (thay vi scoring_run_id lam PK truc tiep) - khop dung khuon
-- score_explanations/ResumeParsedData: mot id sinh rieng lam khoa chinh, FK 1-1 la mot cot UNIQUE
-- rieng, khong tai su dung FK lam PK.
CREATE TABLE score_explanation_attempts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scoring_run_id     UUID NOT NULL UNIQUE REFERENCES scoring_runs(id) ON DELETE CASCADE,
    attempt_count      INT NOT NULL DEFAULT 0,
    last_error         VARCHAR(255) NOT NULL,
    last_attempted_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
