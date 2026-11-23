#!/usr/bin/env python3
"""FORMA v0.8.5 regression: OTA access and preserving historical metadata."""
from pathlib import Path
root=Path(__file__).resolve().parent.parent
main=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text()
gradle=(root/"app/build.gradle").read_text()
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text()
assert "versionCode 32" in gradle and "versionName '0.10.14'" in gradle
assert 'super(context, "forma_workouts.db", null, 10);' in db
assert 'update.setOnClickListener(v->updater.check(true));' in main
assert 'version.setOnClickListener(v->updater.configure());' in main
assert 'settingsEntry("⌁","Informacje i OTA"' not in main
assert 'settingsSection.equals("updates")' not in main
assert 'button("Sprawdź aktualizacje",true)' not in main
assert 'else db.save(session.id,ex.id,index,value,repeats,previous.rir,' in main
assert 'previous.note,previous.warmup' in main
assert "private void showCompletedActions(WorkoutDb.Session session)" in main
assert 'addSessionMenuItem(sheet,"✎","Edytuj trening"' in main
assert "dialog.dismiss();confirmDeleteCompleted(session);" in main
assert 'new LinearLayout.LayoutParams(0,dp(39),1)' in main
assert 'String.format(Locale.ROOT,"%02d",entry.setIndex+1)' in main
assert 'TextView back=actionLabel("‹  Progres",TEAL)' in main
assert "updater.check(true)" in main and "updater.configure()" in main
print("FORMA 0.8.5 OTA toolbar, history metadata and compact details: OK")
