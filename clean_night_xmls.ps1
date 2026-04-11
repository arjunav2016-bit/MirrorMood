$lintFile = "f:\AndroidStudioProjects\MirrorMood\app\build\reports\lint-results-debug.txt"
$text = Get-Content $lintFile -Raw

$pattern = '(?m)^(.*):(\d+): Error: The color "(.*)" in values-night has no declaration in the base values folder.*?\[MissingDefaultResource\]'
$matches = [regex]::Matches($text, $pattern)

$itemsToRemove = @{}

foreach ($m in $matches) {
    $filePath = $m.Groups[1].Value.Trim()
    if (-not $itemsToRemove.ContainsKey($filePath)) {
        $itemsToRemove[$filePath] = @()
    }
    $resName = $m.Groups[3].Value.Trim()
    $itemsToRemove[$filePath] += $resName
}

foreach ($file in $itemsToRemove.Keys) {
    if (Test-Path $file) {
        $xmlContent = Get-Content $file -Raw
        $namesToRemove = $itemsToRemove[$file]
        foreach ($name in $namesToRemove) {
            $nodePattern = '(?ms)^\s*<color[^>]*name="' + [regex]::Escape($name) + '"[^>]*>.*?</color>\r?\n?'
            $xmlContent = $xmlContent -replace $nodePattern, ''
            
            $singleNodePattern = '(?ms)^\s*<color[^>]*name="' + [regex]::Escape($name) + '"[^>]*/>\r?\n?'
            $xmlContent = $xmlContent -replace $singleNodePattern, ''
        }
        Set-Content -Path $file -Value $xmlContent
        Write-Host "Cleaned $file"
    }
}
