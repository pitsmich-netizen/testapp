#!/usr/bin/env python3
"""FORMA 0.10.20: live and historical read-only workout comparison integration."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
model=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutComparison.java").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
timer=(root/"app/src/main/java/pl/pitsmich/forma/RestTimer.java").read_text(encoding="utf8")
assert "versionCode 38" in gradle and "versionName '0.10.20'" in gradle
assert 'super(context, "forma_workouts.db", null, 11);' in db
assert "public Session previousCompletedForCurrentPlan(Session current)" in db
assert "for(Plan.Exercise exercise:workoutPlan(current.workout))" in db
assert "isTestSession(candidate)" in db
assert "candidate.startedAt>=current.startedAt" in db
assert "label.equals(sessionWorkoutLabel(candidate))" in db
assert "common*2>=Math.max(planned.size(),before.size())" in db
assert "public Session previousCompletedSameWorkout(Session current)" in db
assert "else if(page.equals(\"compare\")) showWorkoutComparison();" in main
assert "if(screen.page.equals(\"compare\"))return db.byId(screen.detailSession)!=null;" in main
assert "page.equals(\"summary\")||page.equals(\"compare\")" in main
assert "page=\"compare\";render();" in main
assert "private void showWorkoutComparison()" in main
assert "ongoing?db.previousCompletedForCurrentPlan(current):" in main
assert "db.previousCompletedSameWorkout(current);" in main
assert "WorkoutDb.isTestSession(current)?null:" in main
assert "comparisonSets(current.id),comparisonSets(previous.id),planOrder" in main
assert "for(WorkoutDb.Entry entry:db.sets(sessionId))" in main
assert "db.loadMultiplier(entry.exerciseId)" in main
assert "workoutPlan(current.workout)" in main
assert "ex.currentSets+\" teraz / \"+ex.previousSets" in main
assert "ex.matchedCurrentVolume-ex.matchedPreviousVolume" in main
assert "ex.currentAverageRir()" in main
assert "ex.previousAverageRir()" in main
assert "comparisonDelta(pair)" in main
assert "if(row.current.rir>=0 && row.previous.rir>=0)" in main
assert "ΔRIR —" in main
assert "Całkowita objętość będzie niepełna" in main
assert "RIR pokazujemy osobno" in main
assert "rows.setVisibility(expanded?View.VISIBLE:View.GONE);" in main
assert "if(set==null||set.warmup" in model
assert "if(current==null||previous==null)return MISSING;" in model
assert "if(weight>=0 && reps>=0)return IMPROVED;" in model
assert "if(weight<=0 && reps<=0)return DECLINED;" in model
assert "return MIXED;" in model
assert "if(row.status!=MISSING)" in model
assert 'RestTimer.start(this,seconds)' in main
assert 'if(previousEntry==null) {\n                    stopRest();' in main
assert "renderKeepingSessionPosition();" in main
assert "RestCountdownNotification.show(context,end);" in timer
print("FORMA 0.10.20: read-only comparisons, day/plan safety and timer regressions OK")
