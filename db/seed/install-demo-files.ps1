<#
db/seed/install-demo-files.ps1

Muc dich: chep file PDF CV demo tu db/seed/resumes/ sang backend/uploads/resumes/ -
buoc chay SAU KHI da nap xong ca ba file SQL theo dung thu tu:
    reset-demo-db.sql -> seed-demo-structural.sql -> seed-demo-ai-output.sql
De resumes.file_url (da nap tu seed-demo-ai-output.sql) tro dung toi file that tren dia.

Doi chieu hai chieu giua database va thu muc dich, giong export-ai-output.ps1:
- Dem so dong file_url doc tu DB, in ra.
- Xoa sach file .pdf cu trong backend/uploads/resumes/ truoc khi chep (tranh sot file
  UUID cu tu lan cai truoc).
- Thieu file nguon (db/seed/resumes/<uuid>.pdf) thi throw.
- Sau khi chep, dem lai so file thuc te, lech so luong thi throw.

Chay lai duoc nhieu lan: moi lan chay xoa sach dich roi chep lai tu dau.

CACH CHAY (tu thu muc goc repo, SAU KHI da nap du 3 file SQL):
    .\db\seed\install-demo-files.ps1

Yeu cau: docker compose dang chay (postgres) va da nap xong ca 3 file SQL.

GHI CHU RANG BUOC POWERSHELL 5.1 (da gap loi that trong export-ai-output.ps1):
- Bat ky bieu thuc '+' hoac '-f' nao la MOT PHAN TU ben trong @(...) deu phai boc
  ngoac don rieng, vi dau phay co do uu tien cao hon '+' chua boc ngoac -
  @('a', 'b' + $x, 'c') se vo thanh 4 phan tu thay vi 3 phan tu nhu tuong.
  Script nay khong co @(...) nao chua bieu thuc nhu vay (chi dung @() rong),
  nhung ghi chu lai de nguoi sua sau nay biet ranh buoc.
- Khong dung ky tu tieng Viet co dau trong ma nguon: file .ps1 khong co BOM,
  Windows PowerShell 5.1 se doc theo ANSI he thong va lam hong chuoi.
#>

$ErrorActionPreference = 'Stop'

$RepoRoot  = (Get-Item $PSScriptRoot).Parent.Parent.FullName
$SourceDir = Join-Path $RepoRoot 'db\seed\resumes'
$DestDir   = Join-Path $RepoRoot 'backend\uploads\resumes'

Write-Host '=== Buoc 1/2: doc danh sach file_url tu database ==='

if (-not (Test-Path $SourceDir)) {
    throw "Khong tim thay thu muc nguon: $SourceDir. Ban co dang chay dung tu thu muc goc repo khong?"
}

$fileUrls = docker compose exec -T postgres psql -U recruitment -d recruitment -t -A -c "SELECT file_url FROM resumes ORDER BY uploaded_at;"
if ($LASTEXITCODE -ne 0) {
    throw "Khong doc duoc file_url tu database (exit code $LASTEXITCODE). Kiem tra docker compose da chay va da nap du 3 file SQL chua."
}

$expectedNames = @()
foreach ($line in $fileUrls) {
    $trimmed = $line.Trim()
    if ([string]::IsNullOrWhiteSpace($trimmed)) { continue }
    $expectedNames += (Split-Path $trimmed -Leaf)
}
$expectedCount = $expectedNames.Count
Write-Host "So dong file_url doc tu DB: $expectedCount"
if ($expectedCount -eq 0) {
    throw "Bang resumes dang RONG - kiem tra da nap seed-demo-ai-output.sql chua truoc khi chay script nay."
}

Write-Host ''
Write-Host '=== Buoc 2/2: doi chieu va chep file PDF ==='

if (-not (Test-Path $DestDir)) {
    New-Item -ItemType Directory -Path $DestDir -Force | Out-Null
    Write-Host "Da tao thu muc: $DestDir"
}

# Xoa sach file .pdf cu truoc khi chep - tranh sot file UUID cu tu lan cai truoc.
$oldPdfs = Get-ChildItem -Path $DestDir -Filter '*.pdf' -ErrorAction SilentlyContinue
if ($oldPdfs) {
    Remove-Item -Path $oldPdfs.FullName -Force
}

$copiedCount = 0
foreach ($baseName in $expectedNames) {
    $sourcePath = Join-Path $SourceDir $baseName
    $destPath   = Join-Path $DestDir $baseName
    if (-not (Test-Path $sourcePath)) {
        throw "Khong tim thay file PDF nguon: $sourcePath (co trong resumes.file_url nhung khong co trong db/seed/resumes/). DUNG LAI - repo co the thieu file, kiem tra lai git."
    }
    Copy-Item -Path $sourcePath -Destination $destPath -Force
    $copiedCount++
}

$actualPdfs  = Get-ChildItem -Path $DestDir -Filter '*.pdf'
$actualCount = $actualPdfs.Count
Write-Host "So file .pdf thuc te trong ${DestDir}: $actualCount"

if ($expectedCount -ne $actualCount) {
    throw "LECH SO LUONG: DB co $expectedCount dong file_url nhung $DestDir co $actualCount file .pdf sau khi chep. DUNG LAI, kiem tra thu cong."
}

Write-Host ''
Write-Host "Xong. Da chep $copiedCount file PDF sang $DestDir - khop voi $expectedCount dong file_url trong DB."
Write-Host "Co the dang nhap va bam 'Xem CV goc' de kiem tra."
