#!/usr/bin/env python3
"""Static wiring checks. Actual timing on a Samsung needs an on-device OTA test."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parent.parent
def read(name):
    return (root/name).read_text(encoding="utf-8")

main=read("app/src/main/java/pl/pitsmich/forma/MainActivity.java")
timer=read("app/src/main/java/pl/pitsmich/forma/RestTimer.java")
receiver=read("app/src/main/java/pl/pitsmich/forma/RestAlarmReceiver.java")
database=read("app/src/main/java/pl/pitsmich/forma/WorkoutDb.java")
manifest=ET.fromstring(read("app/src/main/AndroidManifest.xml"))
ns="{http://schemas.android.com/apk/res/android}"

permissions={p.get(ns+"name") for p in manifest.findall("uses-permission")}
assert "android.permission.POST_NOTIFICATIONS" in permissions
assert "android.permission.SCHEDULE_EXACT_ALARM" in permissions
assert "android.permission.VIBRATE" in permissions
r=manifest.find("./application/receiver")
assert r is not None and r.get(ns+"name")==".RestAlarmReceiver"
assert r.get(ns+"exported")=="false"
assert "RestTimer.finishIfExpired(context.getApplicationContext())" in receiver
assert 'private static final String KEY_END = "restUntil"' in timer
assert "setExactAndAllowWhileIdle" in timer and "setAndAllowWhileIdle" in timer
assert "NotificationChannel" in timer and "enableVibration(true)" in timer
assert "RestTimer.start(this,seconds)" in main
assert "RestTimer.stop(this)" in main
assert "RestTimer.reschedule(this)" in main
assert "RestTimer.finishIfExpired(MainActivity.this)" in main
assert "showCompletedEditor()" in main
assert "initialImportAudit(" in main and "initialImportAudit(" in database
assert 'super(context, "forma_workouts.db", null, 9);' in database
assert "db.deleteSession(sessionId)" not in database[database.index("public String initialImportAudit"):database.index("public String sessionNotes")]
assert "versionCode 12" in read("app/build.gradle")
assert "versionName '0.8.3'" in read("app/build.gradle")
print("Timer wiring, permissions, safe import audit and database compatibility: OK")
