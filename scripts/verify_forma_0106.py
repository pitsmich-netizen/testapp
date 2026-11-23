#!/usr/bin/env python3
"""FORMA background workout status and timer regression."""
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
main=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
rest=(root/"app/src/main/java/pl/pitsmich/forma/RestTimer.java").read_text(encoding="utf8")
service=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutStatusService.java").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
manifest=ET.fromstring((root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf8"))
ns="{http://schemas.android.com/apk/res/android}"
assert "versionCode 31" in gradle and "versionName '0.10.13'" in gradle
assert 'super(context, "forma_workouts.db", null, 10);' in (root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
assert "WorkoutStatusService.start(this)" in main and "WorkoutStatusService.stop(this)" in main
assert "syncWorkoutNotification();" in main and "askWorkoutNotificationPermission();" in main
assert "startForeground(ID,build(this))" in service
assert "NotificationManager.IMPORTANCE_HIGH" in service
assert "setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)" in service
assert "setUsesChronometer(true)" not in service
assert "setChronometerCountDown(true)" not in service
assert "setOngoing(true)" in service and "setOnlyAlertOnce(true)" in service
assert "setContentIntent(tap)" in service
assert ".addAction(" not in service
assert "WorkoutStatusService.refresh(context)" in rest
assert "showFinished(context)" in rest
assert "setExactAndAllowWhileIdle" in rest
assert "RestTimer.finishIfExpired" in (root/"app/src/main/java/pl/pitsmich/forma/RestAlarmReceiver.java").read_text()
serviceTag=manifest.find("./application/service")
assert serviceTag is not None and serviceTag.get(ns+"name")==".WorkoutStatusService"
assert serviceTag.get(ns+"foregroundServiceType")=="specialUse"
permissions={p.get(ns+"name") for p in manifest.findall("uses-permission")}
assert {"android.permission.FOREGROUND_SERVICE","android.permission.FOREGROUND_SERVICE_SPECIAL_USE","android.permission.POST_NOTIFICATIONS"}.issubset(permissions)
print("FORMA persistent workout status and timer regressions passed.")
