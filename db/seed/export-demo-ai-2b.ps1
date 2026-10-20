<#
db/seed/export-demo-ai-2b.ps1

Muc dich: xuat TANG 2b cua seed demo (chore/seed-final, K1 Q2, K4 dot 5, P3) - output AI THAT sinh mot lan tren DB
chi co demo (reset -> 1 -> 2 -> 1b, KHONG co bo test) - ra db/seed/seed-demo-ai-output-2b.sql, va chep 2 PDF CV builder
cua Bui Ngoc Mai (P4) tu backend/uploads/resumes/ sang db/seed/resumes/.

Khac export-ai-output.ps1 (tang 2, dump NGUYEN bang): tang 2b chi lay DONG MOI cua luot chay, nen moi bang duoc loc
vao schema tam seed2b_export (CREATE TABLE ... AS SELECT ... WHERE), pg_dump rieng tung bang theo DUNG thu tu khoa
ngoai, roi doi tien to "seed2b_export." thanh "public." (ca o comment "Schema: ..." pg_dump tu ghi). Dong moi: INSERT ... ON CONFLICT DO NOTHING (pg_dump
--on-conflict-do-nothing). Cot doi tren dong co san (P3): UPDATE sinh tu DB - embedding 4 ho so, rubric bi khoa khi tao
luot cham. Schema tam bi xoa o cuoi (ke ca khi loi).

Bang va dieu kien loc (so dong ky vong ghi o $Expected - lech thi THROW):
  resumes / resume_parsed_data / cv_builder_documents : CV dung tu form (moi dong cv_builder_documents)
  job_embeddings                                      : tin dai d1b00000-% (cong ty 2)
  scoring_runs                                        : don dai d1b00000-% (H14 - Tran Minh Hoang -> DevOps)
  criterion_scores / score_explanations /
  score_explanation_attempts                          : cua cac luot cham tren
  notifications                                       : SCORING_FINISHED cua don dai d1b (khong lay dong viet tay d1b)

DIEU KIEN TRUOC KHI CHAY: docker compose dang chay; backend DA DUNG (luot cham DONE, co giai thich).
CACH CHAY (tu thu muc goc repo):
    .\db\seed\export-demo-ai-2b.ps1

Ky thuat ghi script bash / lay file ra: giong export-ai-output.ps1 (LF thuan, docker compose cp, khong pipe qua
PowerShell de giu dau tieng Viet).
#>

$ErrorActionPreference = 'Stop'

$RepoRoot          = (Get-Item $PSScriptRoot).Parent.Parent.FullName
$DbSeedDir         = Join-Path $RepoRoot 'db\seed'
$ResumesOutDir     = Join-Path $DbSeedDir 'resumes'
$UploadsResumesDir = Join-Path $RepoRoot 'backend\uploads\resumes'
$OutputSqlPath     = Join-Path $DbSeedDir 'seed-demo-ai-output-2b.sql'

# Thu tu bang PHAI dung theo khoa ngoai. Gia tri = so dong ky vong (K6, L29-L31).
$TableOrder = @(
    'resumes', 'resume_parsed_data', 'cv_builder_documents', 'job_embeddings', 'scoring_runs',
    'criterion_scores', 'score_explanations', 'score_explanation_attempts', 'notifications'
)
$Expected = @{
    'resumes' = 2; 'resume_parsed_data' = 2; 'cv_builder_documents' = 2; 'job_embeddings' = 6; 'scoring_runs' = 1;
    'criterion_scores' = 5; 'score_explanations' = 1; 'score_explanation_attempts' = 0; 'notifications' = 1
}
$ExpectedProfileUpdates = 4
$ExpectedRubricUpdates  = 1

$StageSql = @'
DROP SCHEMA IF EXISTS seed2b_export CASCADE;
CREATE SCHEMA seed2b_export;
CREATE TABLE seed2b_export.resumes AS
    SELECT * FROM public.resumes WHERE id IN (SELECT resume_id FROM public.cv_builder_documents);
CREATE TABLE seed2b_export.resume_parsed_data AS
    SELECT * FROM public.resume_parsed_data WHERE resume_id IN (SELECT resume_id FROM public.cv_builder_documents);
CREATE TABLE seed2b_export.cv_builder_documents AS
    SELECT * FROM public.cv_builder_documents;
CREATE TABLE seed2b_export.job_embeddings AS
    SELECT * FROM public.job_embeddings WHERE job_id::text LIKE 'd1b00000-%';
CREATE TABLE seed2b_export.scoring_runs AS
    SELECT * FROM public.scoring_runs WHERE application_id::text LIKE 'd1b00000-%';
CREATE TABLE seed2b_export.criterion_scores AS
    SELECT * FROM public.criterion_scores WHERE scoring_run_id IN (SELECT id FROM seed2b_export.scoring_runs);
CREATE TABLE seed2b_export.score_explanations AS
    SELECT * FROM public.score_explanations WHERE scoring_run_id IN (SELECT id FROM seed2b_export.scoring_runs);
CREATE TABLE seed2b_export.score_explanation_attempts AS
    SELECT * FROM public.score_explanation_attempts WHERE scoring_run_id IN (SELECT id FROM seed2b_export.scoring_runs);
CREATE TABLE seed2b_export.notifications AS
    SELECT * FROM public.notifications
    WHERE type = 'SCORING_FINISHED' AND id::text NOT LIKE 'd1b00000-%' AND entity_id::text LIKE 'd1b00000-%';
'@

# UPDATE cho dong co san (P3). search_path cua pg_dump la rong nen moi ten bang deu ghi kem "public.".
$UpdatesSql = @'
SELECT format('UPDATE public.candidate_profiles SET embedding = %L, embedding_model = %L WHERE id = %L;',
              embedding::text, embedding_model, id)
FROM public.candidate_profiles WHERE embedding IS NOT NULL ORDER BY id;
SELECT format('UPDATE public.rubrics SET is_locked = TRUE WHERE id = %L;', id)
FROM public.rubrics WHERE is_locked ORDER BY id;
'@

$Header = @'
-- ---------------------------------------------------------------------------
-- db/seed/seed-demo-ai-output-2b.sql - TANG 2b cua seed demo (chore/seed-final)
--
-- SINH TU DONG bang db/seed/export-demo-ai-2b.ps1 tu mot luot chay AI THAT (19/10/2026) - KHONG sua tay.
-- Chay SAU tang 1b (K5): reset -> seed-demo-structural.sql -> seed-demo-ai-output.sql -> seed-demo-structural-1b.sql
-- -> file nay -> install-demo-files.ps1.
-- Dong moi: INSERT ... ON CONFLICT DO NOTHING; dong co san: UPDATE (embedding 4 ho so, rubric bi khoa - P3).
-- Da nap file nay thi KHONG chay lai rieng 1b (chot chan L11) - nap lai tu dau theo K5.
-- ---------------------------------------------------------------------------
BEGIN;
DO $guard$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.job_applications WHERE id = 'd1b00000-0009-0000-0000-000000000001') THEN
        RAISE EXCEPTION 'CHOT CHAN 2b THAT BAI: chua co tang 1b (don d1b00000-0009-...-01). Nap theo K5.';
    END IF;
END
$guard$;
'@

function Invoke-Psql([string]$Sql) {
    docker compose exec -T postgres psql -U recruitment -d recruitment -v ON_ERROR_STOP=1 -t -A -c $Sql
    if ($LASTEXITCODE -ne 0) { throw "psql that bai (exit $LASTEXITCODE). DUNG LAI." }
}

function Write-LfFile([string]$Path, [string]$Content) {
    # LF thuan, UTF-8 khong BOM (xem export-ai-output.ps1: Set-Content ghi CRLF lam vo bash).
    [System.IO.File]::WriteAllText($Path, $Content.Replace("`r`n", "`n"), (New-Object System.Text.UTF8Encoding($false)))
}

Write-Host '=== Buoc 1/4: dua script vao container ==='
$tmp = Join-Path $env:TEMP 'export-demo-ai-2b'
New-Item -ItemType Directory -Path $tmp -Force | Out-Null
Write-LfFile (Join-Path $tmp 'stage.sql') $StageSql
Write-LfFile (Join-Path $tmp 'updates.sql') $UpdatesSql
Write-LfFile (Join-Path $tmp 'header.sql') $Header

$bashLines = @('set -e', 'OUT=/tmp/seed-demo-ai-output-2b.sql', 'P="psql -U recruitment -d recruitment -v ON_ERROR_STOP=1"',
    'trap ''$P -q -c "DROP SCHEMA IF EXISTS seed2b_export CASCADE" >/dev/null'' EXIT',
    '$P -q -f /tmp/export-2b/stage.sql', 'cat /tmp/export-2b/header.sql > "$OUT"')
foreach ($t in $TableOrder) {
    $bashLines += ('echo "-- Bang: {0}" >> "$OUT"' -f $t)
    $bashLines += ('pg_dump -U recruitment -d recruitment --data-only --column-inserts --on-conflict-do-nothing --no-owner --table=seed2b_export.{0} | sed -e "s/^INSERT INTO seed2b_export\./INSERT INTO public./" -e "s/Schema: seed2b_export;/Schema: public;/" >> "$OUT"' -f $t)
}
$bashLines += 'echo "-- Cot doi tren dong co san (P3)" >> "$OUT"'
$bashLines += '$P -t -A -f /tmp/export-2b/updates.sql >> "$OUT"'
$bashLines += 'echo "COMMIT;" >> "$OUT"'
Write-LfFile (Join-Path $tmp 'export.sh') (($bashLines -join "`n") + "`n")

docker compose exec -T postgres mkdir -p /tmp/export-2b
foreach ($f in 'stage.sql', 'updates.sql', 'header.sql', 'export.sh') {
    docker compose cp (Join-Path $tmp $f) "postgres:/tmp/export-2b/$f"
    if ($LASTEXITCODE -ne 0) { throw "Khong dua duoc $f vao container. DUNG LAI." }
}

Write-Host '=== Buoc 2/4: loc dong + pg_dump tung bang trong container ==='
docker compose exec -T postgres bash /tmp/export-2b/export.sh
if ($LASTEXITCODE -ne 0) { throw "Export trong container that bai (exit $LASTEXITCODE). DUNG LAI." }
docker compose cp 'postgres:/tmp/seed-demo-ai-output-2b.sql' $OutputSqlPath
if ($LASTEXITCODE -ne 0) { throw "Khong lay duoc file SQL ra khoi container. DUNG LAI." }
$left = Invoke-Psql "SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'seed2b_export';"
if (($left | Select-Object -First 1).Trim() -ne '0') { throw "Schema tam seed2b_export van con. DUNG LAI." }

Write-Host '=== Buoc 3/4: doi chieu so dong ==='
$sqlText = [System.IO.File]::ReadAllText($OutputSqlPath, [System.Text.Encoding]::UTF8)
$totalInsert = ([regex]::Matches($sqlText, '(?m)^INSERT INTO ')).Count
$sum = 0
foreach ($t in $TableOrder) {
    $n = ([regex]::Matches($sqlText, '(?m)^INSERT INTO public\.' + $t + ' ')).Count
    Write-Host ("  {0,-28} {1} (ky vong {2})" -f $t, $n, $Expected[$t])
    if ($n -ne $Expected[$t]) { throw "Bang $t co $n dong INSERT, ky vong $($Expected[$t]). DUNG LAI." }
    $sum += $n
}
if ($sum -ne $totalInsert) { throw "Tong theo bang ($sum) khac tong INSERT ($totalInsert). DUNG LAI." }
$profileUpdates = ([regex]::Matches($sqlText, '(?m)^UPDATE public\.candidate_profiles ')).Count
$rubricUpdates  = ([regex]::Matches($sqlText, '(?m)^UPDATE public\.rubrics ')).Count
Write-Host "  UPDATE candidate_profiles   $profileUpdates (ky vong $ExpectedProfileUpdates)"
Write-Host "  UPDATE rubrics              $rubricUpdates (ky vong $ExpectedRubricUpdates)"
if ($profileUpdates -ne $ExpectedProfileUpdates -or $rubricUpdates -ne $ExpectedRubricUpdates) { throw "So UPDATE lech. DUNG LAI." }
if ($sqlText -match 'seed2b_export') { throw "File SQL con ten schema tam seed2b_export. DUNG LAI." }
# Dau tieng Viet: dung ky tu tu ma (khong phu thuoc BOM cua file .ps1 - xem export-ai-output.ps1).
$vn = -join (@(0x0110, 0x00E0, 0x006F, 0x0020, 0x0074, 0x1EA1, 0x006F) | ForEach-Object { [char]$_ })
if (-not $sqlText.Contains($vn)) { throw "Khong thay chuoi tieng Viet mau trong file SQL - dau co the da hong. DUNG LAI." }

Write-Host '=== Buoc 4/4: chep PDF CV builder theo file_url doc tu DB ==='
$fileUrls = Invoke-Psql 'SELECT r.file_url FROM resumes r JOIN cv_builder_documents d ON d.resume_id = r.id ORDER BY r.uploaded_at, r.id;'
$copied = 0
foreach ($line in $fileUrls) {
    $name = Split-Path $line.Trim() -Leaf
    if ([string]::IsNullOrWhiteSpace($name)) { continue }
    $src = Join-Path $UploadsResumesDir $name
    if (-not (Test-Path $src)) { throw "Khong thay file PDF nguon: $src. DUNG LAI." }
    Copy-Item -Path $src -Destination (Join-Path $ResumesOutDir $name) -Force
    Write-Host "  $name"
    $copied++
}
if ($copied -ne $Expected['resumes']) { throw "Chep $copied PDF, ky vong $($Expected['resumes']). DUNG LAI." }

Write-Host ''
Write-Host "Xong. File SQL: $OutputSqlPath ($totalInsert INSERT, $($profileUpdates + $rubricUpdates) UPDATE); $copied PDF -> $ResumesOutDir"
