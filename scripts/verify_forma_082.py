#!/usr/bin/env python3
"""FORMA 0.8.2: UI compatibility checks; Android build validates Java symbols."""
from pathlib import Path
root=Path(__file__).resolve().parent.parent
main=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text()
gradle=(root/"app/build.gradle").read_text()
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text()
assert "versionCode 25" in gradle
assert "versionName '0.10.7'" in gradle
assert 'super(context, "forma_workouts.db", null, 10);' in db
assert 'TextView version=text(BuildConfig.VERSION_NAME' in main
assert 'if(!page.equals("session")) {' in main, "No global gear on workout screen"
assert 'title.setGravity(Gravity.CENTER);' in main, "Workout title centered"
assert 'controls.setOnClickListener(v->showSessionActions())' in main
assert 'day.setTextSize(17)' in main, "Larger weekday typography"
assert "TextView minus=actionLabel(\"−\",AMBER)" in main
assert "TextView plus=actionLabel(\"+\",GREEN)" in main
assert "TextView repMinus=actionLabel(\"−\",MUTED)" in main
assert "TextView repPlus=actionLabel(\"+\",MUTED)" in main
assert 'Button save=button("✓",true)' in main
assert "rirDraft[0]=selected-1" in main
assert 'db.save(openSession,ex.id,index,w,repsValue,rirDraft[0]' in main
assert "private void changeReps(" in main
assert "copy.setOnClickListener" not in main, "Old copy button must not clutter workout"
assert 'if(finished && expandedExerciseId.equals(ex.id)' in main
assert "advanceAfterLastSet(ex)" in main
assert "undoLastSet()" in main
assert "RestTimer.start(this,seconds)" in main
print("FORMA 0.8.2 UI wiring, settings isolation, versions, data preservation: OK")
