#!/usr/bin/env python3
from pathlib import Path
p=Path(__file__).resolve().parents[1]
app=(p/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
gradle=(p/"app/build.gradle").read_text(encoding="utf8")
assert "versionCode 25" in gradle and "versionName '0.10.7'" in gradle
assert 'private void showPlanPreview()' in app
assert 'page="planPreview"' in app
assert 'openPlanPreview(workout)' in app
assert 'start.setOnClickListener(v->startWorkout(workout))' in app
assert 'preview.setOnClickListener(v->openPlanPreview(workout))' in app
assert 'clickCard(card,()->startWorkout(workout))' not in app
assert 'ExerciseGuides.forId(ex.id,ex.description)' in app
assert 'if(!page.equals("session")' in app
print("FORMA 0.10.2 preview-only and explicit start UX validated")
