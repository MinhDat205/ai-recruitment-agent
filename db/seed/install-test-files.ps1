# ---------------------------------------------------------------------------
# db/seed/install-test-files.ps1
#
# Cai file CV cho bo du lieu TEST (seed-test.sql) vao backend/uploads/resumes/ -
# noi backend doc file theo resumes.file_url (app.storage.local-path = ./uploads,
# tuong doi theo backend/). SQL khong ghi duoc file len dia nen can buoc nay.
#
# KHONG commit PDF nao cho bo test: 5 file PDF duoi day duoc SINH ngay trong
# script (noi dung ASCII khong dau - font chuan cua PDF khong co glyph tieng
# Viet). Rieng R3 (CV DOCX tieng Viet co dau, da qua pipeline trich xuat that)
# duoc commit san o db/seed/test-files/ va chi chep sang.
#
# Moi file khop dung trang thai CV trong seed-test.sql:
#   R1 (FAILED, EXTRACT_EMPTY)   -> PDF mot trang trang, khong co lop chu
#   R5 (FAILED, EXTRACT_CORRUPT) -> file khong phai PDF
#   R2, R4, R6 (PENDING dong bang) -> PDF mot trang co chu
#   R3 (DONE)                    -> chep db/seed/test-files/cv-mau-quoc-huy.docx
#
# CACH CHAY (PowerShell, tu thu muc goc repo):
#   .\db\seed\install-test-files.ps1
#
# File .ps1 nay co y KHONG chua ky tu tieng Viet co dau (PowerShell 5.1 doc .ps1
# khong BOM bang codepage ANSI - xem walkthrough chore-seed-demo muc 5, loi 4).
# ---------------------------------------------------------------------------

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$targetDir = Join-Path $repoRoot 'backend\uploads\resumes'
$docxSource = Join-Path $PSScriptRoot 'test-files\cv-mau-quoc-huy.docx'

if (-not (Test-Path $docxSource)) {
    throw "Khong tim thay file nguon: $docxSource"
}
New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

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

function Write-TestFile([string] $name, [byte[]] $bytes) {
    $path = Join-Path $targetDir $name
    [System.IO.File]::WriteAllBytes($path, $bytes)
    Write-Host ("Da ghi " + $name + " (" + $bytes.Length + " byte)")
}

$header = 'CV MAU - DU LIEU TEST (ho so hu cau, khong phai nguoi that)'

# R1 - Quoc Huy, ban scan khong co lop chu -> khop EXTRACT_EMPTY
Write-TestFile 'e7000000-0000-0000-0000-000000000001.pdf' (New-MinimalPdf @())

# R2 - Quoc Huy, CV chinh, cho trich xuat (dong bang)
Write-TestFile 'e7000000-0000-0000-0000-000000000002.pdf' (New-MinimalPdf @(
    $header, '', 'QUOC HUY', 'Chuyen vien Cham soc khach hang',
    'Email: quochuy.cv@test.local', 'Kinh nghiem: 03/2022 - nay, Cham soc khach hang'))

# R3 - Quoc Huy, DOCX tieng Viet co dau, da trich xuat that (6b)
Copy-Item -Force $docxSource (Join-Path $targetDir 'e7000000-0000-0000-0000-000000000003.docx')
Write-Host 'Da chep e7000000-0000-0000-0000-000000000003.docx'

# R4 - Le Thi Lan, cho trich xuat (dong bang)
Write-TestFile 'e7000000-0000-0000-0000-000000000004.pdf' (New-MinimalPdf @(
    $header, '', 'LE THI LAN', 'Nhan vien Ho tro khach hang', 'Email: le.thi.lan@test.local'))

# R5 - Pham Van Khoa, file hong -> khop EXTRACT_CORRUPT
Write-TestFile 'e7000000-0000-0000-0000-000000000005.pdf' ([System.Text.Encoding]::ASCII.GetBytes(
    "Day khong phai file PDF - du lieu test cho trang thai EXTRACT_CORRUPT.`n"))

# R6 - Tran Bao Ngoc, cho trich xuat (dong bang)
Write-TestFile 'e7000000-0000-0000-0000-000000000006.pdf' (New-MinimalPdf @(
    $header, '', 'TRAN BAO NGOC', 'Nhan vien Cham soc khach hang', 'Email: tran.bao.ngoc@test.local'))

Write-Host ('Xong: 6 file CV test trong ' + $targetDir)
