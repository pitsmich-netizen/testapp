#!/usr/bin/env python3
"""FORMA 0.10.12: confirmed series end previous rest and smart next-series defaults."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text()
timer=(root/"app/src/main/java/pl/pitsmich/forma/RestTimer.java").read_text()
gradle=(root/"app/build.gradle").read_text()
assert "versionCode 30" in gradle and "versionName '0.10.12'" in gradle
# No timer cancellations while typing: only after successful save/validation.
validate=ui.index("double w=Double.parseDouble(kg.getText()")
save=ui.index("db.save(openSession,ex.id,index,w,repsValue")
cancel=ui.index("stopRest();",save)
feedback=ui.index("save.performHapticFeedback(",save)
assert validate<save<cancel<feedback
# New rest is started only if additional planned working sets remain.
assert "if(previousEntry==null && ex.restSeconds>0 &&" in ui
assert "db.countExerciseSets(openSession,ex.id)<db.expectedSets(openWorkout,ex))" in ui
assert "startRest(ex.restSeconds);" in ui
assert 'lastInSession=index>0?db.get(openSession,ex.id,index-1):null' in ui
assert "lastInSession!=null&&!lastInSession.warmup?lastInSession:previous" in ui
assert "draftSource!=null?weight(draftSource.weight)" in ui
assert "draftSource!=null?Integer.toString(draftSource.reps)" in ui
assert '"Ostatnia seria: "' in ui and '"Ostatnio: "' in ui
assert "timerShell.setPadding(dp(12),dp(7),dp(12),dp(8));" in ui
assert "dp(33),dp(42)" in ui and "dp(31),dp(42)" in ui
# Stable timer notification from the accepted 0.10.11 release.
assert "RestCountdownNotification.show(context,end);" in timer
assert "RestCountdownNotification.cancel(context);" in timer
assert 'super(context, "forma_workouts.db", null, 10);' in (root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text()
print("FORMA 0.10.12 confirmed-save timer, input defaults and stable notifications OK.")
