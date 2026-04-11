$mapping = @{
    "@color/mm_background_alt" = "?attr/mmBackgroundAlt";
    "@color/mm_background" = "?android:attr/colorBackground";
    "@color/mm_surface_dim" = "?attr/colorSurfaceVariant";
    "@color/mm_surface" = "?attr/colorSurface";
    "@color/mm_on_surface_secondary" = "?attr/colorOnSurfaceVariant";
    "@color/mm_on_surface" = "?attr/colorOnSurface";
    "@color/mm_primary" = "?attr/colorPrimary";
    "@color/mm_accent" = "?attr/colorSecondary";
    "@color/mm_error" = "?attr/colorError";
    "@color/mm_success" = "?attr/mmSuccess";
    "@color/mm_chart_bg" = "?attr/mmChartBg";
    "@color/mm_chart_grid" = "?attr/mmChartGrid";
    "@color/mm_chart_line" = "?attr/mmChartLine";
    "@color/mm_chart_dot" = "?attr/mmChartDot";
    "@color/mm_calendar_selected" = "?attr/mmCalendarSelected";
    "@color/mm_calendar_today_border" = "?attr/mmCalendarTodayBorder";
    "@color/mm_calendar_outside_month" = "?attr/mmCalendarOutsideMonth";
    "@color/mm_mood_happy" = "?attr/mmMoodHappy";
    "@color/mm_mood_stressed" = "?attr/mmMoodStressed";
    "@color/mm_mood_tired" = "?attr/mmMoodTired";
    "@color/mm_mood_focused" = "?attr/mmMoodFocused";
    "@color/mm_mood_bored" = "?attr/mmMoodBored";
    "@color/mm_mood_neutral" = "?attr/mmMoodNeutral"
}

Get-ChildItem -Path "c:\Users\minod\AndroidStudioProjects\MirrorMood\app\src\main\res" -Recurse -Filter "*.xml" | Where-Object {
    $_.FullName -notmatch '\\values(-night)?\\' -or $_.Name -eq 'styles.xml'
} | ForEach-Object {
    $content = [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8)
    $orig_content = $content
    foreach ($key in $mapping.Keys) {
        $content = $content.Replace($key, $mapping[$key])
    }
    if ($content -cne $orig_content) {
        [System.IO.File]::WriteAllText($_.FullName, $content, [System.Text.Encoding]::UTF8)
        Write-Output "Updated $($_.FullName)"
    }
}
