# Sinh docs/ER.md tu JSON do docs/er/schema-export.sql xuat ra (catalog Postgres). Cach chay: phu luc cua docs/ER.md.
# File nay luu UTF-8 co BOM: PowerShell 5.1 doc file khong BOM theo ANSI, lam hong chuoi tieng Viet co dau.
param(
    [Parameter(Mandatory)] [string]$SchemaJson,
    [Parameter(Mandatory)] [string]$OutFile,
    # Tuy chon: ghi them tung so do ra file .mmd de render thu bang mermaid-cli
    [string]$MmdDir
)
$ErrorActionPreference = 'Stop'

# [IO.File] dung thu muc lam viec cua tien trinh, khong phai thu muc hien tai cua PowerShell - doi sang duong dan tuyet doi
$OutFile = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutFile)
if ($MmdDir) { $MmdDir = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($MmdDir) }

$schema = Get-Content $SchemaJson -Raw -Encoding UTF8 | ConvertFrom-Json
$tables = @{}
foreach ($t in $schema.tables) { $tables[$t.name] = $t }

$domains = @(
    @{ No = 1; Title = 'Tài khoản và công ty'; Packages = '`auth/`, `user/`, `company/`, hồ sơ nghề nghiệp (FR-U14)';
       Tables = @('users', 'companies', 'candidate_profiles') },
    @{ No = 2; Title = 'Tin tuyển dụng, rubric, sàng lọc'; Packages = '`job/`, `rubric/`, `interviewtemplate/`, `screening/`, `jobdraft/`, `blindreview/`';
       Tables = @('jobs', 'rubrics', 'rubric_criteria', 'interview_templates', 'job_screening_questions', 'job_ai_generations', 'job_blind_review_changes') },
    @{ No = 3; Title = 'CV, trích xuất, embedding, CV builder'; Packages = '`resume/`, `ai/embedding/`, `ai/cvimprovement/`, `cvbuilder/`';
       Tables = @('resumes', 'resume_parsed_data', 'resume_reparse_requests', 'cv_improvement_requests', 'cv_improvement_suggestions', 'cv_builder_documents', 'job_embeddings', 'vector_store') },
    @{ No = 4; Title = 'Đơn ứng tuyển, chấm điểm, giải thích'; Packages = '`jobapplication/`, `scoring/`, `ai/criterion/`, `ai/explanation/`';
       Tables = @('job_applications', 'application_status_history', 'application_screening_answers', 'scoring_runs', 'criterion_scores', 'score_explanations', 'score_explanation_attempts') },
    @{ No = 5; Title = 'Giấy mời, tin nhắn, thông báo, kho ứng viên'; Packages = '`interviewinvitation/`, `messaging/`, `notification/`, `talentpool/`';
       Tables = @('interview_invitations', 'interview_invitation_slots', 'application_messages', 'notifications', 'talent_pools', 'talent_pool_entries') },
    @{ No = 6; Title = 'Danh mục dùng chung'; Packages = '`catalog/` (FR-C05)';
       Tables = @('catalog_industries', 'catalog_industry_aliases', 'catalog_provinces', 'catalog_province_aliases') }
)

# Moi bang thuoc dung mot so do; lech la dung lai, khong sinh tai lieu sai
$home_ = @{}
foreach ($d in $domains) {
    foreach ($name in $d.Tables) {
        if (-not $tables.ContainsKey($name)) { throw "Bang '$name' khong co trong catalog" }
        if ($home_.ContainsKey($name)) { throw "Bang '$name' nam o hai so do" }
        $home_[$name] = $d.No
    }
}
$missing = $tables.Keys | Where-Object { -not $home_.ContainsKey($_) }
if ($missing) { throw "Bang chua duoc xep vao so do nao: $($missing -join ', ')" }

function Get-ShortType([string]$type) {
    $short = switch -Regex ($type) {
        '^character varying' { 'varchar'; break }
        '^timestamp with time zone$' { 'timestamptz'; break }
        '^timestamp without time zone$' { 'timestamp'; break }
        '^numeric' { 'numeric'; break }
        '^vector' { 'vector'; break }
        '^integer$' { 'int'; break }
        '^boolean$' { 'bool'; break }
        '^text\[\]$' { 'text_array'; break }
        default { $type }
    }
    if ($short -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { throw "Kieu '$type' chua co ten rut gon hop le cho Mermaid" }
    return $short
}

function Get-Constraints($t, [string]$kind) { @($t.constraints | Where-Object { $_.kind -eq $kind }) }

function Test-SameSet($a, $b) {
    $a = @($a | Sort-Object); $b = @($b | Sort-Object)
    if ($a.Count -ne $b.Count) { return $false }
    for ($i = 0; $i -lt $a.Count; $i++) { if ($a[$i] -ne $b[$i]) { return $false } }
    return $true
}

function Get-EntityLines($t, [bool]$stub, [int]$stubHome) {
    $pk = @((Get-Constraints $t 'p') | ForEach-Object { $_.columns })
    $fk = @((Get-Constraints $t 'f') | ForEach-Object { $_.columns })
    $uk = @((Get-Constraints $t 'u') | Where-Object { @($_.columns).Count -eq 1 } | ForEach-Object { $_.columns })
    $lines = @("    $($t.name) {")
    foreach ($c in $t.columns) {
        if ($stub -and ($pk -notcontains $c.name)) { continue }
        $keys = @()
        if ($pk -contains $c.name) { $keys += 'PK' }
        if (-not $stub -and $fk -contains $c.name) { $keys += 'FK' }
        if (-not $stub -and $uk -contains $c.name) { $keys += 'UK' }
        $line = "        $(Get-ShortType $c.type) $($c.name)"
        if ($keys) { $line += ' ' + ($keys -join ', ') }
        if ($stub) { $line += " `"chi tiết ở sơ đồ $stubHome`"" }
        elseif ($c.notNull) { $line += ' "not null"' }
        $lines += $line
    }
    $lines += '    }'
    return $lines
}

function Get-Relationship($t, $fkCon) {
    $cols = @($fkCon.columns)
    $notNull = $true
    foreach ($name in $cols) {
        $col = $t.columns | Where-Object { $_.name -eq $name }
        if (-not $col.notNull) { $notNull = $false }
    }
    # Cot FK trung khop dung mot PK/UNIQUE khong dieu kien -> phia con toi da mot dong
    $unique = $false
    foreach ($c in @(Get-Constraints $t 'p') + @(Get-Constraints $t 'u')) {
        if (Test-SameSet $c.columns $cols) { $unique = $true }
        if ($c.kind -eq 'p' -and @($c.columns).Count -eq 1 -and $cols -contains $c.columns[0]) { $unique = $true }
    }
    $parentMark = if ($notNull) { '||' } else { '|o' }
    $childMark = if ($unique) { 'o|' } else { 'o{' }
    return "    $($fkCon.refTable) $parentMark--$childMark $($t.name) : `"$($cols -join ', ')`""
}

function Format-Check([string]$def) {
    $m = [regex]::Match($def, '^CHECK \(\(\((\w+)\)::text = ANY \(\(ARRAY\[(.*)\]\)::text\[\]\)\)\)$')
    if ($m.Success) {
        $values = [regex]::Matches($m.Groups[2].Value, "'([^']*)'") | ForEach-Object { $_.Groups[1].Value }
        return "``$($m.Groups[1].Value)`` ∈ {$($values -join ', ')}"
    }
    $clean = $def -replace '::character varying', '' -replace '::text\[\]', '' -replace '::text', '' -replace '::numeric', ''
    # pg_get_constraintdef luon tra "CHECK (bieu thuc)" - bo lop ngoac boc ngoai do
    $clean = $clean -replace '^CHECK \((.*)\)$', '$1'
    return "``$clean``"
}

function Get-ConstraintRows($t) {
    $rows = @()
    foreach ($c in Get-Constraints $t 'u') {
        if (@($c.columns).Count -gt 1) {
            $rows += "| ``$($t.name)`` | ``$($c.name)`` | UNIQUE | ($($c.columns -join ', ')) |"
        }
    }
    foreach ($i in $t.uniqueIndexes) {
        $cols = (@($i.columns) | ForEach-Object { if ($_ -eq '(expr)') { 'biểu thức' } else { $_ } }) -join ', '
        # Bo phan WHERE truoc, roi moi lay danh sach cot/bieu thuc trong USING btree (...)
        $indexHead = $i.definition -replace ' WHERE .*$', ''
        if ($indexHead -match 'USING btree \((.*)\)$') { $cols = $Matches[1] -replace '::text', '' }
        $where = if ($i.predicate) { " WHERE ``$($i.predicate -replace '::character varying', '' -replace '::text\[\]', '' -replace '::text', '')``" } else { '' }
        $rows += "| ``$($t.name)`` | ``$($i.name)`` | UNIQUE INDEX một phần | ($cols)$where |"
    }
    foreach ($c in Get-Constraints $t 'c') {
        if ($c.name -like 'chk_*' -or $c.name -like '*_status_check') {
            $rows += "| ``$($t.name)`` | ``$($c.name)`` | CHECK | $(Format-Check $c.definition) |"
        }
    }
    return $rows
}

$out = New-Object System.Collections.Generic.List[string]
$add = { param($s) $out.Add($s) }
$drawn = 0

& $add '# Sơ đồ ER — AI Recruitment Agent'
& $add ''
& $add "> Xuất tự động từ catalog Postgres (``pg_catalog``) của database dev ở Flyway **V$($schema.flywayVersion)**, ngày"
& $add "> $($schema.exportedAt) (giờ Việt Nam). Không vẽ tay, không đọc file migration. Flyway là nguồn sự thật của schema"
& $add '> (`backend/src/main/resources/db/migration`); schema đổi thì xuất lại theo phụ lục.'
& $add ''
& $add "Database có **$($tables.Count) bảng** trong schema ``public`` (không tính ``flyway_schema_history``), chia thành"
& $add "$($domains.Count) sơ đồ theo package backend. Mỗi bảng được vẽ đầy đủ cột ở đúng một sơ đồ; ở sơ đồ khác nó chỉ"
& $add 'hiện tên và khoá chính (ghi "chi tiết ở sơ đồ N"). Mỗi khoá ngoại được vẽ đúng một lần, ở sơ đồ của bảng chứa nó.'
& $add ''
& $add 'Cách đọc:'
& $add ''
& $add '- Kiểu rút gọn: `varchar` (character varying), `timestamptz`, `numeric`, `vector` (pgvector, 1536 chiều),'
& $add '  `text_array` (`text[]`), `int`, `bool`. Độ dài/độ chính xác bỏ khỏi sơ đồ.'
& $add '- `PK` khoá chính, `FK` khoá ngoại, `UK` UNIQUE một cột không điều kiện. UNIQUE nhiều cột và UNIQUE INDEX một phần'
& $add '  (partial) nằm ở bảng "Ràng buộc quan trọng" dưới mỗi sơ đồ. `"not null"` là cột NOT NULL.'
& $add '- Quan hệ: `||--o{` bảng con có cột FK NOT NULL; `|o--o{` cột FK cho phép NULL; `--o|` khi cột FK của bảng con'
& $add '  đồng thời là PK/UNIQUE (quan hệ 1–0..1). Nhãn là tên cột FK.'
& $add '- Bảng "Ràng buộc quan trọng" liệt kê UNIQUE nhiều cột, UNIQUE INDEX một phần, CHECK tên `chk_*` và CHECK trạng thái'
& $add '  (`*_status_check`); phép ép kiểu `::text`/`::character varying` bỏ cho dễ đọc. CHECK còn lại (độ dài, giá trị enum'
& $add '  phụ…) có trong JSON do câu truy vấn ở phụ lục xuất ra.'
& $add ''
& $add '## Mục lục'
& $add ''
foreach ($d in $domains) {
    $anchor = ("$($d.No). $($d.Title)".ToLowerInvariant() -replace '[^\p{L}\p{Nd}\s-]', '' -replace '\s', '-')
    & $add "- [$($d.No). $($d.Title)](#$anchor) — $($d.Tables.Count) bảng"
}
& $add '- [Phụ lục: cách xuất lại](#phụ-lục-cách-xuất-lại)'
& $add ''

foreach ($d in $domains) {
    & $add "## $($d.No). $($d.Title)"
    & $add ''
    $tableList = ($d.Tables | ForEach-Object { '`' + $_ + '`' }) -join ', '
    & $add "Package: $($d.Packages). Bảng: $tableList."
    & $add ''
    $mermaid = New-Object System.Collections.Generic.List[string]
    $mermaid.Add('erDiagram')
    $stubs = @()
    foreach ($name in $d.Tables) {
        foreach ($f in Get-Constraints $tables[$name] 'f') {
            if ($home_[$f.refTable] -ne $d.No -and $stubs -notcontains $f.refTable) { $stubs += $f.refTable }
        }
    }
    foreach ($name in $d.Tables) { foreach ($l in Get-EntityLines $tables[$name] $false 0) { $mermaid.Add($l) }; $drawn++ }
    foreach ($name in ($stubs | Sort-Object)) { foreach ($l in Get-EntityLines $tables[$name] $true $home_[$name]) { $mermaid.Add($l) } }
    foreach ($name in $d.Tables) {
        foreach ($f in Get-Constraints $tables[$name] 'f') { $mermaid.Add((Get-Relationship $tables[$name] $f)) }
    }
    & $add '```mermaid'
    foreach ($l in $mermaid) { & $add $l }
    & $add '```'
    & $add ''
    if ($MmdDir) {
        [IO.File]::WriteAllText((Join-Path $MmdDir "er-$($d.No).mmd"), (($mermaid -join "`n") + "`n"), (New-Object Text.UTF8Encoding($false)))
    }
    $rows = @()
    foreach ($name in $d.Tables) { $rows += Get-ConstraintRows $tables[$name] }
    & $add '**Ràng buộc quan trọng**'
    & $add ''
    if ($rows.Count -eq 0) {
        & $add 'Không có UNIQUE nhiều cột, UNIQUE INDEX một phần hay CHECK thuộc nhóm trên.'
    } else {
        & $add '| Bảng | Tên | Loại | Điều kiện |'
        & $add '|---|---|---|---|'
        foreach ($r in $rows) { & $add $r }
    }
    & $add ''
}

if ($drawn -ne $tables.Count) { throw "So bang ve ($drawn) khac so bang catalog ($($tables.Count))" }

& $add '## Phụ lục: cách xuất lại'
& $add ''
& $add "Số bảng: catalog $($tables.Count) — vẽ đầy đủ trong tài liệu này $drawn."
& $add ''
& $add 'Hai file trong [`docs/er/`](er/):'
& $add ''
& $add '- [`schema-export.sql`](er/schema-export.sql) — đọc `pg_catalog` (cột, kiểu, NOT NULL, PK, FK, UNIQUE, UNIQUE INDEX một'
& $add '  phần, CHECK) của schema `public` trừ `flyway_schema_history`, trả về một JSON.'
& $add '- [`generate-er.ps1`](er/generate-er.ps1) — sinh lại toàn bộ file này từ JSON đó. Bảng mới phải được thêm vào danh sách'
& $add '  sơ đồ trong script; thiếu hoặc trùng thì script dừng, không ghi file.'
& $add ''
& $add 'Chạy từ gốc repo (PowerShell 5.1), khi `docker compose up -d` đang chạy và database đã migrate xong:'
& $add ''
& $add '```powershell'
& $add 'docker compose cp docs\er\schema-export.sql postgres:/tmp/schema-export.sql'
& $add 'docker compose exec -T postgres psql -U recruitment -d recruitment -At -v ON_ERROR_STOP=1 -f /tmp/schema-export.sql -o /tmp/schema.json'
& $add 'docker compose cp postgres:/tmp/schema.json "$env:TEMP\schema.json"'
& $add '.\docs\er\generate-er.ps1 -SchemaJson "$env:TEMP\schema.json" -OutFile docs\ER.md'
& $add '```'
& $add ''
& $add 'Chỉ dòng ngày xuất ở đầu file thay đổi giữa hai lần chạy trên cùng một schema.'

$text = ($out -join "`n") + "`n"
[IO.File]::WriteAllText($OutFile, $text, (New-Object Text.UTF8Encoding($false)))
"OK: $drawn/$($tables.Count) bang, $($out.Count) dong -> $OutFile"
