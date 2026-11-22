#!/usr/bin/env python3
"""FORMA 0.10.4: per-session exercise comments survive migration and backup."""
from pathlib import Path
import sqlite3
root=Path(__file__).resolve().parents[1]
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
backup=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutBackup.java").read_text(encoding="utf8")
ui=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
assert "versionCode 27" in gradle and "versionName '0.10.9'" in gradle
assert 'super(context, "forma_workouts.db", null, 10);' in db
assert 'if(oldVersion<10) createExerciseComments(db);' in db
assert "public String exerciseComment(long sessionId,String exerciseId)" in db
assert "public void saveExerciseComment(long sessionId,String exerciseId,String comment)" in db
assert 'CREATE TABLE IF NOT EXISTS exercise_comments' in db
assert 'FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE' in db
assert 'root.put("schema",3)' in backup
assert "version!=1&&version!=2&&version!=3" in backup
assert 'session.put("exercise_comments",exerciseComments)' in backup
assert 'src.optJSONArray("exercise_comments")' in backup
assert 'sql.delete("exercise_comments","session_id=?"' in backup
assert 'db.saveExerciseComment(sessionId,exerciseId,comment)' in ui
assert ui.count("changeExerciseComment(")>=4
assert 'db.exerciseComment(openSession,ex.id)' in ui
assert 'db.exerciseComment(selected.id,exerciseId)' in ui
assert 'db.exerciseComment(session.id,ex.id)' in ui
sql=sqlite3.connect(":memory:")
sql.execute("PRAGMA foreign_keys=ON")
sql.executescript("""
CREATE TABLE sessions (id INTEGER PRIMARY KEY,workout INTEGER NOT NULL);
CREATE TABLE exercise_sets (session_id INTEGER,exercise_id TEXT,set_index INTEGER,weight REAL,reps INTEGER);
INSERT INTO sessions VALUES(1,0),(2,1);
INSERT INTO exercise_sets VALUES(1,'lat_wide',0,30,12),(2,'lat_wide',0,35,10);
CREATE TABLE IF NOT EXISTS exercise_comments (
session_id INTEGER NOT NULL, exercise_id TEXT NOT NULL,
comment TEXT NOT NULL DEFAULT '', PRIMARY KEY(session_id,exercise_id),
FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE);
INSERT INTO exercise_comments VALUES(1,'lat_wide','Maszyna A');
INSERT INTO exercise_comments VALUES(2,'lat_wide','Maszyna B');
""")
assert sql.execute("SELECT COUNT(*) FROM exercise_sets").fetchone()[0]==2
assert sql.execute("SELECT comment FROM exercise_comments ORDER BY session_id").fetchall()==[("Maszyna A",),("Maszyna B",)]
sql.execute("DELETE FROM sessions WHERE id=1")
assert sql.execute("SELECT COUNT(*) FROM exercise_comments").fetchone()[0]==1
print("FORMA 0.10.4: safe v9->v10 schema, independent exercise comments, backup v1/v2/v3 and UI checks passed")
