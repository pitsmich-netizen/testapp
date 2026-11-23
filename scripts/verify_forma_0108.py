#!/usr/bin/env python3
"""FORMA 0.10.8: no stale active-workout notification after pause, cancellation or finish."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
service=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutStatusService.java").read_text(encoding="utf8")
activity=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
timer=(root/"app/src/main/java/pl/pitsmich/forma/RestTimer.java").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
assert "versionCode 32" in gradle and "versionName '0.10.14'" in gradle
assert "private static boolean hasRunningWorkout(Context context)" in service
assert "return active!=null && active.pausedAt==0;" in service
assert "running=false;\n        NotificationManager manager" in service
assert "if(manager!=null)manager.cancel(ID);" in service
assert "context.stopService(new Intent(context,WorkoutStatusService.class));" in service
assert "if(!hasRunningWorkout(context)) {" in service
assert "stop(context);" in service
assert "if(manager!=null && running)" in service
assert "if(!hasRunningWorkout(this)){" in service
assert "stopForeground(STOP_FOREGROUND_REMOVE);" in service
assert "stopSelf(startId);" in service
assert "db.finish(id);\n                WorkoutStatusService.stop(this);" in activity
assert "db.pause(openSession);\n        WorkoutStatusService.stop(this);" in activity
assert "db.deleteSession(id);\n                WorkoutStatusService.stop(this);" in activity
assert "WorkoutStatusService.refresh(context);" in timer
assert 'super(context, "forma_workouts.db", null, 10);' in (root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
print("FORMA 0.10.8: stop immediately, reject stale timer refreshes and service restarts, data safe")
