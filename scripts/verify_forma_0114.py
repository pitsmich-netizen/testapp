#!/usr/bin/env python3
"""FORMA 0.10.14: workout resume, previous screen and modern Android Back."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
app=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
assert "versionCode 33" in gradle and "versionName '0.10.15'" in gradle
assert 'android:enableOnBackInvokedCallback="true"' in manifest
assert "registerOnBackInvokedCallback" in app and "unregisterOnBackInvokedCallback" in app
assert "private void navigateBack()" in app and "private void renderFromBack()" in app
assert "private final java.util.ArrayDeque<ScreenLocation> screenHistory" in app
assert "captureNavigation();" in app and "lastScreen=new ScreenLocation(this);" in app
assert "restoreRunningSessionToForeground()" in app
assert "protected void onStop()" in app and "if(wasStopped && restoreRunningSessionToForeground())" in app
assert 'getSharedPreferences("forma_session_ui",0)' in app
assert 'p.getLong("session_id",-1)!=active.id' in app
assert 'return exerciseInWorkout(active.workout,saved)?saved:"";' in app
assert "expandedExerciseId=savedActiveExercise(active);" in app
assert "rememberActiveExercise();" in app
assert "back.setOnClickListener(v->navigateBack());" in app
assert 'if(active.pausedAt>0)db.resume(active.id);' in app
assert 'if(active==null||active.pausedAt>0)return false;' in app
assert 'super(context, "forma_workouts.db", null, 10);' in db
assert 'if(previousEntry==null) {\n                    stopRest();' in app
print("FORMA 0.10.14: saved exercise, navigation, timer and DB unchanged")
