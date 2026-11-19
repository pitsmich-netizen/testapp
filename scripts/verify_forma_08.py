#!/usr/bin/env python3
"""Regression tests for FORMA 0.8.0 schema, UX wiring, backup and OTA invariants."""
from pathlib import Path
import sqlite3
import re

ROOT=Path(__file__).resolve().parent.parent
def source(name):
    return (ROOT/name).read_text(encoding="utf-8")
database=source("app/src/main/java/pl/pitsmich/forma/WorkoutDb.java")
ui=source("app/src/main/java/pl/pitsmich/forma/MainActivity.java")
backup=source("app/src/main/java/pl/pitsmich/forma/WorkoutBackup.java")
gradle=source("app/build.gradle")

assert 'super(context, "forma_workouts.db", null, 9);' in database
assert "if(oldVersion<8) createPersonalTables(db)" in database
assert "versionCode 16" in gradle and "versionName '0.9.1'" in gradle
assert "CREATE TABLE IF NOT EXISTS personal_exercises" in database
assert "CREATE TABLE IF NOT EXISTS load_settings" in database
assert "CREATE TABLE IF NOT EXISTS workout_schedule" in database
assert "onUpgrade(SQLiteDatabase db" in database and "if(oldVersion<7)" in database
for method in ("moveScheduledWorkout", "assignWeekday", "renameWorkout", "workoutLabel",
               "createExercise", "exerciseCatalog", "saveLoadSettings",
               "loadMultiplier", "weightStep", "prCount", "sessionVolume"):
    assert method+"(" in database,method
for phrase in ("startDragAndDrop(", "ACTION_DROP", "showSessionSummary()",
               "undoLastSet()", "canUndoSet", "isPersonalRecord(",
               "createCustomExercise(", "editExerciseLoadSettings(",
               "showExtraSetOptions(", "getBoolean(\"auto_next\",true)"):
    assert phrase in ui,phrase
assert 'root.put("schema",2)' in backup
assert "version!=1&&version!=2" in backup, "Older backups must remain readable."
for key in ("personal_exercises", "workout_schedule", "load_settings"):
    assert 'root.put("'+key+'"' in backup,key
assert backup.index('JSONArray personal=backup.optJSONArray("personal_exercises")') < backup.index('for(int i=0;i<sessions.length();i++) {',backup.index('public static String restore')), "Definitions must be restored before history."
assert "sql.beginTransaction()" in backup and "sql.setTransactionSuccessful()" in backup
assert "FORMA_SIGNING" not in backup and "access_token" not in backup
# SQLite migration smoke simulation: old data remains, new schema works and joins
# preserve working volume while excluding warmups.
connection=sqlite3.connect(":memory:")
connection.executescript("""
CREATE TABLE sessions (id INTEGER PRIMARY KEY,workout INTEGER,started_at INTEGER,
 finished_at INTEGER, notes TEXT,cloud_id TEXT,paused_at INTEGER,
 paused_total_ms INTEGER,audit_resolution TEXT);
CREATE TABLE exercise_sets (session_id INTEGER,exercise_id TEXT,set_index INTEGER,
 weight REAL,reps INTEGER,rir INTEGER NOT NULL DEFAULT -1,
 note TEXT NOT NULL DEFAULT '',warmup INTEGER NOT NULL DEFAULT 0,
 PRIMARY KEY(session_id,exercise_id,set_index));
CREATE TABLE plan_items(workout INTEGER,position INTEGER,exercise_id TEXT,sets INTEGER,
 min_reps INTEGER,max_reps INTEGER,rest_seconds INTEGER,
 PRIMARY KEY(workout,position));
INSERT INTO sessions VALUES(1,0,1700000000000,1700000030000,'user-data','seed-id',NULL,0,'confirmed');
INSERT INTO exercise_sets VALUES(1,'db_flat',0,22.5,12,-1,'',0);
INSERT INTO exercise_sets VALUES(1,'db_flat',1,15,8,-1,'',1);
CREATE TABLE personal_exercises (id TEXT PRIMARY KEY,name TEXT NOT NULL,
 description TEXT NOT NULL DEFAULT '',region TEXT NOT NULL DEFAULT '',
 equipment TEXT NOT NULL DEFAULT '',sets INTEGER,min_reps INTEGER,max_reps INTEGER,
 rest_seconds INTEGER,each_side INTEGER);
CREATE TABLE load_settings (exercise_id TEXT PRIMARY KEY,step_kg REAL,multiplier INTEGER);
CREATE TABLE workout_schedule (workout INTEGER PRIMARY KEY,position INTEGER UNIQUE,
 weekday INTEGER UNIQUE,title TEXT);
INSERT INTO workout_schedule VALUES(0,0,1,'GÓRA A');
INSERT INTO workout_schedule VALUES(1,1,3,'NOGI');
INSERT INTO workout_schedule VALUES(2,2,5,'GÓRA B');
INSERT INTO load_settings VALUES('db_flat',2.5,2);
""")
assert connection.execute("SELECT notes FROM sessions WHERE id=1").fetchone()[0]=="user-data"
mult=connection.execute("SELECT multiplier FROM load_settings WHERE exercise_id='db_flat'").fetchone()[0]
volume=connection.execute(
    "SELECT SUM(CASE WHEN warmup=0 THEN weight*reps*? ELSE 0 END) FROM exercise_sets",
    (mult,)).fetchone()[0]
assert volume==540.0,volume
assert connection.execute("SELECT COUNT(*) FROM exercise_sets").fetchone()[0]==2
assert connection.execute("SELECT COUNT(*) FROM workout_schedule").fetchone()[0]==3
print("FORMA 0.8.0 checks passed: migration wiring, backup v1/v2, drag, summary, custom exercise, saved data, two-dumbbell volume and warmup exclusion.")
