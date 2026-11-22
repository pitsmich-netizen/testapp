#!/usr/bin/env python3
"""FORMA 0.10.7: high-visibility Samsung notification and generic equipment photos."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
app=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
service=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutStatusService.java").read_text(encoding="utf8")
photos=(root/"app/src/main/java/pl/pitsmich/forma/ExerciseEquipmentPhotos.java").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf8")
assert "versionCode 27" in gradle and "versionName '0.10.9'" in gradle
assert "forma_workout_visible_v3" in service
assert "NotificationManager.IMPORTANCE_HIGH" in service
assert "channel.setSound(null,null)" in service
assert "channel.enableVibration(false)" in service
assert "Notification.VISIBILITY_PUBLIC" in service
assert "channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC)" in service
assert "Notification.FOREGROUND_SERVICE_IMMEDIATE" in service
assert "setChronometerCountDown(true)" not in service
assert "setContentIntent(tap)" in service
assert ".addAction(" not in service
assert "Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS" in app
assert "WorkoutStatusService.CHANNEL" in app
assert "Powiększ zdjęcie" in app
assert "showEquipmentPhoto(exerciseId)" in app
assert "ExerciseEquipmentPhotos.into(this,exerciseId,photo" in app
assert app.count("appendExerciseIllustration(guide,ex.id);")==2
assert "ExerciseGuides.forId(ex.id,ex.description)" in app
assert "https://raw.githubusercontent.com/" in photos
assert "HttpURLConnection" in photos
assert "getFilesDir()" in photos
assert "new LruCache<String,Bitmap>(24*1024)" in photos
assert 'case "leg_press": return "legs/leg-press.webp";' in photos
assert 'case "leg_curl": return "legs/leg-curl.webp";' in photos
assert 'case "leg_ext": return "legs/leg-extension.webp";' in photos
assert 'case "lat_wide": case "lat_close": return "back/lat-pulldown.webp";' in photos
assert not (root/"app/src/main/java/pl/pitsmich/forma/ExerciseDiagramView.java").exists()
assert "android.permission.INTERNET" in manifest and "FOREGROUND_SERVICE" in manifest
assert 'super(context, "forma_workouts.db", null, 10);' in (root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
print("FORMA 0.10.7 notification channel, photo mapping, caching, full-size UI, and data compatibility OK")
