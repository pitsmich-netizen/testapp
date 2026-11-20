#!/usr/bin/env python3
"""Regression: exercise diagrams remain offline and readable at any density."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
art=(root/"app/src/main/java/pl/pitsmich/forma/ExerciseDiagramView.java").read_text(encoding="utf8")
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
assert "extends View" in art
assert "Paint.ANTI_ALIAS_FLAG" in art
assert "canvas.scale(scale,scale)" in art
assert art.count("case \"")>=9
assert "android.graphics.Bitmap" not in art
assert "ExerciseDiagramView.supports(exerciseId)" in ui
assert "new ExerciseDiagramView(this,exerciseId)" in ui
assert ui.count("appendExerciseIllustration(guide,ex.id);")==2
assert "Powiększ schemat" in ui
assert 'super(context, "forma_workouts.db", null, 10);' in db
print("FORMA vector guide regression passed.")
