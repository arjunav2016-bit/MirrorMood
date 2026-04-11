$fixMapping = [ordered]@{
    "?attr/colorOnSurface_secondary" = "?attr/colorOnSurfaceVariant";
    "?android:attr/colorBackground_alt" = "?attr/mmBackgroundAlt";
    "?attr/colorSurface_dim" = "?attr/colorSurfaceVariant"
}
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

Get-ChildItem -Path "c:\Users\minod\AndroidStudioProjects\MirrorMood\app\src\main\res" -Recurse -Filter "*.xml" | ForEach-Object {
    $content = [System.IO.File]::ReadAllText($_.FullName)
    $orig_content = $content
    foreach ($key in $fixMapping.Keys) {
        $content = $content.Replace($key, $fixMapping[$key])
    }
    if ($content -cne $orig_content) {
        [System.IO.File]::WriteAllText($_.FullName, $content, $utf8NoBom)
        Write-Output "Fixed typos in $($_.FullName)"
    }
}
