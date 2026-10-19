<#
db/seed/install-demo-files.ps1

Muc dich: chep file cua bo du lieu DEMO tu repo sang backend/uploads/ - buoc chay SAU KHI
da nap xong cac file SQL demo (reset-demo-db.sql -> seed-demo-structural.sql ->
seed-demo-ai-output.sql, va cac tang sau neu co). SQL khong ghi duoc file len dia nen can
buoc nay.

Nguon -> dich (thu muc nguon chua co thi bo qua em):
    db/seed/resumes/                      -> backend/uploads/resumes/
    db/seed/demo-files/logos/             -> backend/uploads/logos/
    db/seed/demo-files/message-attachments/ -> backend/uploads/message-attachments/

CHI ghi de file co trong nguon (cung ten). KHONG xoa file nao khac trong backend/uploads/ -
file cua bo test (install-test-files.ps1) hay file tai len qua UI giu nguyen. Vi vay khong con
rang buoc thu tu voi install-test-files.ps1.

Sau khi chep, doi chieu voi DB: moi khoa file cua DU LIEU DEMO phai co file dich, thieu thi
throw (liet ke du cac khoa thieu). Du lieu demo xac dinh theo dai id:
    user demo    : d0000000-% (tang 1), d1b00000-% (tang 1b)
    cong ty demo : d1000000-% (tang 1), d1b00000-% (tang 1b)
    resumes.file_url               ("resumes/<ten>")              - CV cua user demo
    companies.logo_url             ("/uploads/logos/<ten>")       - cong ty demo
    application_messages.attachment_key ("message-attachments/<ten>") - tin cua don vao tin
                                                                      tuyen dung cua cong ty demo
Khoa logo la URL cong khai (LocalStorageService.store tra "/uploads/" + thu muc + ten), khac
hai khoa con lai (khoa tuong doi, ResumeService/MessageService tu ghep).

Chay lai duoc nhieu lan.

CACH CHAY (tu thu muc goc repo, SAU KHI da nap du cac file SQL demo):
    .\db\seed\install-demo-files.ps1

Yeu cau: docker compose dang chay (postgres).

GHI CHU RANG BUOC POWERSHELL 5.1 (da gap loi that trong export-ai-output.ps1):
- Bat ky bieu thuc '+' hoac '-f' nao la MOT PHAN TU ben trong @(...) deu phai boc
  ngoac don rieng, vi dau phay co do uu tien cao hon '+' chua boc ngoac -
  @('a', 'b' + $x, 'c') se vo thanh 4 phan tu thay vi 3 phan tu nhu tuong.
- Khong dung ky tu tieng Viet co dau trong ma nguon: file .ps1 khong co BOM,
  Windows PowerShell 5.1 se doc theo ANSI he thong va lam hong chuoi.
#>

$ErrorActionPreference = 'Stop'

$RepoRoot   = (Get-Item $PSScriptRoot).Parent.Parent.FullName
$UploadsDir = Join-Path $RepoRoot 'backend\uploads'

# Cap nguon (trong repo) -> thu muc con duoi backend/uploads/.
$Sources = @(
    @{ Source = (Join-Path $RepoRoot 'db\seed\resumes');                         Sub = 'resumes' },
    @{ Source = (Join-Path $RepoRoot 'db\seed\demo-files\logos');               Sub = 'logos' },
    @{ Source = (Join-Path $RepoRoot 'db\seed\demo-files\message-attachments'); Sub = 'message-attachments' }
)

Write-Host '=== Buoc 1/2: chep file tu nguon cua seed (chi ghi de file cung ten) ==='

$copiedCount = 0
foreach ($pair in $Sources) {
    if (-not (Test-Path $pair.Source)) {
        Write-Host ("Bo qua (chua co thu muc nguon): " + $pair.Source)
        continue
    }
    $files = @(Get-ChildItem -Path $pair.Source -File)
    if ($files.Count -eq 0) {
        Write-Host ("Bo qua (thu muc nguon rong): " + $pair.Source)
        continue
    }
    $destDir = Join-Path $UploadsDir $pair.Sub
    New-Item -ItemType Directory -Path $destDir -Force | Out-Null
    foreach ($f in $files) {
        Copy-Item -Path $f.FullName -Destination (Join-Path $destDir $f.Name) -Force
        $copiedCount++
    }
    Write-Host ("Da chep " + $files.Count + " file -> " + $destDir)
}

Write-Host ''
Write-Host '=== Buoc 2/2: doi chieu khoa file cua du lieu demo trong DB voi file dich ==='

# Moi dong: <loai>|<khoa>. ORDER BY ket thuc bang khoa (duy nhat trong tung loai).
$query = @"
SELECT 'resume|' || r.file_url FROM resumes r
 WHERE r.candidate_id::text LIKE 'd0000000-%' OR r.candidate_id::text LIKE 'd1b00000-%'
UNION ALL
SELECT 'logo|' || c.logo_url FROM companies c
 WHERE c.logo_url IS NOT NULL
   AND (c.id::text LIKE 'd1000000-%' OR c.id::text LIKE 'd1b00000-%')
UNION ALL
SELECT 'attachment|' || m.attachment_key FROM application_messages m
  JOIN job_applications a ON a.id = m.application_id
  JOIN jobs j ON j.id = a.job_id
 WHERE m.attachment_key IS NOT NULL
   AND (j.company_id::text LIKE 'd1000000-%' OR j.company_id::text LIKE 'd1b00000-%')
ORDER BY 1;
"@

Push-Location $RepoRoot
try {
    $rows = docker compose exec -T postgres psql -U recruitment -d recruitment -t -A -v ON_ERROR_STOP=1 -c $query
    $exit = $LASTEXITCODE
} finally {
    Pop-Location
}
if ($exit -ne 0) {
    throw "Khong doc duoc khoa file tu database (exit code $exit). Kiem tra docker compose da chay va da nap du cac file SQL demo chua."
}

$counts  = @{ resume = 0; logo = 0; attachment = 0 }
$missing = @()
foreach ($line in $rows) {
    $trimmed = $line.Trim()
    if ([string]::IsNullOrWhiteSpace($trimmed)) { continue }
    $parts = $trimmed.Split('|', 2)
    $kind = $parts[0]
    $key  = $parts[1]
    if ($kind -eq 'logo') {
        if (-not $key.StartsWith('/uploads/')) {
            $missing += ("logo (khoa khong bat dau bang /uploads/): " + $key)
            continue
        }
        $relative = $key.Substring('/uploads/'.Length)
    } else {
        $relative = $key
    }
    $counts[$kind]++
    $target = Join-Path $UploadsDir ($relative.Replace('/', '\'))
    if (-not (Test-Path $target -PathType Leaf)) {
        $missing += ($kind + ": " + $key)
    }
}

Write-Host ("Khoa file cua du lieu demo trong DB: resume " + $counts['resume'] + ", logo " + $counts['logo'] + ", attachment " + $counts['attachment'])

if ($counts['resume'] -eq 0) {
    throw "Khong co CV demo nao trong DB - kiem tra da nap seed-demo-ai-output.sql chua truoc khi chay script nay."
}
if ($missing.Count -gt 0) {
    Write-Host ''
    Write-Host ("THIEU " + $missing.Count + " file dich:")
    foreach ($m in $missing) { Write-Host ("  " + $m) }
    throw ("THIEU FILE: " + $missing.Count + " khoa file cua du lieu demo khong co file trong $UploadsDir. Repo co the thieu file nguon - kiem tra db/seed/resumes/ va db/seed/demo-files/.")
}

Write-Host ''
Write-Host "Xong. Da chep $copiedCount file; moi khoa file cua du lieu demo deu co file trong $UploadsDir."
