$activities = @(
    "c:\Users\minod\AndroidStudioProjects\MirrorMood\app\src\main\java\com\mirrormood\MainActivity.kt",
    "c:\Users\minod\AndroidStudioProjects\MirrorMood\app\src\main\java\com\mirrormood\ui\journal\JournalActivity.kt",
    "c:\Users\minod\AndroidStudioProjects\MirrorMood\app\src\main\java\com\mirrormood\ui\history\HistoryActivity.kt",
    "c:\Users\minod\AndroidStudioProjects\MirrorMood\app\src\main\java\com\mirrormood\ui\correlations\CorrelationsActivity.kt",
    "c:\Users\minod\AndroidStudioProjects\MirrorMood\app\src\main\java\com\mirrormood\ui\privacy\PrivacyActivity.kt",
    "c:\Users\minod\AndroidStudioProjects\MirrorMood\app\src\main\java\com\mirrormood\ui\insights\InsightsActivity.kt",
    "c:\Users\minod\AndroidStudioProjects\MirrorMood\app\src\main\java\com\mirrormood\ui\recommendations\RecommendationsActivity.kt",
    "c:\Users\minod\AndroidStudioProjects\MirrorMood\app\src\main\java\com\mirrormood\ui\lock\LockActivity.kt"
)

foreach ($filePath in $activities) {
    if (Test-Path $filePath) {
        $content = [System.IO.File]::ReadAllText($filePath, [System.Text.Encoding]::UTF8)
        if (-not $content.Contains("ThemeHelper.applyTheme(this)")) {
            if (-not $content.Contains("import com.mirrormood.util.ThemeHelper")) {
                $content = $content.Replace("import android.os.Bundle", "import android.os.Bundle`nimport com.mirrormood.util.ThemeHelper")
            }
            $content = $content.Replace("super.onCreate(savedInstanceState)", "ThemeHelper.applyTheme(this)`n        super.onCreate(savedInstanceState)")
            [System.IO.File]::WriteAllText($filePath, $content, [System.Text.Encoding]::UTF8)
            Write-Output "Updated $filePath"
        }
    }
}
