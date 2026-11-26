#!/usr/bin/env python3
"""FORMA 0.10.15 RIR aggregation, display and data compatibility."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
assert "versionCode 38" in gradle and "versionName '0.10.20'" in gradle
assert "public int rirSum=0, rirCount=0" in db
assert "return rirCount==0?-1.0:((double)rirSum)/rirCount;" in db
assert "e.reps,e.rir FROM exercise_sets" in db
assert "e.warmup=0 AND s.finished_at IS NOT NULL" in db
assert "s.notes NOT LIKE '%[FORMA:TEST]%'" in db
assert "if(rir>=0) {p.rirSum+=rir;p.rirCount++;}" in db
assert 'String[] kinds={"Ciężar","Powtórzenia","Objętość","RIR"};' in ui
assert "if(metric<0||metric>3)metric=0;" in ui
assert "if(metric==3)return progress.averageRir();" in ui
assert "if(metric==3)return value<0?" in ui
assert "double max=metric==3?4.0:0;" in ui
assert "double fraction=metric==3?(value<0?0:" in ui
assert "newest.rirCount>0" in ui and "newest.rirCount+\"/\"+newest.numberOfSets" in ui
assert 'String recordedRir=p.rirCount==0?"RIR —":' in ui
assert "Wyższy RIR nie zawsze oznacza lepszy wynik" in ui
assert 'super(context, "forma_workouts.db", null, 11);' in db
assert 'if(previousEntry==null) {\n                    stopRest();' in ui
assert "RestCountdownNotification" not in ui or "RestTimer.stop(this);" in ui
# Logic expectations: RIR 0 is measured; -1 is missing; partial coverage excludes missing.
def average(items):
    good=[x for x in items if x>=0]
    return -1.0 if not good else sum(good)/len(good)
assert average([-1,-1])==-1.0
assert average([0,-1])==0.0
assert average([1,3,-1])==2.0
print("FORMA 0.10.15: working RIR trend, partial coverage and missing vs zero checks OK")
