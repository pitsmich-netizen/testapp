#!/usr/bin/env python3
"""FORMA 0.9.0 regression: data-safe progress charts, weekly analysis, session reflection, unified detail UI."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
app=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
theme=(root/"app/src/main/res/values/styles.xml").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
assert "versionCode 15" in gradle and "versionName '0.9.0'" in gradle
assert 'super(context, "forma_workouts.db", null, 9);' in db, "Retain database v9 and existing history."
assert "private void showWeeklyAnalysis()" in app and 'page.equals("weekly")' in app
assert 'result.sessions++' in app and 'if(entry.warmup)continue' in app
assert 'db.loadMultiplier(entry.exerciseId)' in app
assert 'private String groupForExercise(' in app
assert 'String[] order={"Klatka","Plecy","Nogi","Barki","Biceps","Triceps","Łydki","Core","Inne"}' in app
assert 'private void showSessionReflection(' in app
assert 'Samopoczucie: '+'' in app
assert 'private void saveSessionFeeling(' in app
assert 'db.updateCompleted(session.id,session.startedAt,notes)' in app
assert 'new SimpleDateFormat("dd.MM.yyyy' in app
assert 'private double exerciseVolumeAt(' in app
assert 'private double trendValue(' in app
assert 'String[] kinds={"Ciężar","Powtórzenia","Objętość"}' in app
assert 'getInt("trend_metric",0)' in app
assert 'db.progressionTip(ex)' in app
assert "unknownRir" in db and "insufficientRir" in db
assert 'label.equals(sessionWorkoutLabel(candidate))' in db
assert "common*2>=Math.max(" in db
assert 'private final java.util.HashSet<String> collapsedDetailExercises' in app
assert 'showCompletedActions(selected)' in app
assert 'private void addAppearanceChoice(' in app
assert 'putInt(key,value)' in app and 'getBoolean("compact_ui",true)' in app
assert '#78C4B1' in theme
assert 'version.setOnClickListener(v->updater.configure())' in app
assert 'update.setOnClickListener(v->updater.check(true))' in app
assert "RestTimer.start(this,seconds)" in app
assert 'exportBackup()' in app and 'importBackup()' in app
print("FORMA 0.9.0: history, charts, weekly summaries, RIR, notes, preferences and OTA preserved.")
