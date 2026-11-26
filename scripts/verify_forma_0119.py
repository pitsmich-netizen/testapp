#!/usr/bin/env python3
"""FORMA 0.10.19: conservative, explicit next-workout suggestions."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
app=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
policy=(root/"app/src/main/java/pl/pitsmich/forma/ProgressionPolicy.java").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
timer=(root/"app/src/main/java/pl/pitsmich/forma/RestTimer.java").read_text(encoding="utf8")
assert "versionCode 39" in gradle and "versionName '0.10.21'" in gradle
assert 'super(context, "forma_workouts.db", null, 11);' in db
assert 'private long lastExerciseSession(int workout,String exercise)' in db
assert 's.workout=? AND e.exercise_id=?' in db
assert 's.finished_at IS NOT NULL AND s.notes NOT LIKE' in db
assert 'WHERE session_id=? AND exercise_id=? AND warmup=0 ORDER BY set_index ASC' in db
assert 'public ProgressionPolicy.Suggestion nextSetSuggestion(' in db
assert 'int floor=exercise.rirMin<0?1:exercise.rirMin;' in db
assert 'ProgressionPolicy.recommend(prior,setIndex,expected,' in db
assert "boolean complete=previous.size()==expectedSets;" in policy
assert "if(entry.rir<minRir)allSafeRir=false;" in policy
assert "if(entry.reps<maxReps)allAtCeiling=false;" in policy
assert "if(Math.abs(firstWeight-entry.weight)>0.001)sameWeight=false;" in policy
assert "complete && sameWeight && allAtCeiling && allSafeRir" in policy
assert "firstWeight+increment<=2000" in policy
assert 'if(target.rir<0)' in policy
assert 'if(target.rir<minRir)' in policy
assert 'Math.min(maxReps,target.reps+1)' in policy
assert 'target.weight,target.reps,false' in policy
assert 'db.nextSetSuggestion(proposed,openWorkout,0)' in app
assert 'db.nextSetSuggestion(ex,openWorkout,index)' in app
assert 'db.nextSetSuggestion(planned,selectedProgressWorkout,0)' in app
assert 'String tip=db.progressionTip(proposed);' not in app
assert 'String suggestion=db.progressionTip(ex);' not in app
assert 'apply.setOnClickListener(v->{' in app
assert 'kg.setText(weight(next.weight));' in app
assert 'reps.setText(Integer.toString(next.reps));' in app
assert 'Zatwierdź' not in app or 'Wstawiono propozycję' in app
assert 'if(previousEntry==null) {\n                    stopRest();' in app
assert "boolean anotherExercisePending=!moreSets && hasAnotherPendingExercise(ex);" in app
assert "RestCountdownNotification.show(context,end);" in timer
print("FORMA 0.10.19: no silent writes, conservative RIR, workout scoping and timer unchanged")
