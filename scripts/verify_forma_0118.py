#!/usr/bin/env python3
"""FORMA 0.10.18: muscle groups, weekly attribution, legacy-safe migration and backup."""
from pathlib import Path
import re
import sqlite3
root=Path(__file__).resolve().parents[1]
main=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
backup=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutBackup.java").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
assert "versionCode 36" in gradle and "versionName '0.10.18'" in gradle
assert 'super(context, "forma_workouts.db", null, 11);' in db
assert "createMuscleGroupTable(db);" in db
assert "if(oldVersion<11) createMuscleGroupTable(db);" in db
assert "CREATE TABLE IF NOT EXISTS exercise_groups (" in db
assert "exercise_id TEXT PRIMARY KEY, muscle_group TEXT NOT NULL" in db
assert "exercise_groups" not in db[db.index("CREATE TABLE exercise_sets"):db.index('createExerciseComments(db);')]
assert 'public static final String[] MUSCLE_GROUPS=' in db
for name in ("Klatka","Plecy","Nogi","Barki","Biceps","Triceps","Łydki","Core","Inne"):
    assert '"'+name+'"' in db
assert "public void saveMuscleGroup(String exerciseId,String group)" in db
assert "isValidMuscleGroup(group)" in db
assert "group.equals(defaultMuscleGroup(exerciseId))" in db
assert 'if("bi_cable".equals(id))return "Biceps";' in db
assert 'return "Inne";' in db
assert "return db.muscleGroup(id);" in main
assert "String group=groupCache.get(entry.exerciseId);" in main
assert "if(entry.warmup)continue;" in main
assert "WorkoutDb.isTestSession(session)" in main
assert "previous.sets.getOrDefault(group,0)" in main
assert "vs poprzedni tydzień:" in main
assert 'db.muscleGroup(exercise.id)' in main
assert "editExerciseMuscleGroup(db.workoutPlan(editingWorkout).get(position))" in main
assert "db.saveMuscleGroup(exercise.id,WorkoutDb.MUSCLE_GROUPS[index]);" in main
assert "db.saveMuscleGroup(exercise.id," in main
assert "PARTIA MIĘŚNIOWA — STATYSTYKI" in main
assert "muscleGroup.setSelection(WorkoutDb.MUSCLE_GROUPS.length-1)" in main
assert "root.put(\"schema\",4);" in backup
assert "version!=1&&version!=2&&version!=3&&version!=4" in backup
assert 'root.put("muscle_groups",muscleGroups);' in backup
assert 'JSONArray muscleGroups=backup.optJSONArray("muscle_groups");' in backup
assert 'if(muscleGroups!=null)' in backup # No overrides lost when importing older backups.
assert "WorkoutDb.isValidMuscleGroup(group)" in backup
assert 'sql.delete("exercise_groups",null,null);' in backup
assert "sql.insertOrThrow(\"exercise_groups\",null,row);" in backup
assert 'sql.beginTransaction()' in backup and 'sql.setTransactionSuccessful()' in backup
assert "RestCountdownNotification.show(context,end);" in (root/"app/src/main/java/pl/pitsmich/forma/RestTimer.java").read_text()
# Group changes are additive: exercise_sets and old training results remain intact.
sql=sqlite3.connect(":memory:")
sql.execute("CREATE TABLE sessions(id INTEGER PRIMARY KEY, started_at INTEGER)")
sql.execute("CREATE TABLE exercise_sets(session_id INTEGER, exercise_id TEXT, set_index INTEGER, weight REAL, reps INTEGER)")
sql.execute("INSERT INTO sessions VALUES (7,123)")
sql.execute("INSERT INTO exercise_sets VALUES (7,'custom_123',0,20,12)")
sql.execute("CREATE TABLE IF NOT EXISTS exercise_groups(exercise_id TEXT PRIMARY KEY, muscle_group TEXT NOT NULL)")
sql.execute("INSERT INTO exercise_groups VALUES ('custom_123','Klatka')")
assert sql.execute("SELECT weight,reps FROM exercise_sets WHERE session_id=7").fetchone()==(20,12)
assert sql.execute("SELECT muscle_group FROM exercise_groups WHERE exercise_id='custom_123'").fetchone()==("Klatka",)
print("FORMA 0.10.18: groups, backup v4 and backward compatibility, weekly counts and existing sets OK")
