# eiass-monitor 대시보드를 로컬 웹서버로 제공합니다.
param(
    [int]$Port = 8080
)

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$siteDir = Join-Path $root "site"

$mimeMap = @{
    ".html" = "text/html; charset=utf-8"
    ".js"   = "application/javascript; charset=utf-8"
    ".css"  = "text/css; charset=utf-8"
    ".json" = "application/json; charset=utf-8"
    ".png"  = "image/png"
    ".jpg"  = "image/jpeg"
    ".svg"  = "image/svg+xml"
    ".ico"  = "image/x-icon"
}

$listener = New-Object System.Net.HttpListener
$prefix = "http://localhost:$Port/"
$listener.Prefixes.Add($prefix)

try {
    $listener.Start()
} catch {
    Write-Host "포트 $Port 를 열 수 없습니다: $($_.Exception.Message)"
    Write-Host "다른 포트로 다시 시도해보세요. 예: powershell -File serve.ps1 -Port 8081"
    exit 1
}

Write-Host "==================================================="
Write-Host " EIASS 채석단지 모니터링 대시보드 서버 실행 중"
Write-Host " 주소: $prefix"
Write-Host " 종료하려면 이 창에서 Ctrl+C 를 누르세요"
Write-Host "==================================================="

while ($listener.IsListening) {
    $context = $listener.GetContext()
    $request = $context.Request
    $response = $context.Response

    $path = $request.Url.LocalPath
    if ($path -eq "/") { $path = "/index.html" }
    $filePath = Join-Path $siteDir ($path.TrimStart("/"))
    $filePath = [System.IO.Path]::GetFullPath($filePath)

    # 디렉터리 탈출 방지
    if (-not $filePath.StartsWith([System.IO.Path]::GetFullPath($siteDir))) {
        $response.StatusCode = 403
        $response.Close()
        continue
    }

    if (Test-Path $filePath -PathType Leaf) {
        $ext = [System.IO.Path]::GetExtension($filePath)
        $contentType = if ($mimeMap.ContainsKey($ext)) { $mimeMap[$ext] } else { "application/octet-stream" }
        $bytes = [System.IO.File]::ReadAllBytes($filePath)
        $response.ContentType = $contentType
        $response.ContentLength64 = $bytes.Length
        $response.OutputStream.Write($bytes, 0, $bytes.Length)
    } else {
        $response.StatusCode = 404
        $notFound = [System.Text.Encoding]::UTF8.GetBytes("404 Not Found: $path")
        $response.OutputStream.Write($notFound, 0, $notFound.Length)
    }
    $response.Close()
}
