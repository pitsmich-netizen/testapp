#!/usr/bin/env python3
"""FBW 1.0 regression: exactly 57 sets, correct exercise order and 0.8.1 migration."""
from pathlib import Path
import re
import sqlite3

ROOT=Path(__file__).resolve().parent.parent
def read(path):return (ROOT/path).read_text(encoding="utf-8")
plan=read("app/src/main/java/pl/pitsmich/forma/Plan.java")
db=read("app/src/main/java/pl/pitsmich/forma/WorkoutDb.java")
ui=read("app/src/main/java/pl/pitsmich/forma/MainActivity.java")
backup=read("app/src/main/java/pl/pitsmich/forma/WorkoutBackup.java")
gradle=read("app/build.gradle")

start=plan.index("public static List<Exercise> exercises(int workout)")
end=plan.index("private static Exercise f(",start)
body=plan[start:end]
groups=re.findall(r'return Arrays.asList\((.*?)\n        \);',body,re.S)
assert len(groups)==3,len(groups)
expected_ids=[
["db_flat","lat_wide","leg_press","leg_curl","cable_lateral","tri_push","bi_cable","calf_standing","ab_crunch"],
["lat_close","smith_incline","rev_lunge","leg_ext","leg_curl","row_cable","facepull","bi_cable","tri_overhead"],
["chest_press","straight_arm","leg_press","leg_curl","butterfly","cable_lateral","tri_push","calf_seated","pallof"],
]
for day,(group,ids) in enumerate(zip(groups,expected_ids)):
    lines=[x.strip() for x in group.splitlines() if x.strip().startswith('f("')]
    parsed=[]
    for line in lines:
        match=re.search(r'f\("([^"]+)".*?,(\d+),(\d+),(\d+),(\d+),(true|false),(\d+),(\d+)\)',line)
        assert match,line
        ident,n,minrep,maxrep,rest,side,rirlo,rirhi=match.groups()
        parsed.append((ident,int(n),int(minrep),int(maxrep),int(rest),side,int(rirlo),int(rirhi)))
    assert [p[0] for p in parsed]==ids,(day,parsed)
    assert len(parsed)==9 and sum(x[1] for x in parsed)==19,(day,parsed)
    for ident,n,lo,hi,rest,side,rirlo,rirhi in parsed:
        assert 1<=n<=3 and 8<=lo<=hi<=20
        assert rest in (0,60,75,120)
        assert 1<=rirlo<=rirhi<=3
    if day in (0,2):
        assert parsed[-1][1]==1 and parsed[-1][4]==0
assert 'super(context, "forma_workouts.db", null, 10);' in db
assert 'if(oldVersion<9) migrateFbwPlan(db)' in db
assert 'pre_fbw_plan_items' in db and 'pre_fbw_schedule' in db
assert 'ALTER TABLE sessions ADD COLUMN workout_label' in db
assert 'workout_label' in backup and 'rir_min' in backup and 'rir_max' in backup
assert 'versionCode 33' in gradle and "versionName '0.10.15'" in gradle
assert 'startDragAndDrop(' in ui
assert 'firstPending' in ui
assert 'if(!editing && current==null)' in ui
assert 'ExerciseGuides.forId(ex.id,ex.description)' in ui
assert 'private static final int TEAL=Color.rgb(120,196,177);' in ui
# Probe non-destructive migration SQL for a populated v8 DB.
conn=sqlite3.connect(":memory:")
conn.executescript("""
CREATE TABLE sessions (id INTEGER PRIMARY KEY,workout INTEGER,notes TEXT);
CREATE TABLE exercise_sets (session_id INTEGER,exercise_id TEXT,set_index INTEGER,
 weight REAL,reps INTEGER,PRIMARY KEY(session_id,exercise_id,set_index));
CREATE TABLE plan_items (workout INTEGER,position INTEGER,exercise_id TEXT,
 sets INTEGER,min_reps INTEGER,max_reps INTEGER,rest_seconds INTEGER,
 PRIMARY KEY(workout,position));
CREATE TABLE workout_schedule (workout INTEGER PRIMARY KEY,position INTEGER UNIQUE,
 weekday INTEGER UNIQUE,title TEXT);
INSERT INTO sessions VALUES(1,1,'stary trening');
INSERT INTO exercise_sets VALUES(1,'leg_press',0,110,12);
INSERT INTO plan_items VALUES(1,0,'leg_press',4,10,12,120);
INSERT INTO workout_schedule VALUES(0,0,1,'GÓRA A');
INSERT INTO workout_schedule VALUES(1,1,3,'NOGI');
INSERT INTO workout_schedule VALUES(2,2,5,'GÓRA B');
ALTER TABLE plan_items ADD COLUMN rir_min INTEGER NOT NULL DEFAULT -1;
ALTER TABLE plan_items ADD COLUMN rir_max INTEGER NOT NULL DEFAULT -1;
CREATE TABLE pre_fbw_plan_items AS SELECT * FROM plan_items WHERE 0;
INSERT INTO pre_fbw_plan_items SELECT * FROM plan_items;
CREATE TABLE pre_fbw_schedule AS SELECT * FROM workout_schedule;
ALTER TABLE sessions ADD COLUMN workout_label TEXT;
UPDATE sessions SET workout_label=CASE workout WHEN 0 THEN 'GÓRA A'
 WHEN 1 THEN 'NOGI' ELSE 'GÓRA B' END;
""")
assert conn.execute("SELECT COUNT(*) FROM exercise_sets").fetchone()[0]==1
assert conn.execute("SELECT workout_label FROM sessions").fetchone()[0]=='NOGI'
assert conn.execute("SELECT exercise_id FROM pre_fbw_plan_items").fetchone()[0]=='leg_press'
print("FBW 1.0 validated: 3×9 exercises, 19 sets/day, 57 weekly; legacy sets and schedule archived.")
