import re
import os

lint_file = r"f:\AndroidStudioProjects\MirrorMood\app\build\reports\lint-results-debug.txt"

unused_files = []
unused_values = []

with open(lint_file, "r", encoding="utf-8") as f:
    text = f.read()

# Lines with UnusedResources look like:
# F:\...\app\src\main\res\drawable\bg_app_surface.xml:2: Warning: The resource R.drawable.bg_app_surface appears to be unused [UnusedResources]
pattern = re.compile(r"^(.*?):(\d+): Warning: The resource (.*?) appears to be unused \[UnusedResources\]", re.MULTILINE)

for match in pattern.finditer(text):
    filepath = match.group(1).strip()
    line_num = int(match.group(2))
    resource_name = match.group(3)
    
    # Standalone files (like drawable, anim, layout) generally define exactly 1 resource matching the filename or inside tags.
    # Note: R.drawable.xxx doesn't rule out it being inside a values xml, but if path contains \values\ it's definitely a compound file.
    
    if r"\values\"" in filepath or "values\\" in filepath or "values/" in filepath:
        unused_values.append((filepath, line_num, resource_name))
    else:
        # Other standalone files, e.g. drawable, layout, anim
        unused_files.append(filepath)

# Dedup files
unused_files = list(set(unused_files))

print(f"File resources to delete: {len(unused_files)}")
for f in unused_files:
    if os.path.exists(f):
        print("Deleting:", f)
        os.remove(f)
    else:
        print("Missing:", f)

print(f"\nValue resources to update manually: {len(unused_values)}")
# Let's remove the lines from values files by reading file, breaking into lines, and commenting out or removing the surrounding <element> tag.
# For simplicity, we just print them so we can handle them with multi_replace_file_content or a smarter python logic.

# Let's write a quick XML element remover.
import xml.etree.ElementTree as ET

# Instead of full XML parsing, which loses comments/formatting, simple regex line removal or multi-replace works best.
# We'll just list them out.
for v in sorted(list(set(unused_values))):
    print(v)
