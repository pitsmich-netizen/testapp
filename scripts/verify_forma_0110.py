#!/usr/bin/env python3
"""FORMA 0.10.10 — one persistent workout notification with integrated rest timer and proper app icon."""
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
service=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutStatusService.java").read_text(encoding="utf-8")
timer=(root/"app/src/main/java/pl/pitsmich/forma/RestTimer.java").read_text(encoding="utf-8")
gradle=(root/"app/build.gradle").read_text(encoding="utf-8")
manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
ns="{http://schemas.android.com/apk/res/android}"
assert "versionCode 28" in gradle and "versionName '0.10.10'" in gradle
assert 'android:icon="@mipmap/ic_launcher"' in manifest
assert 'android:roundIcon="@mipmap/ic_launcher_round"' in manifest
assert 'boolean rest=end>System.currentTimeMillis();' in service
assert '.setContentTitle(rest?"FORMA • Przerwa":"FORMA • Trening trwa")' in service
assert 'b.setWhen(end).setUsesChronometer(true).setChronometerCountDown(true)' in service
assert 'startForeground(ID,build(this))' in service
assert 'manager.notify(ID,build(context))' in service
assert 'if(!hasRunningWorkout(context))' in service
assert 'if(manager!=null)manager.cancel(ID)' in service
assert '.setSmallIcon(R.drawable.ic_forma_notification)' in service
assert '.setSmallIcon(R.drawable.ic_forma_notification)' in timer
assert 'RestCountdownNotification.show' not in timer
assert 'RestCountdownNotification.cancel' not in timer
assert not (root/"app/src/main/java/pl/pitsmich/forma/RestCountdownNotification.java").exists()
assert 'notifications.cancel(LEGACY_COUNTDOWN_ID);' in timer
assert 'cancelLegacyCountdown(context)' in timer
assert 'if(canNotify(context))showFinished(context)' in timer
for path in ("app/src/main/res/drawable/ic_forma_foreground.xml",
             "app/src/main/res/drawable/ic_forma_notification.xml",
             "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
             "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
             "app/src/main/res/values/forma_icon_colors.xml"):
    ET.parse(root/path)
for name in ("ic_launcher.xml","ic_launcher_round.xml"):
    icon=ET.parse(root/"app/src/main/res/mipmap-anydpi-v26"/name).getroot()
    assert icon.tag=="adaptive-icon"
    assert icon.find("foreground").get(ns+"drawable")=="@drawable/ic_forma_foreground"
assert 'super(context, "forma_workouts.db", null, 10);' in (root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf-8")
print("FORMA 0.10.10 unified notification timer, brand icon and DB compatibility OK.")
