#!/usr/bin/env python3
"""FORMA 0.8.4 regression checks for UI wiring and data invariants."""
from pathlib import Path
root=Path(__file__).resolve().parent.parent
main=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text()
gradle=(root/"app/build.gradle").read_text()
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text()
timer=(root/"app/src/main/java/pl/pitsmich/forma/RestTimer.java").read_text()
assert "versionCode 14" in gradle and "versionName '0.8.5'" in gradle
assert "buildFeatures { buildConfig true }" in gradle
assert "BuildConfig.VERSION_NAME" in main
assert 'text("FORMA 0.8.1"' not in main
assert 'super(context, "forma_workouts.db", null, 9);' in db
assert "RestTimer.start(this,seconds)" in main and "RestTimer.reschedule(this)" in main
assert 'private ProgressBar restProgress' in main
assert 'restProgress.setProgress' in main
assert 'putInt("rest_total",seconds)' in main
assert "getInt(\"rest_total\",90)" in main
assert 'settingsEntry("♙","Konto i synchronizacja"' in main
assert 'settingsEntry("◷","Trening i timer"' in main
assert 'settingsEntry("◐","Wygląd aplikacji"' in main
assert 'settingsEntry("▣","Kopie zapasowe"' in main
assert 'if(page.equals("settings")) {' in main
assert 'new Switch(this)' in main
assert 'getBoolean(key,defaultValue)' in main
assert "getBoolean(\"auto_next\",true)" in main
assert "getBoolean(\"timer_sound\",true)" in timer
assert 'settingsSection=state.getString("settingsSection","");' in main
assert 'settingsSection="";render();' in main
assert "scheduleWeekdays.put(scheduled.weekday,scheduled.workout)" in main
assert "showPlannedCalendarDay" in main
assert 'cell.setOnClickListener(v->openCalendarDay(day,workouts))' in main
assert "lp.width=0;lp.height=dp(42);" in main
assert "Rozpoczęcie uruchomi trening z aktualną datą" in main
assert "exportBackup()" in main and "importBackup()" in main and "runCloudSync()" in main
print("FORMA 0.8.4: timer, categorized settings, real version, calendar and migration contracts OK")
