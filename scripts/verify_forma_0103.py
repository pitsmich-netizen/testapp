#!/usr/bin/env python3
"""FORMA 0.10.3: offline generated art, per-exercise mapping, and safe UI fallback."""
import base64
from pathlib import Path

root=Path(__file__).resolve().parents[1]
java=(root/"app/src/main/java/pl/pitsmich/forma/ExerciseIllustrations.java").read_text(encoding="utf-8")
ui=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf-8")
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf-8")
gradle=(root/"app/build.gradle").read_text(encoding="utf-8")
assert "versionCode 24" in gradle and "versionName '0.10.6'" in gradle
assert 'super(context, "forma_workouts.db", null, 10);' in db
for i,size in [(0,5304),(1,4510)]:
    data=(root/f"app/src/main/assets/forma_atlas_{i}.base64").read_text(encoding="ascii").strip()
    raw=base64.b64decode(data,validate=True)
    assert len(raw)==size,(i,len(raw))
    assert raw[:4]==b"RIFF" and raw[8:12]==b"WEBP"
    assert int.from_bytes(raw[4:8],"little")+8==len(raw)
assert java.count('return new int[]{')>=21
for name in ("lat_one","butterfly","leg_press"):
    assert 'case "'+name+'"' not in java, name
assert "BitmapFactory.decodeByteArray" in java and "Bitmap.createBitmap" in java
assert "ExerciseIllustrations.forId(this,exerciseId)" in ui
assert ui.count("appendExerciseIllustration(guide,ex.id);")==2
assert "guide.setVisibility(View.GONE)" in ui
assert "RestTimer.start(this,seconds)" in ui
print("FORMA 0.10.3 offline art and history safety validated")
