#!/usr/bin/env python3
"""Regression: native Android countdown continues in a foreground workout notification."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
service=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutStatusService.java").read_text(encoding="utf8")
rest=(root/"app/src/main/java/pl/pitsmich/forma/RestTimer.java").read_text(encoding="utf8")
activity=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf8")
assert "extends Service" in service and "startForeground(ID,build(this))" in service
assert "setUsesChronometer(true)" in service and "setChronometerCountDown(true)" in service
assert "NotificationManager.IMPORTANCE_HIGH" in service
assert "setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)" in service
assert "Notification.VISIBILITY_PUBLIC" in service and "setContentIntent(tap)" in service
assert ".addAction(" not in service
assert 'android:foregroundServiceType="specialUse"' in manifest
assert "android.permission.FOREGROUND_SERVICE" in manifest
assert "WorkoutStatusService.refresh(context)" in rest
assert "showFinished(context)" in rest
assert "channel.enableVibration(true)" in rest
assert "syncWorkoutNotification();" in activity
assert "WorkoutStatusService.stop(this);" in activity
assert 'if(RestTimer.ACTION_OPEN_SESSION.equals(intent.getAction()))' in activity
print("FORMA persistent rest notification regression passed.")
