$lintFile = "f:\AndroidStudioProjects\MirrorMood\app\build\reports\lint-results-debug.txt"
$text = Get-Content $lintFile -Raw

$pattern = '(?m)^(.*):(\d+): Warning: The resource (.*) appears to be unused \[UnusedResources\]'
$matches = [regex]::Matches($text, $pattern)

$filesToDelete = @()
foreach ($m in $matches) {
    $filePath = $m.Groups[1].Value.Trim()
    if ($filePath -notmatch '\\values\\') {
        if (-not $filesToDelete.Contains($filePath)) {
            $filesToDelete += $filePath
        }
    } else {
        Write-Host "Manual update needed for: $filePath"
    }
}

Write-Host "Found $($filesToDelete.Count) files to delete:"
foreach ($file in $filesToDelete) {
    if (Test-Path $file) {
        Write-Host "Removing: $file"
        Remove-Item -Path $file -Force
    } else {
        Write-Host "Not found: $file"
    }
}
