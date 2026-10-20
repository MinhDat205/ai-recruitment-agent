# ---------------------------------------------------------------------------
# db/seed/install-test-files.ps1
#
# Cai file cho bo du lieu TEST (seed-test.sql) vao backend/uploads/ - noi backend
# doc file theo resumes.file_url / application_messages.attachment_key
# (app.storage.local-path = ./uploads, tuong doi theo backend/). SQL khong ghi duoc
# file len dia nen can buoc nay.
#
# KHONG commit PDF/PNG nao cho bo test: cac file duoi day duoc SINH ngay trong
# script (noi dung ASCII khong dau - font chuan cua PDF khong co glyph tieng
# Viet; PNG la chuoi base64 co dinh). Rieng R3 (CV DOCX tieng Viet co dau, da qua
# pipeline trich xuat that) duoc commit san o db/seed/test-files/ va chi chep sang.
#
# Moi file khop dung trang thai trong seed-test.sql:
#   R1 (FAILED, EXTRACT_EMPTY)   -> PDF mot trang trang, khong co lop chu
#   R5 (FAILED, EXTRACT_CORRUPT) -> file khong phai PDF
#   R2, R4, R6 (PENDING dong bang) -> PDF mot trang co chu
#   R3 (DONE)                    -> chep db/seed/test-files/cv-mau-quoc-huy.docx
#   Dai f2 (muc 17): 2 CV PENDING dong bang (resumes/f2000000-0019-...) va 3 tep
#   dinh kem (message-attachments/f2000000-0014-...): 2 PDF, 1 PNG.
#   SO BYTE cua 2 CV va 3 tep dinh kem f2 duoc GHI CUNG trong seed-test.sql
#   (file_size / attachment_size - L22): sua noi dung sinh o day thi phai sua
#   so byte trong SQL; buoc 2 se bao lech.
#
# Buoc 2 (L24): doi chieu CHI DOC voi DB - moi khoa file cua bo test (CV cua user
# test e0.../f2..., tep dinh kem cua tin thuoc cong ty test e1.../f2...) phai co
# file tren dia, va voi dai f2 so byte file phai bang so byte trong DB. Lech thi
# in tung khoa/duong dan lech va thoat ma 1. Script chi ghi file cua chinh no,
# khong xoa file nao.
#
# CACH CHAY (PowerShell, tu thu muc goc repo, SAU KHI nap seed-test.sql):
#   .\db\seed\install-test-files.ps1
#
# File .ps1 nay co y KHONG chua ky tu tieng Viet co dau (PowerShell 5.1 doc .ps1
# khong BOM bang codepage ANSI - xem walkthrough chore-seed-demo muc 5, loi 4).
# ---------------------------------------------------------------------------

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$uploadsDir = Join-Path $repoRoot 'backend\uploads'
$targetDir = Join-Path $uploadsDir 'resumes'
$attachmentDir = Join-Path $uploadsDir 'message-attachments'
$docxSource = Join-Path $PSScriptRoot 'test-files\cv-mau-quoc-huy.docx'

if (-not (Test-Path $docxSource)) {
    throw "Khong tim thay file nguon: $docxSource"
}
New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
New-Item -ItemType Directory -Force -Path $attachmentDir | Out-Null

# Dung PDF 1.4 toi thieu: 1 trang A4, font Helvetica chuan. Offset bang xref
# tinh tu do dai byte thuc (ASCII nen 1 ky tu = 1 byte).
function New-MinimalPdf([string[]] $lines) {
    $content = ''
    if ($lines.Count -gt 0) {
        $content = "BT /F1 12 Tf 56 780 Td 16 TL`n"
        foreach ($line in $lines) {
            $escaped = $line.Replace('\', '\\').Replace('(', '\(').Replace(')', '\)')
            $content += "($escaped) Tj T*`n"
        }
        $content += "ET`n"
    }
    $objects = @(
        '<< /Type /Catalog /Pages 2 0 R >>',
        '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
        '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
        '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>',
        ("<< /Length " + $content.Length + " >>`nstream`n" + $content + "endstream")
    )
    $pdf = "%PDF-1.4`n"
    $offsets = @()
    for ($i = 0; $i -lt $objects.Count; $i++) {
        $offsets += $pdf.Length
        $pdf += ('' + ($i + 1) + " 0 obj`n" + $objects[$i] + "`nendobj`n")
    }
    $xrefStart = $pdf.Length
    $pdf += "xref`n0 " + ($objects.Count + 1) + "`n0000000000 65535 f `n"
    foreach ($o in $offsets) {
        $pdf += ('{0:D10} 00000 n `n' -f $o)
    }
    $pdf += "trailer`n<< /Size " + ($objects.Count + 1) + " /Root 1 0 R >>`nstartxref`n" + $xrefStart + "`n%%EOF`n"
    return [System.Text.Encoding]::ASCII.GetBytes($pdf)
}

function Write-TestFile([string] $dir, [string] $name, [byte[]] $bytes) {
    $path = Join-Path $dir $name
    [System.IO.File]::WriteAllBytes($path, $bytes)
    Write-Host ("Da ghi " + $name + " (" + $bytes.Length + " byte)")
}

$header = 'CV MAU - DU LIEU TEST (ho so hu cau, khong phai nguoi that)'
$attachmentHeader = 'TEP DINH KEM MAU - DU LIEU TEST (hu cau)'

Write-Host '=== Buoc 1/2: sinh/chep file cua bo test ==='

# R1 - Quoc Huy, ban scan khong co lop chu -> khop EXTRACT_EMPTY
Write-TestFile $targetDir 'e7000000-0000-0000-0000-000000000001.pdf' (New-MinimalPdf @())

# R2 - Quoc Huy, CV chinh, cho trich xuat (dong bang)
Write-TestFile $targetDir 'e7000000-0000-0000-0000-000000000002.pdf' (New-MinimalPdf @(
    $header, '', 'QUOC HUY', 'Chuyen vien Cham soc khach hang',
    'Email: quochuy.cv@test.local', 'Kinh nghiem: 03/2022 - nay, Cham soc khach hang'))

# R3 - Quoc Huy, DOCX tieng Viet co dau, da trich xuat that (6b)
Copy-Item -Force $docxSource (Join-Path $targetDir 'e7000000-0000-0000-0000-000000000003.docx')
Write-Host 'Da chep e7000000-0000-0000-0000-000000000003.docx'

# R4 - Le Thi Lan, cho trich xuat (dong bang)
Write-TestFile $targetDir 'e7000000-0000-0000-0000-000000000004.pdf' (New-MinimalPdf @(
    $header, '', 'LE THI LAN', 'Nhan vien Ho tro khach hang', 'Email: le.thi.lan@test.local'))

# R5 - Pham Van Khoa, file hong -> khop EXTRACT_CORRUPT
Write-TestFile $targetDir 'e7000000-0000-0000-0000-000000000005.pdf' ([System.Text.Encoding]::ASCII.GetBytes(
    "Day khong phai file PDF - du lieu test cho trang thai EXTRACT_CORRUPT.`n"))

# R6 - Tran Bao Ngoc, cho trich xuat (dong bang)
Write-TestFile $targetDir 'e7000000-0000-0000-0000-000000000006.pdf' (New-MinimalPdf @(
    $header, '', 'TRAN BAO NGOC', 'Nhan vien Cham soc khach hang', 'Email: tran.bao.ngoc@test.local'))

# Dai f2 (muc 17) - CV chinh cua 2 ung vien cong ty test 2, cho trich xuat (dong bang)
Write-TestFile $targetDir 'f2000000-0019-0000-0000-000000000001.pdf' (New-MinimalPdf @(
    $header, '', 'VU HOANG NAM', 'Nhan vien Ho tro khach hang', 'Email: vu.hoang.nam@test.local'))
Write-TestFile $targetDir 'f2000000-0019-0000-0000-000000000002.pdf' (New-MinimalPdf @(
    $header, '', 'DO MINH CHAU', 'Nhan vien Ho tro khach hang', 'Email: do.minh.chau@test.local'))

# Dai f2 - tep dinh kem tin nhan. Ten luu = <id tin>.<duoi chuan> (app dung uuid ngau nhien - K2).
# 0014-01 HR -> Do Minh Chau (don -02): PDF
Write-TestFile $attachmentDir 'f2000000-0014-0000-0000-000000000001.pdf' (New-MinimalPdf @(
    $attachmentHeader, '', 'HUONG DAN CHUAN BI PHONG VAN',
    '1. Mang theo CCCD va ban sao bang tot nghiep.', '2. Den truoc gio hen 15 phut.'))
# 0014-02 Do Minh Chau -> HR (don -02): PNG 64x64 co dinh
Write-TestFile $attachmentDir 'f2000000-0014-0000-0000-000000000002.png' ([Convert]::FromBase64String(
    'iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAD4SURBVHhe7dChbQMAEAPADNZJMlPmLCoNTvgRq8zSGxwxseXH79/7c9nD4JodYHDNDjC4ZgcYXLMDDK7ZAQbX/PuAn+ermnuTHWCQWNjGvckOMEgsbOPeZAcYJBa2cW+yAwwSC9u4N9kBBomFbdyb7ACDxMI27k12gEFiYRv3JjvAILGwjXuTHWCQWNjGvckOMEgsbOPeZAcYJBa2cW+yAwwSC9u4N9kBBomFbdyb7ACDxMI27k12gEFiYRv3JjvAILGwjXuTHWCQWNjGvckOMEgsbOPeZAcYJBa2cW+yAwyu2QEG1+wAg2t2gME1O8Dgmh1gcM35A75PsBuvdsiEzAAAAABJRU5ErkJggg=='))
# 0014-03 Vu Hoang Nam -> HR (don -01, dang an danh): PDF
Write-TestFile $attachmentDir 'f2000000-0014-0000-0000-000000000003.pdf' (New-MinimalPdf @(
    $attachmentHeader, '', 'BANG DIEM TOT NGHIEP', 'Ho ten: VU HOANG NAM', 'Diem trung binh: 7.8/10'))

Write-Host ''
Write-Host '=== Buoc 2/2: doi chieu khoa file cua bo test trong DB voi file tren dia (chi doc) ==='

# Moi dong: <loai>|<khoa>|<so byte trong DB>|<co kiem so byte: 1/0>. Chi kiem so byte cho dai f2 (L22) - file
# dai e... sinh tu truoc dot 4, so byte trong DB khong thuoc pham vi doi chieu nay. ORDER BY ket thuc bang khoa.
$query = @"
SELECT 'resume|' || r.file_url || '|' || COALESCE(r.file_size::text, '') || '|'
       || CASE WHEN r.id::text LIKE 'f2000000-%' THEN '1' ELSE '0' END
  FROM resumes r
 WHERE r.candidate_id::text LIKE 'e0000000-%' OR r.candidate_id::text LIKE 'f2000000-0001-%'
UNION ALL
SELECT 'attachment|' || m.attachment_key || '|' || m.attachment_size::text || '|'
       || CASE WHEN m.id::text LIKE 'f2000000-%' THEN '1' ELSE '0' END
  FROM application_messages m
  JOIN job_applications a ON a.id = m.application_id
  JOIN jobs j ON j.id = a.job_id
 WHERE m.attachment_key IS NOT NULL
   AND (j.company_id::text LIKE 'e1000000-%' OR j.company_id::text LIKE 'f2000000-0002-%')
ORDER BY 1;
"@

Push-Location $repoRoot
try {
    $rows = docker compose exec -T postgres psql -U recruitment -d recruitment -t -A -v ON_ERROR_STOP=1 -c $query
    $exit = $LASTEXITCODE
} finally {
    Pop-Location
}
if ($exit -ne 0) {
    Write-Host "Khong doc duoc khoa file tu database (exit code $exit). Kiem tra docker compose da chay va da nap seed-test.sql chua."
    exit 1
}

$counts = @{ resume = 0; attachment = 0 }
$mismatches = @()
foreach ($line in $rows) {
    $trimmed = $line.Trim()
    if ([string]::IsNullOrWhiteSpace($trimmed)) { continue }
    $parts = $trimmed.Split('|')
    $kind = $parts[0]
    $key = $parts[1]
    $dbSize = $parts[2]
    $checkSize = $parts[3] -eq '1'
    $counts[$kind]++
    $target = Join-Path $uploadsDir ($key.Replace('/', '\'))
    if (-not (Test-Path $target -PathType Leaf)) {
        $mismatches += ($kind + ' thieu file: ' + $key + ' -> ' + $target)
        continue
    }
    if ($checkSize) {
        $diskSize = (Get-Item $target).Length
        if ([string]$diskSize -ne $dbSize) {
            $mismatches += ($kind + ' lech so byte: ' + $key + ' (DB ' + $dbSize + ', dia ' + $diskSize + ') -> ' + $target)
        }
    }
}

Write-Host ('Khoa file cua bo test trong DB: resume ' + $counts['resume'] + ', attachment ' + $counts['attachment'])

if ($counts['resume'] -eq 0) {
    Write-Host 'Khong co CV test nao trong DB - kiem tra da nap seed-test.sql chua.'
    exit 1
}
if ($mismatches.Count -gt 0) {
    Write-Host ''
    Write-Host ('LECH ' + $mismatches.Count + ' khoa file:')
    foreach ($m in $mismatches) { Write-Host ('  ' + $m) }
    exit 1
}

Write-Host ('Xong: moi khoa file cua bo test deu co file trong ' + $uploadsDir + '; so byte dai f2 khop DB.')
