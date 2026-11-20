#!/usr/bin/env python3
"""0.8.3 UI smoke checks (Android CI compiles actual Java)."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text()
g=(root/"app/build.gradle").read_text()
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text()
assert "versionCode 19" in g and "versionName '0.10.1'" in g
assert 'super(context, "forma_workouts.db", null, 9);' in db, "Do not change database schema for UI polish."
assert 'TextView version=text(BuildConfig.VERSION_NAME' in s
assert 'LinearLayout weightBox=row();' in s
assert 'LinearLayout repsBox=row();' in s
assert 'controls.addView(weightBox,weightLp);' in s
assert 'controls.addView(repsBox,repsLp);' in s
assert 'controls.addView(save,saveLp);' in s
assert 'controls.addView(rir,new LinearLayout.LayoutParams(dp(44),dp(42)));' in s
assert "LinearLayout actions=row();" not in s[s.index("    private void addSetRow("):s.index("    private void scrollToExpandedExercise()")]
assert 'weightBox.addView(minus,new LinearLayout.LayoutParams(dp(29),dp(42)))' in s
assert 'weightBox.addView(plus,new LinearLayout.LayoutParams(dp(29),dp(42)))' in s
assert "private void addSessionMenuItem(" in s
assert 'dialog.setCanceledOnTouchOutside(true)' in s
assert 'dialog.dismiss();cancelWorkout();' in s
assert 'new AlertDialog.Builder(this)\n            .setTitle("Anulować trening?")' in s
assert 'db.save(openSession,ex.id,index,w,repsValue,rirDraft[0]' in s
assert "advanceAfterLastSet(ex)" in s and "undoLastSet()" in s
print("FORMA 0.8.3: layout, destructive confirmation and persistence contracts OK")
