$lintFile = "f:\AndroidStudioProjects\MirrorMood\app\build\reports\lint-results-debug.txt"
$text = Get-Content $lintFile -Raw

$pattern = '(?m)^(.*):(\d+): Warning: The resource (.*) appears to be unused \[UnusedResources\]'
$matches = [regex]::Matches($text, $pattern)

$itemsToRemove = @{}

foreach ($m in $matches) {
    $filePath = $m.Groups[1].Value.Trim()
    $resourceParts = $m.Groups[3].Value.Trim().Split('\.')
    if ($filePath -match '\\values\\') {
        if (-not $itemsToRemove.ContainsKey($filePath)) {
            $itemsToRemove[$filePath] = @()
        }
        $resName = $resourceParts[-1]
        $itemsToRemove[$filePath] += $resName
    }
}

foreach ($file in $itemsToRemove.Keys) {
    if (Test-Path $file) {
        $xmlContent = Get-Content $file -Raw
        $namesToRemove = $itemsToRemove[$file]
        foreach ($name in $namesToRemove) {
            # Regex to remove entire xml node e.g. <string name="name">...</string> or <color name="name">...</color>
            # Handling styles, colors, dimens, strings
            $nodePattern = '(?ms)^\s*<(color|string|dimen|style)[^>]*name="' + [regex]::Escape($name) + '"[^>]*>.*?</\1>\r?\n?'
            $xmlContent = $xmlContent -replace $nodePattern, ''
            
            # Single line tags: <item name="name" />
            $singleNodePattern = '(?ms)^\s*<(color|dimen)[^>]*name="' + [regex]::Escape($name) + '"[^>]*/>\r?\n?'
            $xmlContent = $xmlContent -replace $singleNodePattern, ''
        }
        Set-Content -Path $file -Value $xmlContent
        Write-Host "Cleaned $file"
    }
}
