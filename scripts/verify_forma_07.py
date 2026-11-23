#!/usr/bin/env python3
"""Static+SQLite smoke tests for FORMA 0.7.0 data compatibility."""
from pathlib import Path
import re
import sqlite3

root=Path(__file__).resolve().parent.parent
def read(filename):
    return (root/filename).read_text(encoding="utf-8")

db=read("app/src/main/java/pl/pitsmich/forma/WorkoutDb.java")
ui=read("app/src/main/java/pl/pitsmich/forma/MainActivity.java")
backup=read("app/src/main/java/pl/pitsmich/forma/WorkoutBackup.java")
cloud=read("app/src/main/java/pl/pitsmich/forma/CloudSync.java")
timer=read("app/src/main/java/pl/pitsmich/forma/RestTimer.java")
gradle=read("app/build.gradle")

assert 'super(context, "forma_workouts.db", null, 10);' in db
assert "versionCode 32" in gradle and "versionName '0.10.14'" in gradle
assert "if(oldVersion<6)" in db and "if(oldVersion<7)" in db
assert "audit_resolution" in db and "plan_items" in db
for column in ("rir", "note", "warmup"):
    assert f"ALTER TABLE exercise_sets ADD COLUMN {column} " in db
    assert column in cloud
assert "e.warmup=0" in db and "CASE WHEN e.warmup=0" in db
assert "workoutPlan(" in db and "savePlan(" in db and "previousWorkout(" in db
assert "showPlanEditor()" in ui and "showCalendar()" in ui
assert "editSetMetadata(" in ui and "changeWeight(" in ui
assert "WorkoutBackup.restore(" in ui and "WorkoutBackup.exportData(" in ui
assert "forma-workout-backup" in backup
assert "replaceSeed" in backup and "sql.beginTransaction()" in backup
assert "FORMA_KEYSTORE" not in backup and "auth_encrypted" not in backup
assert "CHANNEL_SOUND" in timer and "CHANNEL_SILENT" in timer
assert 'getBoolean("timer_sound",true)' in timer
assert "showAuditActions(" in ui and "setAuditResolution" in ui

# Simulate upgrading a populated older device (schema 6 -> 7).
sqlite=sqlite3.connect(":memory:")
sqlite.executescript("""
CREATE TABLE sessions (id INTEGER PRIMARY KEY,workout INTEGER,started_at INTEGER,
 finished_at INTEGER, notes TEXT,cloud_id TEXT,paused_at INTEGER,
 paused_total_ms INTEGER,audit_resolution TEXT);
CREATE TABLE exercise_sets (session_id INTEGER,exercise_id TEXT,set_index INTEGER,
 weight REAL,reps INTEGER,PRIMARY KEY(session_id,exercise_id,set_index));
CREATE TABLE plan_items(workout INTEGER,position INTEGER,exercise_id TEXT,sets INTEGER,
 min_reps INTEGER,max_reps INTEGER,rest_seconds INTEGER,
 PRIMARY KEY(workout,position));
INSERT INTO sessions VALUES(1,0,1700000000000,1700000030000,'original','seed-id',NULL,0,'pending');
INSERT INTO exercise_sets VALUES(1,'db_flat',0,22.5,12);
""")
for sql in re.findall(r'db.execSQL\("(ALTER TABLE exercise_sets ADD COLUMN[^"]+)"\)',db):
    sqlite.execute(sql)
row=sqlite.execute("SELECT weight,reps,rir,note,warmup FROM exercise_sets").fetchone()
assert row==(22.5,12,-1,"",0),row
sqlite.execute("UPDATE exercise_sets SET warmup=1,note='rozgrzewka',rir=2")
row=sqlite.execute("SELECT SUM(CASE WHEN warmup=0 THEN weight*reps ELSE 0 END) FROM exercise_sets").fetchone()
assert row[0]==0,row
print("FORMA 0.7.0: migration preserves existing sets; warmups excluded from volume.")
print("Plan, calendar, RIR, import-audit controls, backup and sound wiring: OK")
