#!/usr/bin/env python3
"""FORMA 0.10.5: simple system chronometer on lock screen, no action buttons."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
rest=(root/"app/src/main/java/pl/pitsmich/forma/RestTimer.java").read_text(encoding="utf8")
activity=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
assert "versionCode 23" in gradle and "versionName '0.10.5'" in gradle
assert 'ACTION_OPEN_SESSION = "pl.pitsmich.forma.OPEN_ACTIVE_SESSION"' in rest
assert 'NotificationManager.IMPORTANCE_LOW' in rest
assert "setUsesChronometer(true)" in rest
assert "setChronometerCountDown(true)" in rest
assert "setWhen(end)" in rest
assert "setVisibility(Notification.VISIBILITY_PUBLIC)" in rest
assert "channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC)" in rest
assert "setOnlyAlertOnce(true)" in rest
assert "setSilent(true)" in rest
assert "setContentIntent(open)" in rest
assert "setOngoing(true)" in rest
assert "setTimeoutAfter" in rest
assert ".addAction(" not in rest
assert "showCountdown(context,end);" in rest
assert "notifications.cancel(COUNTDOWN_ID);" in rest
assert "clearCountdown(context);" in rest
assert "RestTimer.finishIfExpired" in (root/"app/src/main/java/pl/pitsmich/forma/RestAlarmReceiver.java").read_text()
assert "showFinished(context)" in rest and "channel.enableVibration(true)" in rest
assert 'if(RestTimer.ACTION_OPEN_SESSION.equals(intent.getAction()))' in activity
assert 'openSession=active.id;' in activity and 'page="session";' in activity
assert "requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},6071)" in activity
assert "if(requestCode==6071 && RestTimer.canNotify(this))" in activity
print("FORMA 0.10.5: native seconds countdown, lockscreen, tap to resume, alerts and cleanup OK.")
