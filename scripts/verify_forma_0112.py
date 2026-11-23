#!/usr/bin/env python3
"""FORMA 0.10.12: session input is prefetched, rest transitions only on confirmed new sets."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
timer=(root/"app/src/main/java/pl/pitsmich/forma/RestTimer.java").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
assert "versionCode 32" in gradle and "versionName '0.10.14'" in gradle
assert "WorkoutDb.Entry sessionPrevious=null;" in ui
assert "sessionPrevious=db.get(openSession,ex.id,prior);" in ui
assert "WorkoutDb.Entry suggested=sessionPrevious!=null?sessionPrevious:previous;" in ui
assert "suggested!=null?weight(suggested.weight)" in ui
assert "suggested!=null?Integer.toString(suggested.reps)" in ui
assert "Ostatnia seria: " in ui and "Poprz. trening: " in ui
assert "Cel: " in ui and "ex.minReps" in ui
assert "db.save(openSession,ex.id,index,w,repsValue" in ui
assert "if(previousEntry==null) {\n                    stopRest();" in ui
assert "boolean moreSets=db.countExerciseSets(openSession,ex.id)<db.expectedSets(openWorkout,ex);" in ui
assert "if(moreSets && ex.restSeconds>0)startRest(ex.restSeconds);" in ui
assert "restProgress" in ui and "timerShell.setPadding(dp(12),dp(7),dp(12),dp(8))" in ui
assert "NotificationManager" not in ui or "RestTimer.stop(this);" in ui
assert "RestCountdownNotification.show(context,end);" in timer
assert "RestCountdownNotification.cancel(context);" in timer
assert (root/"app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").exists()
assert 'super(context, "forma_workouts.db", null, 10);' in (root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
print("FORMA 0.10.12: confirmed set rest transitions, prefill, compact UI and stable notifications OK")
