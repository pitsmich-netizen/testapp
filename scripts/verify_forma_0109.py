#!/usr/bin/env python3
"""FORMA 0.10.9: independent, ordinary lock-screen rest countdown."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
rest=(root/"app/src/main/java/pl/pitsmich/forma/RestTimer.java").read_text(encoding="utf8")
regular=(root/"app/src/main/java/pl/pitsmich/forma/RestCountdownNotification.java").read_text(encoding="utf8")
service=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutStatusService.java").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
assert "versionCode 27" in gradle and "versionName '0.10.9'" in gradle
assert "class RestCountdownNotification" in regular
assert "new Notification.Builder(context,CHANNEL)" in regular
assert "NotificationManager.IMPORTANCE_DEFAULT" in regular
assert "manager.notify(ID,notification)" in regular
assert "setUsesChronometer(true)" in regular
assert "setChronometerCountDown(true)" in regular
assert "setWhen(end)" in regular
assert "setVisibility(Notification.VISIBILITY_PUBLIC)" in regular
assert "channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC)" in regular
assert "setContentIntent(open)" in regular
assert "setTimeoutAfter(" in regular
assert "setSound(null,null)" in regular
assert "channel.enableVibration(false)" in regular
assert "setOnlyAlertOnce(true)" in regular
assert ".addAction(" not in regular
assert "RestCountdownNotification.show(context,end);" in rest
assert rest.count("RestCountdownNotification.show(context,end);")==2
assert "RestCountdownNotification.cancel(context);" in rest
assert rest.count("RestCountdownNotification.cancel(context);")==2
assert "if(canNotify(context))showFinished(context)" in rest
assert "setUsesChronometer(true)" not in service
assert "setChronometerCountDown(true)" not in service
assert "startForeground(ID,build(this))" in service
assert 'super(context, "forma_workouts.db", null, 10);' in (root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text()
print("FORMA 0.10.9: separate normal countdown notification and cleanup OK")
