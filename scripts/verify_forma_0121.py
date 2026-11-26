#!/usr/bin/env python3
"""FORMA 0.10.21: compact workout interface without losing useful controls."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf-8")
db=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf-8")
gradle=(root/"app/build.gradle").read_text(encoding="utf-8")
assert "versionCode 39" in gradle and "versionName '0.10.21'" in gradle
assert 'super(context, "forma_workouts.db", null, 11);' in db
# Fixed header keeps active rest countdown and skip button; manual presets are opt-in.
assert 'timerLabel=text(restUntil==0?' in ui
assert 'skip.setOnClickListener(v->stopRest());' in ui
assert 'TextView timerOptions=actionLabel("⚙  Ręczna przerwa  ⌄",MUTED);' in ui
assert 'buttons.setVisibility(View.GONE);' in ui
assert 'timerOptions.setOnClickListener(v->{' in ui
assert 'startRest(sec);' in ui
assert 'startRest((int)left+15);' in ui
# Compact training toolbar and note preview, still preserving note editing.
assert 'TextView section=text("ĆWICZENIA",14,TEXT,true);' in ui
assert 'TextView comparison=actionLabel("⇄  Porównaj  ›",MUTED);' in ui
assert 'detailSession=openSession;page="compare";render();' in ui
assert 'TextView commentLink=actionLabel("✎  Notatka",MUTED);' in ui
assert 'preview.setMaxLines(1);' in ui
assert 'changeExerciseComment(openSession,ex.id,ex.name)' in ui
# Exercise and set explanations are hidden until requested; saving stays one tap.
assert 'TextView detailsToggle=actionLabel("ⓘ  Technika i progresja  ⌄",MUTED);' in ui
assert 'exerciseDetails.setVisibility(View.GONE);' in ui
assert 'detailsToggle.setOnClickListener(v->{' in ui
assert 'add(exerciseDetails,suggestion);' in ui
assert 'add(exerciseDetails,guide);' in ui
assert 'setDetails.setVisibility(View.GONE);' in ui
assert 'TextView explain=actionLabel("ⓘ  Szczegóły sugestii  ⌄",MUTED);' in ui
assert 'add(setDetails,guidance);' in ui
assert 'add(setDetails,text(next.explanation,11,MUTED,false));' in ui
assert 'if(current==null) {' in ui
assert 'apply.setOnClickListener(v->{' in ui
assert 'kg.setText(weight(next.weight));' in ui
assert 'reps.setText(Integer.toString(next.reps));' in ui
assert '(current.rir>=0?"  R"+current.rir:"")' in ui
# Comparison and summary remain accessible but show less at once.
assert 'TextView methodology=actionLabel("ⓘ  Jak liczymy porównanie  ⌄",MUTED);' in ui
assert 'methodologyDetails.setVisibility(View.GONE);' in ui
assert 'TextView summaryToggle=actionLabel("ⓘ  Skrót zmian w ćwiczeniach  ⌄",MUTED);' in ui
assert 'exerciseCompare.setVisibility(View.GONE);' in ui
assert 'String defaultExpanded=ongoing?expandedExerciseId:"";' in ui
assert 'rows.setVisibility(expanded?View.VISIBLE:View.GONE);' in ui
assert 'comparisonBriefSet(pair.previous)' in ui
assert 'comparisonBriefSet(pair.current)' in ui
assert 'comparisonDelta(pair)' in ui
# Regression: rest, auto-next, stable viewport and saved RIR are unchanged.
assert 'if(previousEntry==null) {\n                    stopRest();' in ui
assert 'if((moreSets || anotherExercisePending) && ex.restSeconds>0)' in ui
assert 'positionSessionScroll(expandedCard,dp(10));' in ui
assert 'renderKeepingSessionPosition();' in ui
assert 'db.save(openSession,ex.id,index,w,repsValue,rirDraft[0]' in ui
print("FORMA 0.10.21: compact workout, optional detail views, RIR and timer regression OK")
