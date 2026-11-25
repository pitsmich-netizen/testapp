#!/usr/bin/env python3
"""FORMA 0.10.11: restore working normal rest notification, preserve icon and cleanup."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
timer=(root/"app/src/main/java/pl/pitsmich/forma/RestTimer.java").read_text()
regular=(root/"app/src/main/java/pl/pitsmich/forma/RestCountdownNotification.java").read_text()
service=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutStatusService.java").read_text()
gradle=(root/"app/build.gradle").read_text()
assert "versionCode 36" in gradle and "versionName '0.10.18'" in gradle
assert "RestCountdownNotification.show(context,end)" in timer
assert timer.count("RestCountdownNotification.show(context,end)") == 2
assert "RestCountdownNotification.cancel(context)" in timer
assert timer.count("RestCountdownNotification.cancel(context)") == 2
assert "setWhen(end)" in regular and "setUsesChronometer(true)" in regular
assert "setChronometerCountDown(true)" in regular
assert "NotificationManager.IMPORTANCE_DEFAULT" in regular
assert "setContentIntent(open)" in regular
assert "R.drawable.ic_forma_notification" in regular
assert "setUsesChronometer(true)" not in service
assert "setChronometerCountDown(true)" not in service
assert "hasRunningWorkout(context)" in service
assert "manager.cancel(ID)" in service
assert "R.drawable.ic_forma_notification" in service
assert (root/"app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").is_file()
assert 'super(context, "forma_workouts.db", null, 11);' in (root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text()
print("FORMA 0.10.11 restored independent rest countdown and retained icon/data.")
