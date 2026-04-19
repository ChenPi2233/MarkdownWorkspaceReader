$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$drawableDir = Join-Path $projectRoot "app/src/main/res/drawable"

$icons = @(
    @{ Name = "arrow_left"; Source = "outline/arrow-left.svg"; Filled = $false },
    @{ Name = "menu_2"; Source = "outline/menu-2.svg"; Filled = $false },
    @{ Name = "refresh"; Source = "outline/refresh.svg"; Filled = $false },
    @{ Name = "star"; Source = "outline/star.svg"; Filled = $false },
    @{ Name = "star_filled"; Source = "filled/star.svg"; Filled = $true },
    @{ Name = "folder"; Source = "outline/folder.svg"; Filled = $false },
    @{ Name = "folder_open"; Source = "outline/folder-open.svg"; Filled = $false },
    @{ Name = "file_text"; Source = "outline/file-text.svg"; Filled = $false },
    @{ Name = "chevron_right"; Source = "outline/chevron-right.svg"; Filled = $false },
    @{ Name = "chevron_down"; Source = "outline/chevron-down.svg"; Filled = $false },
    @{ Name = "settings"; Source = "outline/settings.svg"; Filled = $false },
    @{ Name = "note"; Source = "outline/note.svg"; Filled = $false }
)

function Convert-TablerSvgToVector {
    param(
        [string]$Svg,
        [bool]$Filled
    )

    $pathMatches = [regex]::Matches($Svg, '<path\s+d="([^"]+)"\s*/?>')
    if ($pathMatches.Count -eq 0) {
        throw "No path data found in SVG."
    }

    $paths = foreach ($match in $pathMatches) {
        $pathData = [System.Security.SecurityElement]::Escape($match.Groups[1].Value)
        if ($Filled) {
            "    <path android:fillColor=`"#1F2421`" android:pathData=`"$pathData`" />"
        } else {
            "    <path android:fillColor=`"@android:color/transparent`" android:pathData=`"$pathData`" android:strokeColor=`"#1F2421`" android:strokeWidth=`"2`" android:strokeLineCap=`"round`" android:strokeLineJoin=`"round`" />"
        }
    }

    @"
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
$($paths -join "`n")
</vector>
"@
}

New-Item -ItemType Directory -Force -Path $drawableDir | Out-Null

foreach ($icon in $icons) {
    $url = "https://raw.githubusercontent.com/tabler/tabler-icons/master/icons/$($icon.Source)"
    $svg = Invoke-WebRequest -Uri $url -UseBasicParsing | Select-Object -ExpandProperty Content
    $vector = Convert-TablerSvgToVector -Svg $svg -Filled ([bool]$icon.Filled)
    $outFile = Join-Path $drawableDir "ic_tabler_$($icon.Name).xml"
    Set-Content -LiteralPath $outFile -Value $vector -Encoding UTF8
}
