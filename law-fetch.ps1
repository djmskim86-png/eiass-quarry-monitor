# 산지관리법(법률·시행령·시행규칙)과 관련 행정규칙, 국회 법률안, 입법예고를 모니터링합니다.
# 실행: law-run.bat 더블클릭, 또는 powershell -ExecutionPolicy Bypass -File law-fetch.ps1
# 정부 사이트가 해외 IP를 막는 경우가 많으므로 국내 PC에서 실행하세요.
#
# 필요한 키 (law-keys.json 또는 환경변수 LAW_OC / ASSEMBLY_KEY):
#   lawOC       : 국가법령정보 공동활용(open.law.go.kr) 신청 후 받는 OC 값
#   assemblyKey : 열린국회정보(open.assembly.go.kr) 인증키
param(
    [switch]$NoOpen,
    [string]$MockDir = ""   # 테스트용: 실제 접속 대신 로컬 파일로 응답을 흉내냅니다.
)

$ErrorActionPreference = "Stop"
try { [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12 } catch {}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$dataDir = Join-Path $root "data\law"
$textDir = Join-Path $dataDir "texts"
$siteDir = Join-Path $root "site"
New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
New-Item -ItemType Directory -Force -Path $textDir | Out-Null

# ---------------------------------------------------------------- 공통 함수

function Write-Utf8NoBom($path, $content) {
    $enc = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($path, $content, $enc)
}

function Read-JsonFile($path) {
    if (-not (Test-Path $path)) { return $null }
    $raw = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    if ([string]::IsNullOrWhiteSpace($raw)) { return $null }
    return ($raw | ConvertFrom-Json)
}

function Get-Prop($obj, $name) {
    if ($null -eq $obj) { return $null }
    $p = $obj.PSObject.Properties[$name]
    if ($p) { return $p.Value }
    return $null
}

function Normalize-Name($s) {
    if ($null -eq $s) { return "" }
    return ($s -replace '\s', '')
}

function Strip-Html($s) {
    if ($null -eq $s) { return "" }
    $t = [regex]::Replace($s, '(?is)<(script|style)\b.*?</\1>', ' ')
    $t = [regex]::Replace($t, '(?s)<[^>]+>', ' ')
    $t = [System.Net.WebUtility]::HtmlDecode($t)
    return ([regex]::Replace($t, '\s+', ' ')).Trim()
}

function Short($s, $n) {
    if ($null -eq $s) { return "" }
    if ($s.Length -le $n) { return $s }
    return $s.Substring(0, $n) + "..."
}

function Format-Ymd($s) {
    if ($s -match '^(\d{4})(\d{2})(\d{2})$') { return "$($Matches[1]).$($Matches[2]).$($Matches[3])" }
    return $s
}

function Enc($s) { return [uri]::EscapeDataString([string]$s) }

function Decode-Bytes($bytes, $contentType) {
    $charset = ""
    if ($contentType -and $contentType -match 'charset\s*=\s*"?([\w\-]+)') { $charset = $Matches[1] }
    if (-not $charset) {
        $len = [Math]::Min($bytes.Length, 4096)
        $head = [System.Text.Encoding]::ASCII.GetString($bytes, 0, $len)
        if ($head -match '(?i)<meta[^>]+charset\s*=\s*"?([\w\-]+)') { $charset = $Matches[1] }
        elseif ($head -match '(?i)<\?xml[^>]+encoding\s*=\s*"([\w\-]+)"') { $charset = $Matches[1] }
    }
    $enc = [System.Text.Encoding]::UTF8
    if ($charset -and $charset -notmatch '(?i)utf-?8') {
        try { $enc = [System.Text.Encoding]::GetEncoding($charset) } catch {
            try { $enc = [System.Text.Encoding]::GetEncoding(949) } catch { $enc = [System.Text.Encoding]::UTF8 }
        }
    }
    return $enc.GetString($bytes).TrimStart([char]0xFEFF)
}

$script:mockMap = $null
function Get-Http($url) {
    if ($MockDir) {
        if ($null -eq $script:mockMap) { $script:mockMap = @((Read-JsonFile (Join-Path $MockDir "map.json")).items) }
        foreach ($m in $script:mockMap) {
            $hit = $true
            foreach ($part in @($m.match)) { if (-not $url.Contains($part)) { $hit = $false; break } }
            if ($hit) {
                if ($m.error) { throw $m.error }
                return [System.IO.File]::ReadAllText((Join-Path $MockDir $m.file), [System.Text.Encoding]::UTF8)
            }
        }
        throw "모의 응답 없음: $url"
    }
    $wc = New-Object System.Net.WebClient
    $wc.Headers.Add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
    $wc.Headers.Add("Accept-Language", "ko-KR,ko;q=0.9")
    try {
        $bytes = $wc.DownloadData($url)
        return (Decode-Bytes $bytes $wc.ResponseHeaders["Content-Type"])
    }
    finally { $wc.Dispose() }
}

function ConvertTo-XmlDoc($text, $label) {
    $doc = New-Object System.Xml.XmlDocument
    try { $doc.LoadXml($text.Trim()) }
    catch { throw "$label 응답을 읽을 수 없습니다. 응답 내용: $(Short (Strip-Html $text) 200)" }
    return $doc
}

function XT($node, $name) {
    $n = $node.SelectSingleNode($name)
    if ($n) { return $n.InnerText.Trim() }
    return ""
}

function New-Item2($key, $title, $url, $date, $cols, $watch) {
    return [PSCustomObject]@{
        key   = [string]$key
        title = [string]$title
        url   = [string]$url
        date  = [string]$date
        cols  = [PSCustomObject]$cols
        watch = [PSCustomObject]$watch
    }
}

# ---------------------------------------------------------------- 설정 / 키

$config = Read-JsonFile (Join-Path $root "law-config.json")
if ($null -eq $config) { throw "law-config.json 파일이 없습니다." }
$keys = Read-JsonFile (Join-Path $root "law-keys.json")

$LawOC = [string](Get-Prop $keys "lawOC")
if ($env:LAW_OC) { $LawOC = $env:LAW_OC }
$AssemblyKey = [string](Get-Prop $keys "assemblyKey")
if ($env:ASSEMBLY_KEY) { $AssemblyKey = $env:ASSEMBLY_KEY }
if ($LawOC -match '\s|open\.law') { $LawOC = "" }          # 예시 문구가 그대로 남아 있는 경우
if ($AssemblyKey -match '\s|발급') { $AssemblyKey = "" }

$lawNamesNorm = @($config.lawNames | ForEach-Object { Normalize-Name $_ })
$versionsPerLaw = [int](Get-Prop $config "versionsPerLaw"); if ($versionsPerLaw -lt 2) { $versionsPerLaw = 6 }

function Require-LawOC {
    if (-not $LawOC) { throw "국가법령정보 OC 값이 없습니다. law-keys.json 의 lawOC 를 채우세요. (open.law.go.kr 에서 무료 신청)" }
}
function Require-AssemblyKey {
    if (-not $AssemblyKey) { throw "열린국회정보 인증키가 없습니다. law-keys.json 의 assemblyKey 를 채우세요. (open.assembly.go.kr 에서 무료 발급)" }
}

# ---------------------------------------------------------------- 국가법령정보센터 (law.go.kr DRF)

function Invoke-LawSearch($target, $query, $extra) {
    Require-LawOC
    $url = "https://www.law.go.kr/DRF/lawSearch.do?OC=$(Enc $LawOC)&target=$target&type=XML&display=100&query=$(Enc $query)$extra"
    $doc = ConvertTo-XmlDoc (Get-Http $url) "국가법령정보센터($target)"
    $rowName = $target
    if ($target -eq "eflaw") { $rowName = "law" }   # 시행일 법령 검색도 결과 항목 이름은 <law>
    $rows = @($doc.SelectNodes("//$rowName"))
    if ($rows.Count -eq 0) {
        $msg = Short (Strip-Html $doc.DocumentElement.InnerText) 200
        $total = XT $doc.DocumentElement "totalCnt"
        if ($total -ne "0" -and $msg -and $msg -notmatch '^\s*\d') { throw "국가법령정보센터 응답: $msg" }
    }
    return $rows
}

function Get-LawRowInfo($r) {
    return [PSCustomObject]@{
        name   = XT $r "법령명한글"
        lawId  = XT $r "법령ID"
        mst    = XT $r "법령일련번호"
        status = XT $r "현행연혁코드"
        promDt = XT $r "공포일자"
        promNo = XT $r "공포번호"
        kind   = XT $r "제개정구분명"
        efYd   = XT $r "시행일자"
        org    = XT $r "소관부처명"
        type   = XT $r "법령구분명"
    }
}

function Get-LawVersionUrl($mst, $efYd) {
    if ($efYd) { return "https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=$mst&efYd=$efYd" }
    return "https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=$mst"
}

function Source-LawCurrent {
    $rows = Invoke-LawSearch "law" $config.lawQuery ""
    $items = @()
    foreach ($r in $rows) {
        $i = Get-LawRowInfo $r
        if ($lawNamesNorm -notcontains (Normalize-Name $i.name)) { continue }
        $items += New-Item2 $i.lawId $i.name ("https://www.law.go.kr/법령/" + (Normalize-Name $i.name)) $i.efYd `
            ([ordered]@{ "구분" = $i.type; "공포일자" = (Format-Ymd $i.promDt); "공포번호" = $i.promNo; "제·개정" = $i.kind; "시행일자" = (Format-Ymd $i.efYd); "법령일련번호" = $i.mst }) `
            ([ordered]@{ "법령일련번호" = $i.mst; "공포일자" = $i.promDt; "공포번호" = $i.promNo; "제·개정" = $i.kind; "시행일자" = $i.efYd })
    }
    $warn = ""
    if ($items.Count -lt $lawNamesNorm.Count) { $warn = "설정한 법령 $($lawNamesNorm.Count)개 중 $($items.Count)개만 찾았습니다. lawNames 이름을 확인하세요." }
    return @{ items = $items; warning = $warn }
}

function Source-LawVersions {
    $rows = Invoke-LawSearch "eflaw" $config.lawQuery "&sort=efdes"
    $byName = @{}
    foreach ($r in $rows) {
        $i = Get-LawRowInfo $r
        $n = Normalize-Name $i.name
        if ($lawNamesNorm -notcontains $n) { continue }
        if (-not $byName.ContainsKey($n)) { $byName[$n] = @() }
        $byName[$n] += $i
    }
    $items = @()
    foreach ($n in $byName.Keys) {
        $seen = @{}
        $list = @($byName[$n] | Sort-Object @{ Expression = { $_.efYd }; Descending = $true }, @{ Expression = { [long]("0" + $_.mst) }; Descending = $true })
        $kept = 0
        foreach ($i in $list) {
            $k = "$($i.mst)_$($i.efYd)"
            if ($seen.ContainsKey($k)) { continue }
            $seen[$k] = $true
            $items += New-Item2 $k "$($i.name) (시행 $(Format-Ymd $i.efYd))" (Get-LawVersionUrl $i.mst $i.efYd) $i.efYd `
                ([ordered]@{ "법령" = $i.name; "상태" = $i.status; "시행일자" = (Format-Ymd $i.efYd); "공포일자" = (Format-Ymd $i.promDt); "제·개정" = $i.kind; "법령일련번호" = $i.mst }) `
                ([ordered]@{ "상태" = $i.status })
            $kept++
            if ($kept -ge $versionsPerLaw) { break }
        }
    }
    return @{ items = $items; warning = "" }
}

function Source-AdminRules {
    $agencies = @($config.adminRuleAgencies | Where-Object { $_ })
    $found = @{}
    $items = @()
    foreach ($kw in @($config.adminRuleKeywords)) {
        $rows = Invoke-LawSearch "admrul" $kw ""
        foreach ($r in $rows) {
            $name = XT $r "행정규칙명"
            $org = XT $r "소관부처명"
            if ($agencies.Count -gt 0 -and ($agencies | Where-Object { $org -like "*$_*" }).Count -eq 0) { continue }
            $id = XT $r "행정규칙ID"
            if (-not $id) { $id = Normalize-Name $name }
            if ($found.ContainsKey($id)) { continue }
            $found[$id] = $true
            $seq = XT $r "행정규칙일련번호"
            $issued = XT $r "발령일자"
            $efYd = XT $r "시행일자"
            $kind = XT $r "제개정구분명"
            $items += New-Item2 $id $name ("https://www.law.go.kr/행정규칙/" + (Normalize-Name $name)) $issued `
                ([ordered]@{ "종류" = (XT $r "행정규칙종류"); "소관" = $org; "발령일자" = (Format-Ymd $issued); "발령번호" = (XT $r "발령번호"); "제·개정" = $kind; "시행일자" = (Format-Ymd $efYd) }) `
                ([ordered]@{ "행정규칙일련번호" = $seq; "발령일자" = $issued; "제·개정" = $kind; "시행일자" = $efYd })
        }
    }
    return @{ items = $items; warning = "" }
}

# ---------------------------------------------------------------- 열린국회정보 (open.assembly.go.kr)

function Invoke-AssemblyApi($endpoint, $params) {
    Require-AssemblyKey
    $all = @()
    $page = 1
    while ($true) {
        $qs = "KEY=$(Enc $AssemblyKey)&Type=json&pIndex=$page&pSize=100"
        foreach ($k in $params.Keys) { if ($params[$k]) { $qs += "&$k=$(Enc $params[$k])" } }
        $text = Get-Http "https://open.assembly.go.kr/portal/openapi/$endpoint`?$qs"
        try { $j = $text | ConvertFrom-Json } catch { throw "국회 API($endpoint) 응답을 읽을 수 없습니다: $(Short (Strip-Html $text) 200)" }
        $body = Get-Prop $j $endpoint
        if ($null -eq $body) {
            $res = Get-Prop $j "RESULT"
            if ($res -and $res.CODE -eq "INFO-200") { break }   # 해당 데이터 없음
            if ($res) { throw "국회 API($endpoint) 오류 [$($res.CODE)] $($res.MESSAGE)" }
            throw "국회 API($endpoint) 응답 형식이 예상과 다릅니다. law-config.json 의 assemblyEndpoints 를 확인하세요."
        }
        $body = @($body)
        $head = @(Get-Prop $body[0] "head")
        $total = 0
        foreach ($h in $head) {
            $c = Get-Prop $h "list_total_count"; if ($null -ne $c) { $total = [int]$c }
            $res = Get-Prop $h "RESULT"
            if ($res -and $res.CODE -ne "INFO-000") {
                if ($res.CODE -eq "INFO-200") { return $all }
                throw "국회 API($endpoint) 오류 [$($res.CODE)] $($res.MESSAGE)"
            }
        }
        $rows = @()
        if ($body.Count -gt 1) { $rows = @(Get-Prop $body[1] "row") }
        $all += $rows
        if ($rows.Count -eq 0 -or $all.Count -ge $total -or $page -ge 10) { break }
        $page++
    }
    return $all
}

function S($row, $name) { $v = Get-Prop $row $name; if ($null -eq $v) { return "" }; return ([string]$v).Trim() }

function Source-AssemblyBills {
    $items = @(); $seen = @{}
    foreach ($kw in @($config.billKeywords)) {
        $rows = Invoke-AssemblyApi $config.assemblyEndpoints.bills ([ordered]@{ AGE = $config.assemblyAge; BILL_NAME = $kw })
        foreach ($r in $rows) {
            $id = S $r "BILL_ID"; if (-not $id -or $seen.ContainsKey($id)) { continue }; $seen[$id] = $true
            $stage = "소관위 접수"
            if (S $r "CMT_PROC_RESULT_CD") { $stage = "소관위 " + (S $r "CMT_PROC_RESULT_CD") }
            if (S $r "LAW_PROC_RESULT_CD") { $stage = "법사위 " + (S $r "LAW_PROC_RESULT_CD") }
            if (S $r "PROC_RESULT") { $stage = "본회의 " + (S $r "PROC_RESULT") }
            $items += New-Item2 $id (S $r "BILL_NAME") (S $r "DETAIL_LINK") (S $r "PROPOSE_DT") `
                ([ordered]@{ "의안번호" = (S $r "BILL_NO"); "제안자" = (S $r "PROPOSER"); "제안일" = (S $r "PROPOSE_DT"); "소관위" = (S $r "COMMITTEE"); "진행단계" = $stage; "의결일" = (S $r "PROC_DT") }) `
                ([ordered]@{ "소관위 처리결과" = (S $r "CMT_PROC_RESULT_CD"); "법사위 처리결과" = (S $r "LAW_PROC_RESULT_CD"); "본회의 결과" = (S $r "PROC_RESULT"); "의결일" = (S $r "PROC_DT") })
        }
    }
    return @{ items = $items; warning = "" }
}

function Source-AssemblyAllBills {
    $items = @(); $seen = @{}
    foreach ($kw in @($config.billKeywords)) {
        $rows = Invoke-AssemblyApi $config.assemblyEndpoints.allBills ([ordered]@{ BILL_NM = $kw })
        foreach ($r in $rows) {
            $name = S $r "BILL_NM"
            if ($name -notlike "*$kw*") { continue }
            $id = S $r "BILL_ID"; if (-not $id -or $seen.ContainsKey($id)) { continue }; $seen[$id] = $true
            $stage = "소관위 회부"
            if (S $r "JRCMIT_PROC_RSLT") { $stage = "소관위 " + (S $r "JRCMIT_PROC_RSLT") }
            if (S $r "LAW_PROC_RSLT") { $stage = "법사위 " + (S $r "LAW_PROC_RSLT") }
            if (S $r "RGS_CONF_RSLT") { $stage = "본회의 " + (S $r "RGS_CONF_RSLT") }
            if (S $r "GVRN_TRSF_DT") { $stage = "정부 이송" }
            if (S $r "PROM_DT") { $stage = "공포" }
            $items += New-Item2 $id $name (S $r "LINK_URL") (S $r "PPSL_DT") `
                ([ordered]@{ "의안번호" = (S $r "BILL_NO"); "대수" = (S $r "ERACO"); "제안자구분" = (S $r "PPSR_KND"); "제안자" = (S $r "PPSR_NM"); "제안일" = (S $r "PPSL_DT"); "진행단계" = $stage; "공포일" = (S $r "PROM_DT"); "공포번호" = (S $r "PROM_NO") }) `
                ([ordered]@{ "소관위 처리결과" = (S $r "JRCMIT_PROC_RSLT"); "법사위 처리결과" = (S $r "LAW_PROC_RSLT"); "본회의 결과" = (S $r "RGS_CONF_RSLT"); "정부 이송일" = (S $r "GVRN_TRSF_DT"); "공포일" = (S $r "PROM_DT"); "공포번호" = (S $r "PROM_NO") })
        }
    }
    return @{ items = $items; warning = "" }
}

function Source-AssemblyNotices {
    $items = @(); $seen = @{}
    foreach ($kw in @($config.billKeywords)) {
        $rows = Invoke-AssemblyApi $config.assemblyEndpoints.notices ([ordered]@{ BILL_NAME = $kw })
        foreach ($r in $rows) {
            $id = S $r "BILL_ID"; if (-not $id -or $seen.ContainsKey($id)) { continue }; $seen[$id] = $true
            $items += New-Item2 $id (S $r "BILL_NAME") (S $r "LINK_URL") (S $r "NOTI_ED_DT") `
                ([ordered]@{ "의안번호" = (S $r "BILL_NO"); "제안자" = (S $r "PROPOSER"); "제안자구분" = (S $r "PROPOSER_KIND_CD"); "소관위" = (S $r "CURR_COMMITTEE"); "의견제출 마감" = (S $r "NOTI_ED_DT") }) `
                ([ordered]@{ "의견제출 마감" = (S $r "NOTI_ED_DT") })
        }
    }
    return @{ items = $items; warning = "" }
}

# ---------------------------------------------------------------- 웹페이지 감시 (입법예고 게시판 등)

function Source-PageWatch($pw) {
    $urls = @($pw.urls | Where-Object { $_ })
    if ($urls.Count -eq 0) { throw "감시할 주소(urls)가 비어 있습니다. law-config.json 에서 설정하세요." }
    $pages = [int](Get-Prop $pw "pages"); if ($pages -lt 1) { $pages = 1 }
    $kws = @($pw.keywords)
    $items = @(); $seen = @{}; $anchorTotal = 0
    foreach ($u in $urls) {
        $pageList = @(1)
        if ($u.Contains("{page}")) { $pageList = 1..$pages }
        foreach ($p in $pageList) {
            $pageUrl = $u.Replace("{page}", [string]$p)
            $html = Get-Http $pageUrl
            $ms = [regex]::Matches($html, '(?is)<a\b([^>]*)>(.*?)</a>')
            $anchorTotal += $ms.Count
            foreach ($m in $ms) {
                $attrs = $m.Groups[1].Value
                $text = Strip-Html $m.Groups[2].Value
                $titleAttr = ""
                if ($attrs -match '(?is)\btitle\s*=\s*"([^"]*)"') { $titleAttr = [System.Net.WebUtility]::HtmlDecode($Matches[1]).Trim() }
                $label = $text
                if ($titleAttr.Length -gt $label.Length) { $label = $titleAttr }
                if (-not $label) { continue }
                $hit = $false
                foreach ($k in $kws) { if ($label.Contains($k)) { $hit = $true; break } }
                if (-not $hit) { continue }
                $href = ""
                if ($attrs -match '(?is)\bhref\s*=\s*"([^"]*)"') { $href = $Matches[1] }
                elseif ($attrs -match "(?is)\bhref\s*=\s*'([^']*)'") { $href = $Matches[1] }
                $href = [System.Net.WebUtility]::HtmlDecode($href)
                $link = $pageUrl; $idPart = ""
                if ($href -and $href -notmatch '^(?i)(javascript:|#)') {
                    try { $link = ([System.Uri]::new([System.Uri]$pageUrl, $href)).AbsoluteUri; $idPart = $link } catch { $idPart = $href }
                }
                else {
                    $js = $href
                    if ($attrs -match '(?is)\bonclick\s*=\s*"([^"]*)"') { $js = $Matches[1] }
                    $idPart = $js
                }
                $key = (Normalize-Name $label) + "|" + $idPart
                if ($seen.ContainsKey($key)) { continue }
                $seen[$key] = $true
                $items += New-Item2 $key $label $link "" ([ordered]@{ "출처 페이지" = $pageUrl }) ([ordered]@{})
            }
        }
    }
    $warn = ""
    if ($anchorTotal -lt 5) { $warn = "페이지에서 링크를 거의 찾지 못했습니다. 화면을 자바스크립트로 그리는 페이지일 수 있으니 주소를 확인하세요." }
    return @{ items = $items; warning = $warn }
}

# ---------------------------------------------------------------- 실행

$fetchedAt = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$statePath = Join-Path $dataDir "state.json"
$eventsPath = Join-Path $dataDir "events.json"
$diffsPath = Join-Path $dataDir "diffs.json"
$prevState = Read-JsonFile $statePath
$eventsData = Read-JsonFile $eventsPath
$events = @(); if ($eventsData) { $events = @($eventsData.items) }
$newEvents = @()

$sourceDefs = @(
    @{ id = "law-current";  name = "국가법령정보센터 - 현행 법령";        group = "법령 본문";   removed = $true;  run = { Source-LawCurrent } },
    @{ id = "law-versions"; name = "국가법령정보센터 - 시행일별 연혁/시행예정"; group = "법령 본문"; removed = $false; run = { Source-LawVersions } },
    @{ id = "law-admrul";   name = "국가법령정보센터 - 행정규칙(고시·훈령)"; group = "행정규칙";   removed = $true;  run = { Source-AdminRules } },
    @{ id = "na-bills";     name = "국회 - 의원 발의 법률안";               group = "국회";       removed = $true;  run = { Source-AssemblyBills } },
    @{ id = "na-allbills";  name = "국회 - 의안정보 통합(정부안·공포 포함)";   group = "국회";       removed = $true;  run = { Source-AssemblyAllBills } },
    @{ id = "na-notices";   name = "국회 - 진행중 입법예고";               group = "국회";       removed = $true;  run = { Source-AssemblyNotices } }
)
foreach ($pw in @($config.pageWatches)) {
    $sourceDefs += @{ id = $pw.id; name = $pw.name; group = $pw.group; removed = $false; enabled = [bool]$pw.enabled; note = [string](Get-Prop $pw "note"); pw = $pw }
}

function Get-PrevSource($id) {
    if ($null -eq $prevState) { return $null }
    foreach ($s in @($prevState.sources)) { if ($s.id -eq $id) { return $s } }
    return $null
}

function Add-Event($src, $type, $title, $detail, $url) {
    $script:newEvents += [PSCustomObject]@{
        at = $fetchedAt; sourceId = $src.id; source = $src.name; group = $src.group
        type = $type; title = $title; detail = $detail; url = $url
    }
}

$sourcesOut = @()
foreach ($def in $sourceDefs) {
    $prev = Get-PrevSource $def.id
    $enabled = $true
    if ($def.ContainsKey("enabled")) { $enabled = $def.enabled }
    $out = [ordered]@{
        id = $def.id; name = $def.name; group = $def.group; note = [string]$def.note
        enabled = $enabled; ok = $false; error = ""; warning = ""
        fetchedAt = $fetchedAt; lastSuccessAt = [string](Get-Prop $prev "lastSuccessAt")
        items = @(); baseline = $false
    }
    if (-not $enabled) {
        $out.error = "사용 안 함 (law-config.json 에서 설정 필요)"
        if ($prev) { $out.items = @($prev.items) }
        $sourcesOut += [PSCustomObject]$out
        Write-Host "[건너뜀] $($def.name)"
        continue
    }
    Write-Host "[수집] $($def.name) ..."
    try {
        if ($def.ContainsKey("pw")) { $res = Source-PageWatch $def.pw } else { $res = & $def.run }
        $items = @($res.items)
        $out.ok = $true
        $out.warning = [string]$res.warning
        $out.items = $items
        $hadBaseline = ($prev -and (Get-Prop $prev "lastSuccessAt"))
        $out.lastSuccessAt = $fetchedAt
        if (-not $hadBaseline) {
            $out.baseline = $true
            Write-Host "   $($items.Count)건 (첫 수집 - 기준선으로 저장)"
        }
        else {
            $prevMap = @{}
            foreach ($p in @($prev.items)) { $prevMap[$p.key] = $p }
            $curKeys = @{}
            foreach ($it in $items) {
                $curKeys[$it.key] = $true
                if (-not $prevMap.ContainsKey($it.key)) {
                    $detail = (@($it.cols.PSObject.Properties | Where-Object { $_.Value } | ForEach-Object { "$($_.Name): $($_.Value)" }) -join " · ")
                    Add-Event $def "신규" $it.title $detail $it.url
                    continue
                }
                $p = $prevMap[$it.key]
                $changes = @()
                foreach ($prop in $it.watch.PSObject.Properties) {
                    $old = [string](Get-Prop $p.watch $prop.Name)
                    $new = [string]$prop.Value
                    if ($old -ne $new) {
                        if (-not $old) { $old = "(없음)" }
                        if (-not $new) { $new = "(없음)" }
                        $changes += "$($prop.Name): $old → $new"
                    }
                }
                if ($changes.Count -gt 0) { Add-Event $def "변경" $it.title ($changes -join " · ") $it.url }
            }
            if ($def.removed) {
                foreach ($p in @($prev.items)) {
                    if (-not $curKeys.ContainsKey($p.key)) { Add-Event $def "목록에서 사라짐" $p.title "이전 수집에는 있었으나 이번에는 없습니다 (예고 종료·폐지·명칭 변경 등)." $p.url }
                }
            }
            Write-Host "   $($items.Count)건"
        }
    }
    catch {
        $out.error = $_.Exception.Message
        if ($prev) { $out.items = @($prev.items) }   # 실패하면 이전 결과 유지, 이벤트는 만들지 않음
        Write-Host "   실패: $($out.error)" -ForegroundColor Yellow
    }
    $sourcesOut += [PSCustomObject]$out
}

# ---------------------------------------------------------------- 조문 단위 비교 (신설/개정/삭제)

function Get-ArticleKey($u) {
    $num = XT $u "조문번호"
    $br = XT $u "조문가지번호"
    $k = "제$num" + "조"
    if ($br -and $br -ne "0") { $k += "의$br" }
    return $k
}

function Get-LawArticles($mst, $efYd) {
    $cache = Join-Path $textDir "$mst`_$efYd.json"
    $c = Read-JsonFile $cache
    if ($c) { return @($c.articles) }
    Require-LawOC
    $doc = $null; $units = @(); $lastErr = ""
    $tries = @(
        "https://www.law.go.kr/DRF/lawService.do?OC=$(Enc $LawOC)&target=eflaw&MST=$mst&efYd=$efYd&type=XML",
        "https://www.law.go.kr/DRF/lawService.do?OC=$(Enc $LawOC)&target=law&MST=$mst&type=XML"
    )
    foreach ($u in $tries) {
        try {
            $doc = ConvertTo-XmlDoc (Get-Http $u) "법령 본문"
            $units = @($doc.SelectNodes("//조문단위"))
            if ($units.Count -gt 0) { break }
        } catch { $lastErr = $_.Exception.Message }
    }
    if ($units.Count -eq 0) { throw "법령 본문(MST=$mst)을 가져오지 못했습니다. $lastErr" }
    $articles = @()
    foreach ($u in $units) {
        if ((XT $u "조문여부") -eq "전문") { continue }   # 장·절 제목
        $lines = @()
        foreach ($n in @($u.SelectNodes(".//조문내용 | .//항내용 | .//호내용 | .//목내용"))) {
            $t = ($n.InnerText -replace '[ \t]+', ' ').Trim()
            if ($t) { $lines += $t }
        }
        $articles += [PSCustomObject]@{ key = (Get-ArticleKey $u); title = (XT $u "조문제목"); text = ($lines -join "`n") }
    }
    Write-Utf8NoBom $cache (([PSCustomObject]@{ mst = $mst; efYd = $efYd; articles = $articles }) | ConvertTo-Json -Depth 5)
    return $articles
}

function Get-ArticleSortKey($k) {
    if ($k -match '^제(\d+)조(?:의(\d+))?') { return ([int]$Matches[1]) * 1000 + [int]("0" + $Matches[2]) }
    return 999999
}

function Compare-Articles($oldArts, $newArts) {
    $om = @{}; foreach ($a in $oldArts) { $om[$a.key] = $a }
    $nm = @{}; foreach ($a in $newArts) { $nm[$a.key] = $a }
    $changes = @()
    foreach ($a in $newArts) {
        $isDeleted = ($a.text -match '^\s*제\d+조(의\d+)?\s*삭제')
        if (-not $om.ContainsKey($a.key)) {
            if (-not $isDeleted) { $changes += [PSCustomObject]@{ key = $a.key; title = $a.title; type = "신설"; oldText = ""; newText = $a.text } }
            continue
        }
        $o = $om[$a.key]
        if (($o.text -replace '\s', '') -ne ($a.text -replace '\s', '')) {
            $type = "개정"
            if ($isDeleted) { $type = "삭제" }
            $changes += [PSCustomObject]@{ key = $a.key; title = $(if ($a.title) { $a.title } else { $o.title }); type = $type; oldText = $o.text; newText = $a.text }
        }
    }
    foreach ($a in $oldArts) {
        if (-not $nm.ContainsKey($a.key)) { $changes += [PSCustomObject]@{ key = $a.key; title = $a.title; type = "삭제"; oldText = $a.text; newText = "" } }
    }
    return @($changes | Sort-Object @{ Expression = { Get-ArticleSortKey $_.key } })
}

$diffsData = Read-JsonFile $diffsPath
$diffs = @(); if ($diffsData) { $diffs = @($diffsData.items) }
$diffMap = @{}; foreach ($d in $diffs) { $d.changes = [object[]]@($d.changes | Where-Object { $_ }); $diffMap[$d.pairKey] = $d }
$diffError = ""

$verSrc = $sourcesOut | Where-Object { $_.id -eq "law-versions" }
$verLiveOk = ($verSrc -and $verSrc.ok)
if ($verSrc -and @($verSrc.items).Count -gt 0) {
    Write-Host "[분석] 조문 단위 변경 비교 ..."
    $byLaw = @{}
    foreach ($it in @($verSrc.items)) {
        $n = $it.cols.'법령'
        if (-not $byLaw.ContainsKey($n)) { $byLaw[$n] = @() }
        $byLaw[$n] += $it
    }
    $neededTexts = @{}
    foreach ($n in $byLaw.Keys) {
        $list = @($byLaw[$n] | Sort-Object @{ Expression = { $_.date }; Descending = $true }, @{ Expression = { [long]("0" + $_.cols.'법령일련번호') }; Descending = $true })
        for ($i = 0; $i -lt $list.Count - 1; $i++) {
            $to = $list[$i]; $from = $list[$i + 1]
            $status = $to.cols.'상태'
            $toMst = $to.cols.'법령일련번호'; $fromMst = $from.cols.'법령일련번호'
            $pairKey = "$toMst`_$($to.date)<-$fromMst`_$($from.date)"
            if ($diffMap.ContainsKey($pairKey) -and -not $diffMap[$pairKey].error) {
                $diffMap[$pairKey].toStatus = $status
            }
            elseif ($LawOC) {
                $neededTexts["$toMst`_$($to.date)"] = $true; $neededTexts["$fromMst`_$($from.date)"] = $true
                try {
                    $changes = @(Compare-Articles (Get-LawArticles $fromMst $from.date) (Get-LawArticles $toMst $to.date))
                    $d = [PSCustomObject]@{
                        pairKey = $pairKey; lawName = $n; toStatus = $status; kind = $to.cols.'제·개정'
                        toMst = $toMst; toEfYd = $to.date; toPromDt = $to.cols.'공포일자'; toUrl = $to.url
                        fromMst = $fromMst; fromEfYd = $from.date; fromUrl = $from.url
                        computedAt = $fetchedAt; error = ""; changes = [object[]]$changes
                    }
                    $isNewPair = -not $diffMap.ContainsKey($pairKey)
                    $diffMap[$pairKey] = $d
                    $cnt = @{ "신설" = 0; "개정" = 0; "삭제" = 0 }
                    foreach ($c in $changes) { $cnt[$c.type]++ }
                    $summary = "신설 $($cnt['신설']) · 개정 $($cnt['개정']) · 삭제 $($cnt['삭제'])"
                    Write-Host "   $n 시행 $(Format-Ymd $to.date) ($status): $summary"
                    if ($isNewPair -and $verLiveOk -and -not $verSrc.baseline) {
                        $script:newEvents += [PSCustomObject]@{
                            at = $fetchedAt; sourceId = "law-diff"; source = "조문 비교"; group = "법령 본문"; type = "조문 변경"
                            title = "$n (시행 $(Format-Ymd $to.date), $status)"; detail = $summary + " — 대시보드 '조문 변경 내역'에서 확인"; url = $to.url
                        }
                    }
                }
                catch {
                    $diffError = $_.Exception.Message
                    Write-Host "   비교 실패: $diffError" -ForegroundColor Yellow
                }
            }
            if ($status -eq "현행" -or $status -eq "연혁") { break }   # 현행 직전 개정까지만 비교
        }
    }
    # 이전에 비교해 둔 버전의 상태(시행예정→현행→연혁) 갱신
    $statusByVer = @{}
    foreach ($it in @($verSrc.items)) { $statusByVer[$it.key] = $it.cols.'상태' }
    foreach ($d in $diffMap.Values) {
        $vk = "$($d.toMst)_$($d.toEfYd)"
        if ($statusByVer.ContainsKey($vk)) { $d.toStatus = $statusByVer[$vk] }
    }
    # 더 이상 필요 없는 본문 캐시 정리
    Get-ChildItem $textDir -Filter "*.json" | Where-Object { -not $neededTexts.ContainsKey($_.BaseName) } | Remove-Item -Force -ErrorAction SilentlyContinue
}
$diffs = @($diffMap.Values | Sort-Object @{ Expression = { $_.toEfYd }; Descending = $true } | Select-Object -First 40)

# ---------------------------------------------------------------- 저장

$events = @($newEvents) + @($events) | Select-Object -First 1000
$state = [PSCustomObject]@{ fetchedAt = $fetchedAt; sources = $sourcesOut }
Write-Utf8NoBom $statePath ($state | ConvertTo-Json -Depth 8)
Write-Utf8NoBom $eventsPath (([PSCustomObject]@{ items = @($events) }) | ConvertTo-Json -Depth 5)
Write-Utf8NoBom $diffsPath (([PSCustomObject]@{ items = @($diffs) }) | ConvertTo-Json -Depth 6)

$okCount = @($sourcesOut | Where-Object { $_.enabled -and $_.ok }).Count
$enabledCount = @($sourcesOut | Where-Object { $_.enabled }).Count
Write-Host ""
Write-Host "정상 수집: $okCount / $enabledCount 개 출처, 새 변경 이벤트: $($newEvents.Count)건"

$templatePath = Join-Path $siteDir "law-template.html"
$outputPath = Join-Path $siteDir "law.html"
$template = [System.IO.File]::ReadAllText($templatePath, [System.Text.Encoding]::UTF8)
$dataForHtml = [PSCustomObject]@{
    fetchedAt = $fetchedAt
    newEventCount = $newEvents.Count
    keys = [PSCustomObject]@{ lawOC = [bool]$LawOC; assembly = [bool]$AssemblyKey }
    diffError = $diffError
    sources = $sourcesOut
    events = @($events)
    diffs = @($diffs)
}
$json = ($dataForHtml | ConvertTo-Json -Depth 8 -Compress).Replace("</", "<\/")
Write-Utf8NoBom $outputPath ($template.Replace("/*__DATA_JSON__*/null", $json))
Write-Host "대시보드 생성 완료: $outputPath"

if (-not $NoOpen) { Start-Process $outputPath }
