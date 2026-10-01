<#
db/seed/export-ai-output.ps1

Muc dich: xuat 13 bang du lieu AI da sinh THAT (D1-D4, F1, F2) tu Postgres dev ra
db/seed/seed-demo-ai-output.sql (tang 2 cua seed demo chore/seed-demo), va chep
file PDF CV that tu backend/uploads/resumes/ sang db/seed/resumes/ - dat ten dung
theo UUID trong resumes.file_url doc truc tiep tu database (khong hardcode UUID).

Chay lai duoc nhieu lan: moi lan chay xoa sach file .pdf cu trong db/seed/resumes/
roi chep lai tu dau, tranh sot file UUID cu tu lan chay truoc (da bi
reset-demo-db.sql xoa khoi DB nhung con nam ngoai dia neu khong don).

DIEU KIEN TRUOC KHI CHAY:
  - docker compose dang chay (postgres) voi du lieu da qua Dot 3-4 (upload CV that,
    nop don, cham diem, sinh giai thich/goi y qua UI that).
  - backend/uploads/resumes/ da co du file PDF tuong ung voi resumes.file_url trong DB.

CACH CHAY (tu thu muc goc repo):
    .\db\seed\export-ai-output.ps1

Ky thuat: pg_dump duoc goi RIENG cho tung bang (mot lenh --table cho moi bang), gop
lai theo DUNG thu tu khoa ngoai ngay BEN TRONG container bang mot script bash duoc
"docker compose cp" vao truoc - khong truyen inline qua "bash -c" vi chuoi bash o day
can dau ngoac kep long nhau ("$OUT") lan bien vong lap, de vo vi quy tac escape khac
nhau giua PowerShell 5.1 va bash (dung loai loi da gap that voi bien psql trong
reset-demo-db.sql). File SQL da gop chi duoc lay ra bang "docker compose cp" - khong
bao gio pipe qua PowerShell - vi PowerShell 5.1 se ma hoa lai theo code page console
va lam mat dau tieng Viet.

Moi buoc kiem chung deu THROW khi phat hien bat thuong, khong chi in canh bao roi
chay tiep - file seed hong ma khong ai nhan ra la loi nguy hiem nhat cua nhanh nay.
#>

$ErrorActionPreference = 'Stop'

$RepoRoot          = (Get-Item $PSScriptRoot).Parent.Parent.FullName
$DbSeedDir         = Join-Path $RepoRoot 'db\seed'
$ResumesOutDir     = Join-Path $DbSeedDir 'resumes'
$UploadsResumesDir = Join-Path $RepoRoot 'backend\uploads\resumes'
$OutputSqlPath     = Join-Path $DbSeedDir 'seed-demo-ai-output.sql'

# Thu tu bang PHAI dung theo khoa ngoai - KHONG duoc sap xep lai theo ten bang.
$TableOrder = @(
    'resumes',
    'resume_parsed_data',
    'job_embeddings',
    'job_recommendations',
    'job_applications',
    'application_status_history',
    'scoring_runs',
    'criterion_scores',
    'score_explanations',
    'score_explanation_attempts',
    'cv_improvement_requests',
    'cv_improvement_suggestions',
    'notifications'
)

Write-Host '=== Buoc 1/4: sinh script bash va dua vao container ==='

$LocalBashScript     = Join-Path $env:TEMP 'export-ai-output.sh'
$ContainerBashScript = '/tmp/export-ai-output.sh'
$ContainerOutputSql  = '/tmp/seed-demo-ai-output.sql'

# Cac dong bash duoi day deu la chuoi PowerShell SINGLE-QUOTED (khong noi suy bien
# PowerShell), chi dung "-f" de chen ten bang - nho vay "$OUT" giu nguyen la bien
# bash chu khong bi PowerShell hieu nham thanh bien cua no.
# QUAN TRONG: '+' phai duoc BOC NGOAC khi la mot phan tu ben trong @(...) - dau
# phay co do uu tien cao hon '+' chua boc ngoac, nen @('a', 'b' + $x, 'c') KHONG
# cho ra 3 phan tu nhu tuong, ma vo thanh 4 phan tu ('a', 'b', gia tri cua $x, 'c')
# vi '+' bi tach khoi 'b'. Da gap loi nay that (OUT= va duong dan tach thanh hai
# dong rieng trong script bash, khien bash chay nham /tmp/....sql nhu mot LENH
# thay vi lam muc tieu redirect).
$bashLines = @('set -e', ('OUT=' + $ContainerOutputSql), '> "$OUT"')
foreach ($t in $TableOrder) {
    $bashLines += ('echo "-- ================================================================" >> "$OUT"')
    $bashLines += ('echo "-- Bang: {0}" >> "$OUT"' -f $t)
    $bashLines += ('echo "-- ================================================================" >> "$OUT"')
    $bashLines += ('pg_dump -U recruitment -d recruitment --data-only --column-inserts --no-owner --table={0} >> "$OUT"' -f $t)
    $bashLines += ('echo "" >> "$OUT"')
}
# QUAN TRONG: khong dung Set-Content de ghi script bash - tren Windows PowerShell
# 5.1, Set-Content luon noi dong bang CRLF ke ca khi chi dinh -Encoding ASCII. Bash
# doc dong "set -e\r" thi \r dinh vao cuoi tham so, "set" nhan chuoi option khong
# hop le va vo ngay dong dau (da gap loi nay that khi chay thu). Ghi bang
# [System.IO.File]::WriteAllText voi noi dong "`n" thuan de dam bao LF-only.
$bashContent = ($bashLines -join "`n") + "`n"
[System.IO.File]::WriteAllText($LocalBashScript, $bashContent, [System.Text.Encoding]::ASCII)

# Kiem chung NGAY TAI CHO, truoc khi dua vao container: loi vo phan tu mang (nhu
# da gap voi bien OUT=) ghi ra file van "trong hop le" - chi lo khi bash chay that
# va that bai voi thong bao kho hieu. Bat truc tiep bang cach doc lai dong thu hai.
$writtenLines = Get-Content -Path $LocalBashScript -Encoding ASCII
if ($writtenLines.Count -lt 2 -or $writtenLines[1].Length -le 4 -or -not $writtenLines[1].StartsWith('OUT=/tmp/')) {
    $actualLine2 = if ($writtenLines.Count -ge 2) { $writtenLines[1] } else { '(khong co dong thu hai)' }
    throw "Script bash vua ghi SAI cau truc: dong thu hai phai la 'OUT=/tmp/...' (dai hon 4 ky tu), nhung thuc te la: '$actualLine2'. DUNG LAI truoc khi dua vao container."
}

docker compose cp $LocalBashScript "postgres:$ContainerBashScript"
if ($LASTEXITCODE -ne 0) {
    throw "Khong dua duoc script bash vao container (exit code $LASTEXITCODE). DUNG LAI."
}

Write-Host '=== Buoc 2/4: chay pg_dump rieng tung bang, gop trong container (dung thu tu khoa ngoai) ==='
docker compose exec -T postgres bash $ContainerBashScript
if ($LASTEXITCODE -ne 0) {
    throw "Chay script export trong container that bai (exit code $LASTEXITCODE). Xem thong bao loi phia tren. DUNG LAI."
}

Write-Host '=== Buoc 3/4: lay DUY NHAT MOT file SQL da gop ra repo bang docker compose cp (khong pipe qua PowerShell) ==='
docker compose cp "postgres:$ContainerOutputSql" $OutputSqlPath
if ($LASTEXITCODE -ne 0) {
    throw "Khong lay duoc file SQL da gop ra khoi container (exit code $LASTEXITCODE). DUNG LAI."
}

Write-Host '=== Buoc 4/4: doi chieu hai chieu PDF <-> database, roi chep file theo file_url doc tu DB ==='
if (-not (Test-Path $ResumesOutDir)) {
    New-Item -ItemType Directory -Path $ResumesOutDir -Force | Out-Null
}

$fileUrls = docker compose exec -T postgres psql -U recruitment -d recruitment -t -A -c "SELECT file_url FROM resumes ORDER BY uploaded_at;"
$expectedNames = @()
foreach ($line in $fileUrls) {
    $trimmed = $line.Trim()
    if ([string]::IsNullOrWhiteSpace($trimmed)) { continue }
    $expectedNames += (Split-Path $trimmed -Leaf)
}
$expectedCount = $expectedNames.Count
Write-Host "So dong file_url doc tu DB: $expectedCount"

# Xoa sach file .pdf cu truoc khi chep - tranh sot file UUID cu tu lan chay truoc.
$oldPdfs = Get-ChildItem -Path $ResumesOutDir -Filter '*.pdf' -ErrorAction SilentlyContinue
if ($oldPdfs) {
    Remove-Item -Path $oldPdfs.FullName -Force
}

$copiedCount = 0
foreach ($baseName in $expectedNames) {
    $sourcePath = Join-Path $UploadsResumesDir $baseName
    $destPath   = Join-Path $ResumesOutDir $baseName
    if (-not (Test-Path $sourcePath)) {
        throw "Khong tim thay file PDF nguon: $sourcePath (co trong resumes.file_url nhung khong co tren dia). Dung lai - kiem tra backend\uploads\resumes\ da co du file chua."
    }
    Copy-Item -Path $sourcePath -Destination $destPath -Force
    $copiedCount++
}

$actualPdfs  = Get-ChildItem -Path $ResumesOutDir -Filter '*.pdf'
$actualCount = $actualPdfs.Count
Write-Host "So file .pdf thuc te trong ${ResumesOutDir}: $actualCount"

if ($expectedCount -ne $actualCount) {
    throw "LECH SO LUONG: DB co $expectedCount dong file_url nhung db/seed/resumes/ co $actualCount file .pdf sau khi chep. Nguyen nhan thuong gap: ten file trung nhau hoac loi chep giua chung. DUNG LAI, kiem tra thu cong."
}

Write-Host "Da chep $copiedCount file PDF sang $ResumesOutDir - khop voi $expectedCount dong file_url trong DB."

Write-Host ''
Write-Host '=== KIEM CHUNG FILE SQL ==='

$sqlLines = Get-Content -Path $OutputSqlPath -Encoding UTF8
$sqlText  = Get-Content -Path $OutputSqlPath -Raw -Encoding UTF8

$totalInsert = ($sqlLines | Select-String -Pattern '^INSERT INTO').Count
Write-Host "Tong so dong INSERT INTO: $totalInsert"
if ($totalInsert -eq 0) {
    throw "Tong so dong INSERT INTO = 0 - file seed-demo-ai-output.sql rong hoac xuat that bai. DUNG LAI."
}

Write-Host ''
Write-Host 'So dong INSERT INTO tung bang (dem tren file SQL da xuat):'
# pg_dump ghi ten bang co tien to schema (vd "INSERT INTO public.resumes (...)"),
# khong phai "INSERT INTO resumes (...)" - da gap loi dem ra 0 o moi bang vi thieu
# "public." trong pattern, trong khi tong dem chung (khong rang buoc ten bang) van
# dung. Them buoc doi chieu tong theo bang voi tong chung de bat loi loai nay ngay,
# khong de no am tham troi qua (kiem chung ma van cho ra 0 het con te hon khong kiem).
$sumPerTable = 0
foreach ($t in $TableOrder) {
    $pattern = 'INSERT INTO public.' + $t + ' '
    $count = ([regex]::Matches($sqlText, [regex]::Escape($pattern))).Count
    Write-Host ("  {0,-32} {1}" -f $t, $count)
    $sumPerTable += $count
}
if ($sumPerTable -ne $totalInsert) {
    throw "LECH SO DONG: tong dem theo tung bang ($sumPerTable) khac tong dem chung ($totalInsert). Co the pattern khong khop dinh dang thuc te cua pg_dump, hoac co bang ngoai danh sach TableOrder. DUNG LAI, kiem tra lai pattern truoc khi tin so lieu tung bang."
}

Write-Host ''
Write-Host 'Kiem tra dau tieng Viet con nguyen:'
# Dung ky tu dung tu MA (khong go literal chu co dau vao source .ps1) de phep kiem
# nay KHONG phu thuoc file .ps1 nay co BOM UTF-8 hay khong - Windows PowerShell 5.1
# doc file .ps1 khong BOM bang codepage ANSI he thong, lam sai lech moi literal co
# dau go thang trong ma nguon (da gap that: mot ho ten tieng Viet go thang bi
# doc sai thanh chuoi mojibake luc parse, khien phep so khop luon that bai du
# du lieu SQL van dung).
# Ai do sua file .ps1 nay bang editor khac lam mat BOM sau nay van khong lam phep
# kiem nay sai theo kieu am tham, vi no khong doc lai literal tu source nua.
$vnChars = @(0x004E, 0x0067, 0x0075, 0x0079, 0x1EC5, 0x006E) | ForEach-Object { [char]$_ }
$vnPattern = -join $vnChars
$vnMatch = $sqlLines | Select-String -Pattern $vnPattern -SimpleMatch | Select-Object -First 1
if (-not $vnMatch) {
    throw "KHONG TIM THAY chuoi co dau tieng Viet mau (dung tu ma ky tu, khong phu thuoc BOM) trong file SQL da xuat - dau tieng Viet co the da bi hong. DUNG LAI, kiem tra lai qua trinh export truoc khi dung file nay."
}
Write-Host ("  TIM THAY (mau '" + $vnPattern + "'): " + $vnMatch.Line.Substring(0, [Math]::Min(160, $vnMatch.Line.Length)))

Write-Host ''
Write-Host '=== KIEM CHUNG VECTOR (hoi thang database, dung rieng cho tung bang) ==='

$jobEmbRaw = docker compose exec -T postgres psql -U recruitment -d recruitment -t -A -c "SELECT count(*), min(vector_dims(embedding)), max(vector_dims(embedding)) FROM job_embeddings;"
if ($LASTEXITCODE -ne 0) {
    throw "Loi khi truy van vector_dims cho job_embeddings (xem thong bao psql o tren). Co the ham vector_dims khong ton tai trong extension pgvector dang cai. DUNG LAI, khong tu doi cach kiem tra khac."
}
$jobEmbParts = ($jobEmbRaw | Select-Object -First 1).ToString().Trim().Split('|')
Write-Host "job_embeddings            - count=$($jobEmbParts[0]) min_dims=$($jobEmbParts[1]) max_dims=$($jobEmbParts[2])"
if ($jobEmbParts[0] -eq '0' -or $jobEmbParts[1] -ne '1536' -or $jobEmbParts[2] -ne '1536') {
    throw "job_embeddings: vector khong dung 1536 chieu hoac khong co dong nao (count=$($jobEmbParts[0]), min=$($jobEmbParts[1]), max=$($jobEmbParts[2])). DUNG LAI."
}

$resumeEmbRaw = docker compose exec -T postgres psql -U recruitment -d recruitment -t -A -c "SELECT count(*), min(vector_dims(embedding)), max(vector_dims(embedding)) FROM resume_parsed_data WHERE embedding IS NOT NULL;"
if ($LASTEXITCODE -ne 0) {
    throw "Loi khi truy van vector_dims cho resume_parsed_data (xem thong bao psql o tren). DUNG LAI, khong tu doi cach kiem tra khac."
}
$resumeEmbParts = ($resumeEmbRaw | Select-Object -First 1).ToString().Trim().Split('|')
Write-Host "resume_parsed_data        - count=$($resumeEmbParts[0]) min_dims=$($resumeEmbParts[1]) max_dims=$($resumeEmbParts[2])"
if ($resumeEmbParts[0] -eq '0' -or $resumeEmbParts[1] -ne '1536' -or $resumeEmbParts[2] -ne '1536') {
    throw "resume_parsed_data: vector khong dung 1536 chieu hoac khong co dong nao (count=$($resumeEmbParts[0]), min=$($resumeEmbParts[1]), max=$($resumeEmbParts[2])). DUNG LAI."
}

Write-Host ''
Write-Host '=== KIEM CHUNG FR-C05 (schema CV v2, kinh nghiem, danh muc Job) ==='
# pg_dump --column-inserts tu dua cac cot moi cua V8 (industry_code, region_code, experience_*) vao
# INSERT - khong can khai rieng. resume_reparse_requests CO Y khong nam trong $TableOrder: do la hang
# doi van hanh, khong phai output AI (REQUIREMENT FR-C05 muc 4, buoc 4).
if ($TableOrder -contains 'resume_reparse_requests') {
    throw "resume_reparse_requests KHONG duoc nam trong dump (hang doi van hanh, khong phai output AI). DUNG LAI."
}

$c05Raw = docker compose exec -T postgres psql -U recruitment -d recruitment -t -A -c "SELECT count(*), count(*) FILTER (WHERE prompt_version = 'resume-parse-v2'), count(*) FILTER (WHERE experience_computed_at IS NOT NULL) FROM resume_parsed_data;"
if ($LASTEXITCODE -ne 0) {
    throw "Loi khi truy van kiem chung FR-C05 tren resume_parsed_data. DUNG LAI."
}
$c05Parts = ($c05Raw | Select-Object -First 1).ToString().Trim().Split('|')
Write-Host "resume_parsed_data        - tong=$($c05Parts[0]) v2=$($c05Parts[1]) da_tinh_kinh_nghiem=$($c05Parts[2])"
if ($c05Parts[0] -ne $c05Parts[1]) {
    throw "Con ban ghi resume_parsed_data chua o resume-parse-v2 ($($c05Parts[1])/$($c05Parts[0])). Goi reparse cho moi CV truoc khi xuat. DUNG LAI."
}
if ($c05Parts[0] -ne $c05Parts[2]) {
    throw "Con ban ghi chua tinh so thang kinh nghiem ($($c05Parts[2])/$($c05Parts[0])). Cho job nen ResumeExperienceScheduler chay xong. DUNG LAI."
}

$jobRaw = docker compose exec -T postgres psql -U recruitment -d recruitment -t -A -c "SELECT count(*) FROM jobs WHERE deleted_at IS NULL AND category_code IS NULL;"
$jobMissing = ($jobRaw | Select-Object -First 1).ToString().Trim()
Write-Host "jobs thieu category_code  - $jobMissing"
if ($jobMissing -ne '0') {
    throw "Con $jobMissing job chua co category_code (REQUIREMENT FR-C05 muc 7.11). DUNG LAI."
}

Write-Host ''
Write-Host "Xong. File SQL: $OutputSqlPath"
Write-Host "Thu muc PDF: $ResumesOutDir ($copiedCount file, khop DB)"
