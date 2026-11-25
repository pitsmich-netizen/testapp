#!/usr/bin/env python3
"""Exercise guide regression: current version keeps text and provides a full-size equipment photo."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
photos=(root/"app/src/main/java/pl/pitsmich/forma/ExerciseEquipmentPhotos.java").read_text(encoding="utf8")
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
assert "ExerciseEquipmentPhotos.hasReference(exerciseId)" in ui
assert "showEquipmentPhoto(exerciseId)" in ui
assert ui.count("appendExerciseIllustration(guide,ex.id);")==2
assert "ExerciseGuides.forId(ex.id,ex.description)" in ui
assert "Powiększ zdjęcie" in ui
assert "LruCache<String,Bitmap>" in photos
assert "HttpURLConnection" in photos
assert 'super(context, "forma_workouts.db", null, 11);' in db
assert not (root/"app/src/main/java/pl/pitsmich/forma/ExerciseDiagramView.java").exists()
print("FORMA equipment-photo guide regression passed.")
