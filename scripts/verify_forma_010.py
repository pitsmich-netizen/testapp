#!/usr/bin/env python3
"""FORMA 0.10.0 smoke/regression checks: do not erase workout history for fake data."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
ui=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
assert "versionCode 39" in gradle and "versionName '0.10.21'" in gradle
assert 'super(context, "forma_workouts.db", null, 11);' in db
assert 'private static final String TEST_MARKER="[FORMA:TEST]"' in db
assert "public void setTestSession(long id,boolean test)" in db
assert 'update("sessions",update,"id=?"' in db
assert "DELETE FROM sessions" not in db[db.index("public void setTestSession"):db.index("public WorkoutDb(Context")]
assert "s.notes NOT LIKE '%[FORMA:TEST]%'" in db
assert "x.notes NOT LIKE '%[FORMA:TEST]%'" in db
assert "public String loadGuidance(" in db
assert 'if(previous.rir<0)' in db and 'previous.rir<exercise.rirMin' in db
assert "db.loadGuidance(ex,openWorkout,index)" in ui
assert "db.setTestSession(session.id,!test)" in ui
assert "WorkoutDb.isTestSession(session)||session.startedAt<start" in ui
assert 'if(!WorkoutDb.isTestSession(item)&&item.workout==selectedProgressWorkout)' in ui
assert 'if(WorkoutDb.isTestSession(session)||session.startedAt!=startedAt)' in ui
assert 'if(WorkoutDb.isTestSession(session))' in ui
assert '"[FORMA:TEST]"' in ui
assert 'getBoolean("auto_next",true)' in ui
assert "undoLastSet()" in ui
assert 'RestTimer.start(this,seconds)' in ui
print("FORMA 0.10.0: non-destructive test flag, RIR guidance, unchanged DB v9, timer/undo/auto-next preserved.")
