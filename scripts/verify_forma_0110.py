#!/usr/bin/env python3
"""Regression: branded launcher and notification icon retained after timer rollback."""
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
manifest=(root/"app/src/main/AndroidManifest.xml").read_text()
gradle=(root/"app/build.gradle").read_text()
assert "versionCode 38" in gradle and "versionName '0.10.20'" in gradle
assert 'android:icon="@mipmap/ic_launcher"' in manifest
assert 'android:roundIcon="@mipmap/ic_launcher_round"' in manifest
for path in ("app/src/main/res/drawable/ic_forma_foreground.xml",
             "app/src/main/res/drawable/ic_forma_notification.xml",
             "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
             "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
             "app/src/main/res/values/forma_icon_colors.xml"):
    ET.parse(root/path)
regular=(root/"app/src/main/java/pl/pitsmich/forma/RestCountdownNotification.java").read_text()
assert "R.drawable.ic_forma_notification" in regular
assert 'super(context, "forma_workouts.db", null, 11);' in (root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text()
print("FORMA icon and data compatibility regression passed.")
