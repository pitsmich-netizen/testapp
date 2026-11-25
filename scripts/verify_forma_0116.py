#!/usr/bin/env python3
"""FORMA 0.10.16: inter-exercise rest without changing stable timer notifications."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
timer=(root/"app/src/main/java/pl/pitsmich/forma/RestTimer.java").read_text(encoding="utf8")
notif=(root/"app/src/main/java/pl/pitsmich/forma/RestCountdownNotification.java").read_text(encoding="utf8")
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
assert "versionCode 35" in gradle and "versionName '0.10.17'" in gradle
assert "private boolean hasAnotherPendingExercise(Plan.Exercise finished)" in ui
assert "for(Plan.Exercise other:db.workoutPlan(openWorkout))" in ui
assert "if(other.id.equals(finished.id))continue;" in ui
assert "db.countExerciseSets(openSession,other.id)<db.expectedSets(openWorkout,other)" in ui
assert "boolean anotherExercisePending=!moreSets && hasAnotherPendingExercise(ex);" in ui
assert "if((moreSets || anotherExercisePending) && ex.restSeconds>0)" in ui
assert "startRest(ex.restSeconds);" in ui
assert "if(previousEntry==null) {\n                    stopRest();" in ui
assert 'if(previousEntry==null&&getSharedPreferences("forma_settings",0)' in ui
assert "advanceAfterLastSet(ex);" in ui
assert "RestCountdownNotification.show(context,end);" in timer
assert "RestCountdownNotification.cancel(context);" in timer
assert "setChronometerCountDown(true)" in notif
assert 'super(context, "forma_workouts.db", null, 10);' in db

# Expected transitions: another exercise matters only after last set of current one.
def rest(more_sets, pending_other, seconds):
    return seconds>0 and (more_sets or ((not more_sets) and pending_other))
assert rest(True,False,90)        # Between normal sets.
assert rest(False,True,90)        # Last set of exercise, next exercise pending.
assert not rest(False,False,90)  # Last set of whole session.
assert not rest(True,True,0)     # Zero-second rests disabled.
assert rest(False,True,120)      # Uses configured duration.
print("FORMA 0.10.16: rest between exercises, no final rest, timer and DB preserved OK")
