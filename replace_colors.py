import os

mapping = {
    "@color/mm_background_alt": "?attr/mmBackgroundAlt",
    "@color/mm_background": "?android:attr/colorBackground",
    "@color/mm_surface_dim": "?attr/colorSurfaceVariant",
    "@color/mm_surface": "?attr/colorSurface",
    "@color/mm_on_surface_secondary": "?attr/colorOnSurfaceVariant",
    "@color/mm_on_surface": "?attr/colorOnSurface",
    "@color/mm_primary": "?attr/colorPrimary",
    "@color/mm_accent": "?attr/colorSecondary",
    "@color/mm_error": "?attr/colorError",
    "@color/mm_success": "?attr/mmSuccess",
    "@color/mm_chart_bg": "?attr/mmChartBg",
    "@color/mm_chart_grid": "?attr/mmChartGrid",
    "@color/mm_chart_line": "?attr/mmChartLine",
    "@color/mm_chart_dot": "?attr/mmChartDot",
    "@color/mm_calendar_selected": "?attr/mmCalendarSelected",
    "@color/mm_calendar_today_border": "?attr/mmCalendarTodayBorder",
    "@color/mm_calendar_outside_month": "?attr/mmCalendarOutsideMonth",
    "@color/mm_mood_happy": "?attr/mmMoodHappy",
    "@color/mm_mood_stressed": "?attr/mmMoodStressed",
    "@color/mm_mood_tired": "?attr/mmMoodTired",
    "@color/mm_mood_focused": "?attr/mmMoodFocused",
    "@color/mm_mood_bored": "?attr/mmMoodBored",
    "@color/mm_mood_neutral": "?attr/mmMoodNeutral"
}

res_path = r"c:\Users\minod\AndroidStudioProjects\MirrorMood\app\src\main\res"

for root, dirs, files in os.walk(res_path):
    for file in files:
        if file.endswith(".xml"):
            file_path = os.path.join(root, file)
            
            # Skip values except styles.xml
            if ("\\values\\" in root or "\\values-" in root) and file != "styles.xml":
                continue
                
            with open(file_path, "r", encoding="utf-8") as f:
                content = f.read()
                
            orig_content = content
            for k, v in mapping.items():
                content = content.replace(k, v)
                
            if content != orig_content:
                with open(file_path, "w", encoding="utf-8") as f:
                    f.write(content)
                print(f"Updated {file_path}")

print("Replacement complete.")
