#!/usr/bin/env python3
"""FORMA 0.10.13: recorded previous RIR is visible, but is never auto-entered as today's RIR."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
assert "versionCode 38" in gradle and "versionName '0.10.20'" in gradle
assert "WorkoutDb.Entry previous=db.previousWorkout(openWorkout,ex.id,index);" in ui
assert '(previous.rir>=0?" · RIR "+previous.rir:"")' in ui
assert 'String rirComparison=(current.rir>=0?"RIR teraz "+current.rir:"");' in ui
assert '"poprz. RIR "+previous.rir' in ui
assert '(suggested.rir>=0?" · RIR "+suggested.rir:"")' in ui
assert '"Poprz. trening · ta seria: RIR "+previous.rir' in ui
assert 'final int[] rirDraft={current==null?-1:current.rir};' in ui
assert 'db.save(openSession,ex.id,index,w,repsValue,rirDraft[0]' in ui
assert 'if(previousEntry==null) {\n                    stopRest();' in ui
assert 'super(context, "forma_workouts.db", null, 11);' in db
print("FORMA 0.10.13: per-set previous RIR, manual RIR, timer and DB preserved")
