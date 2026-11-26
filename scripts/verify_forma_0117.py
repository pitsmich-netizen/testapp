#!/usr/bin/env python3
"""FORMA 0.10.17: stabilize viewport after save/auto-advance without changing the timer."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
timer=(root/"app/src/main/java/pl/pitsmich/forma/RestTimer.java").read_text(encoding="utf8")
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
assert "versionCode 39" in gradle and "versionName '0.10.21'" in gradle
assert "private void renderKeepingSessionPosition()" in ui
assert "int previousY=previousScroll==null?0:previousScroll.getScrollY();" in ui
assert "previousCard.getTop()-previousY" in ui
assert "previousExercise.equals(expandedExerciseId)" in ui
assert "positionSessionScroll(expandedCard,previousCardOnScreen);" in ui
assert "positionSessionScroll(null,previousY);" in ui
assert "private void positionSessionScroll(LinearLayout card,int offset)" in ui
assert "new android.view.ViewTreeObserver.OnPreDrawListener()" in ui
assert "scroll.getViewTreeObserver().removeOnPreDrawListener(this);" in ui
assert "if(contentScroll==scroll && page.equals(\"session\"))" in ui
assert "scroll.scrollTo(0,Math.max(0,target));" in ui
assert "positionSessionScroll(expandedCard,dp(10));" in ui
assert "renderKeepingSessionPosition();" in ui
assert "scroll.post(()->scroll.smoothScrollTo(0,Math.max(0,card.getTop()-dp(10))))" not in ui
assert 'if(previousEntry==null) {\n                    stopRest();' in ui
assert "boolean anotherExercisePending=!moreSets && hasAnotherPendingExercise(ex);" in ui
assert "if((moreSets || anotherExercisePending) && ex.restSeconds>0)" in ui
assert "RestCountdownNotification.show(context,end);" in timer
assert 'super(context, "forma_workouts.db", null, 11);' in db
print("FORMA 0.10.17: stable same-exercise anchor, instant next exercise and timer regression OK")
