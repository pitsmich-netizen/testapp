#!/usr/bin/env python3
"""FORMA 0.10.6: persistent workout notification and sharp offline guides."""
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
main=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
rest=(root/"app/src/main/java/pl/pitsmich/forma/RestTimer.java").read_text(encoding="utf8")
service=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutStatusService.java").read_text(encoding="utf8")
art=(root/"app/src/main/java/pl/pitsmich/forma/ExerciseDiagramView.java").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
manifest=ET.fromstring((root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf8"))
ns="{http://schemas.android.com/apk/res/android}"
assert "versionCode 24" in gradle and "versionName '0.10.6'" in gradle
assert 'super(context, "forma_workouts.db", null, 10);' in (root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
assert "WorkoutStatusService.start(this)" in main and "WorkoutStatusService.stop(this)" in main
assert "syncWorkoutNotification();" in main
assert "askWorkoutNotificationPermission();" in main
assert "startForeground(ID,build(this))" in service
assert "NotificationManager.IMPORTANCE_DEFAULT" in service
assert "setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)" in service
assert "setUsesChronometer(true)" in service
assert "setChronometerCountDown(true)" in service
assert "setWhen(until)" in service
assert "setOngoing(true)" in service
assert "setOnlyAlertOnce(true)" in service
assert "setContentIntent(tap)" in service
assert ".addAction(" not in service
assert "WorkoutStatusService.refresh(context)" in rest
assert "showFinished(context)" in rest
assert 'CHANNEL_COUNTDOWN' not in rest
assert "setExactAndAllowWhileIdle" in rest
assert "RestTimer.finishIfExpired" in (root/"app/src/main/java/pl/pitsmich/forma/RestAlarmReceiver.java").read_text()
serviceTag=manifest.find("./application/service")
assert serviceTag is not None and serviceTag.get(ns+"name")==".WorkoutStatusService"
assert serviceTag.get(ns+"foregroundServiceType")=="specialUse"
permissions={p.get(ns+"name") for p in manifest.findall("uses-permission")}
assert "android.permission.FOREGROUND_SERVICE" in permissions
assert "android.permission.FOREGROUND_SERVICE_SPECIAL_USE" in permissions
assert "android.permission.POST_NOTIFICATIONS" in permissions
assert art.count('case "')>=9
assert "Paint.ANTI_ALIAS_FLAG" in art
assert "canvas.scale(scale,scale)" in art
assert "ExerciseDiagramView.supports(exerciseId)" in main
assert main.count("appendExerciseIllustration(guide,ex.id);")==2
assert "Powiększ schemat" in main
assert not (root/"app/src/main/assets/forma_atlas_0.base64").exists()
print("FORMA 0.10.6: persistent silent workout status, native rest countdown, scalable vector guides and migration compatibility OK")
