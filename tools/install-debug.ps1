param(
    [string]$ApkPath = "app/build/outputs/apk/debug/app-debug.apk"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$resolvedApk = Join-Path $projectRoot $ApkPath

if (-not (Test-Path -LiteralPath $resolvedApk)) {
    Push-Location $projectRoot
    try {
        .\gradlew.bat :app:assembleDebug
    } finally {
        Pop-Location
    }
}

$adb = Get-Command adb -ErrorAction SilentlyContinue
if ($null -eq $adb) {
    $defaultAdb = "C:\Users\cpch2\AppData\Local\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path -LiteralPath $defaultAdb) {
        $adbPath = $defaultAdb
    } else {
        throw "adb was not found. Install Android platform-tools or add adb to PATH."
    }
} else {
    $adbPath = $adb.Source
}

& $adbPath devices -l
& $adbPath install -r $resolvedApk
