# EIASS(환경영향평가정보지원시스템)에서 "토석·모래·자갈·광물 등의 채취"(채석단지) 사업을 수집합니다.
# 실행: run.bat 더블클릭, 또는 powershell -File fetch.ps1
param(
    [switch]$NoOpen
)

$ErrorActionPreference = "Stop"
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$dataDir = Join-Path $root "data"
$siteDir = Join-Path $root "site"
$historyDir = Join-Path $dataDir "history"
New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
New-Item -ItemType Directory -Force -Path $historyDir | Out-Null

function Write-Utf8NoBom($path, $content) {
    $enc = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($path, $content, $enc)
}

# 사업코드 앞 2자리 = 관할 협의기관 코드
$organMap = @{
    "ME" = "기후에너지환경부"; "HG" = "한강유역환경청"; "ND" = "낙동강유역환경청"; "GG" = "금강유역환경청"
    "YS" = "영산강유역환경청"; "WJ" = "원주지방환경청"; "DG" = "대구지방환경청"; "JJ" = "전북지방환경청"
    "GW" = "강원특별자치도청"; "JB" = "전북특별자치도청"; "JE" = "제주특별자치도청"; "JN" = "전남광주통합특별시청"
}

Write-Host "[EIASS] 토석·모래·자갈·광물 등의 채취(채석단지) 사업 목록을 가져오는 중..."

$body = @{
    query       = ""
    collection  = "business"
    # alias=1(환경영향평가), bizGubunCd=Q(토석모래자갈광물 등의 채취) -> 채석단지/토석채취 사업 공식 분류
    urlString   = "&alias=1&bizGubunCd=Q"
    viewName    = "/eiass/user/biz/base/info/searchListEia_searchApi"
    currentPage = "1"
    sort        = "DATE/DESC,APPLY_DT/DESC"
    listCount   = "2000"
}

$resp = Invoke-WebRequest -Uri "https://www.eiass.go.kr/searchApi/search.do" -Method POST -Body $body -UseBasicParsing `
    -Headers @{ "X-Requested-With" = "XMLHttpRequest" }

$pattern = '<tr>\s*<td data-cell-header="사업코드 : ">(?<code>[^<]*)</td>\s*<td data-cell-header="사업구분 : "[^>]*>(?<biztype>[^<]*)</td>\s*<td data-cell-header="사업명 : "[^>]*title="(?<title>[^"]*)">\s*<a href="javascript:view\(''eia'',''(?<eiacd>[^'']*)'',\s*''(?<discseq>[^'']*)''\)"[^>]*>(?<name>[^<]*)</a>\s*</td>\s*<td data-cell-header="접수일 : ">(?<apply>[^<]*)</td>\s*<td data-cell-header="완료일 : ">\s*(?<complete>[^<]*?)\s*</td>\s*<td data-cell-header="진행현황 : ">\s*<span[^>]*>(?<status>[^<]*)</span>\s*</td>\s*</tr>'
$opts = [System.Text.RegularExpressions.RegexOptions]::Singleline
$rowMatches = [regex]::Matches($resp.Content, $pattern, $opts)

function Decode($s) {
    if ($null -eq $s) { return "" }
    return [System.Net.WebUtility]::HtmlDecode($s.Trim())
}

$items = @()
foreach ($m in $rowMatches) {
    $code = Decode $m.Groups['eiacd'].Value
    $discSeq = Decode $m.Groups['discseq'].Value
    $prefix = if ($code.Length -ge 2) { $code.Substring(0, 2) } else { "" }
    $region = if ($organMap.ContainsKey($prefix)) { $organMap[$prefix] } else { $prefix }

    $applyDateVal = Decode $m.Groups['apply'].Value
    $items += [PSCustomObject]@{
        key          = "$code#$applyDateVal"
        eiaCd        = $code
        discSeq      = $discSeq
        bizType      = Decode $m.Groups['biztype'].Value
        name         = Decode $m.Groups['name'].Value
        region       = $region
        applyDate    = Decode $m.Groups['apply'].Value
        completeDate = Decode $m.Groups['complete'].Value
        status       = Decode $m.Groups['status'].Value
        detailUrl    = "https://www.eiass.go.kr/biz/base/info/eiaInfo.do?EIA_CD=$code&EIA_DISC_SEQ=$discSeq"
    }
}

Write-Host "총 $($items.Count)건 수집 완료"

# 이전 스냅샷과 비교 (신규/상태변경 탐지)
$latestPath = Join-Path $dataDir "latest.json"
$isFirstRun = -not (Test-Path $latestPath)
$prevItems = @()
if (-not $isFirstRun) {
    $prevData = Get-Content $latestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($prevData.items) { $prevItems = @($prevData.items) }
}
$prevMap = @{}
foreach ($p in $prevItems) { $prevMap[$p.key] = $p }

$newCount = 0
$changedCount = 0
$finalItems = @()
foreach ($it in $items) {
    $obj = [ordered]@{
        key          = $it.key
        eiaCd        = $it.eiaCd
        discSeq      = $it.discSeq
        bizType      = $it.bizType
        name         = $it.name
        region       = $it.region
        applyDate    = $it.applyDate
        completeDate = $it.completeDate
        status       = $it.status
        detailUrl    = $it.detailUrl
        isNew        = $false
        statusChanged = $false
        prevStatus   = ""
    }
    if (-not $isFirstRun -and -not $prevMap.ContainsKey($it.key)) {
        $obj.isNew = $true
        $newCount++
    }
    elseif ($prevMap.ContainsKey($it.key) -and $prevMap[$it.key].status -ne $it.status) {
        $obj.statusChanged = $true
        $obj.prevStatus = $prevMap[$it.key].status
        $changedCount++
    }
    $finalItems += [PSCustomObject]$obj
}

$fetchedAt = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$snapshot = [PSCustomObject]@{
    fetchedAt    = $fetchedAt
    totalCount   = $finalItems.Count
    newCount     = $newCount
    changedCount = $changedCount
    items        = $finalItems
}

# 이전 스냅샷 보관 (최근 30개)
if (Test-Path $latestPath) {
    $histName = "snapshot_" + (Get-Date -Format "yyyyMMdd_HHmmss") + ".json"
    Copy-Item $latestPath (Join-Path $historyDir $histName)
    $histFiles = Get-ChildItem $historyDir -Filter "snapshot_*.json" | Sort-Object Name -Descending
    if ($histFiles.Count -gt 30) {
        $histFiles | Select-Object -Skip 30 | Remove-Item -Force
    }
}

$snapshotJson = $snapshot | ConvertTo-Json -Depth 6
Write-Utf8NoBom $latestPath $snapshotJson

# 신규 사업 누적 로그 (계속 쌓임 - 언제 새로 나타났는지 기록)
$newLogPath = Join-Path $dataDir "new_log.json"
$newLog = @()
if (Test-Path $newLogPath) {
    $raw = Get-Content $newLogPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($raw) { $newLog = @($raw) }
}
if ($newCount -gt 0) {
    $newEntries = $finalItems | Where-Object { $_.isNew } | ForEach-Object {
        [PSCustomObject]@{
            foundAt   = $fetchedAt
            eiaCd     = $_.eiaCd
            name      = $_.name
            region    = $_.region
            applyDate = $_.applyDate
            detailUrl = $_.detailUrl
        }
    }
    $newLog = @($newEntries) + $newLog
}
Write-Utf8NoBom $newLogPath ($newLog | ConvertTo-Json -Depth 4)

Write-Host "신규: $newCount 건 / 상태변경: $changedCount 건"

# HTML 대시보드 생성 (템플릿에 데이터 주입)
$templatePath = Join-Path $siteDir "template.html"
$outputPath = Join-Path $siteDir "index.html"
$template = Get-Content $templatePath -Raw -Encoding UTF8
$dataForHtml = [PSCustomObject]@{
    fetchedAt    = $fetchedAt
    totalCount   = $finalItems.Count
    newCount     = $newCount
    changedCount = $changedCount
    items        = $finalItems
    newLog       = $newLog
}
$json = $dataForHtml | ConvertTo-Json -Depth 6 -Compress
$html = $template.Replace("/*__DATA_JSON__*/null", $json)
Write-Utf8NoBom $outputPath $html

Write-Host "대시보드 생성 완료: $outputPath"

if (-not $NoOpen) {
    Start-Process $outputPath
}
