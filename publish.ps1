# 최신 데이터를 수집한 뒤 GitHub에 올려 GitHub Pages에 자동 반영합니다.
# eiass.go.kr은 국내 IP에서만 접근되므로, 이 스크립트는 반드시 이 PC(또는 국내 네트워크)에서 실행해야 합니다.

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

# PATH 새로고침 (git/gh가 방금 설치된 세션 대비)
$env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path", "User")

Set-Location $root

Write-Host "1) 최신 데이터 수집 중..."
& "$root\fetch.ps1" -NoOpen

Write-Host "2) 산지관리법 모니터링 수집 중..."
try { & "$root\law-fetch.ps1" -NoOpen } catch { Write-Host "산지관리법 수집 중 오류(건너뜀): $($_.Exception.Message)" }

Write-Host "3) 변경 사항 확인 중..."
git add data/latest.json data/new_log.json site/index.html
if (Test-Path "$root\site\law.html") { git add data/law/state.json data/law/events.json data/law/diffs.json site/law.html site/law-template.html }

$hasChanges = $true
git diff --cached --quiet
if ($LASTEXITCODE -eq 0) { $hasChanges = $false }

if (-not $hasChanges) {
    Write-Host "변경된 내용이 없습니다. (이미 최신 상태)"
    exit 0
}

$data = Get-Content (Join-Path $root "data\latest.json") -Raw -Encoding UTF8 | ConvertFrom-Json
$msg = "데이터 갱신: 전체 $($data.totalCount)건 / 신규 $($data.newCount)건 / 상태변경 $($data.changedCount)건"
if (Test-Path (Join-Path $root "data\law\events.json")) {
    $lawEvents = @((Get-Content (Join-Path $root "data\law\events.json") -Raw -Encoding UTF8 | ConvertFrom-Json).items)
    $todayEvents = @($lawEvents | Where-Object { $_.at -like ((Get-Date -Format "yyyy-MM-dd") + "*") }).Count
    $msg += " / 산지관리법 오늘 변경 $todayEvents건"
}

git commit -m $msg
git push

Write-Host ""
Write-Host "GitHub에 반영 완료. 잠시 후 GitHub Pages에 자동 배포됩니다."
Write-Host "공개 주소: https://djmskim86-png.github.io/eiass-quarry-monitor/"
