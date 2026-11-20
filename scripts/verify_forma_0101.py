#!/usr/bin/env python3
"""FORMA 0.10.1: all built-in exercises have short, expandable technique guides."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
plan=(root/"app/src/main/java/pl/pitsmich/forma/Plan.java").read_text(encoding="utf8")
guides=(root/"app/src/main/java/pl/pitsmich/forma/ExerciseGuides.java").read_text(encoding="utf8")
ui=(root/"app/src/main/java/pl/pitsmich/forma/MainActivity.java").read_text(encoding="utf8")
ids=set(re.findall(r'\b[fe]\("([a-z0-9_]+)"',plan))
covered=set(re.findall(r'case "([a-z0-9_]+)":',guides))
assert ids<=covered, "No guide for: "+repr(sorted(ids-covered))
assert len(covered)>=20
for label in ("USTAWIENIE:","RUCH:","ODDECH:","UNIKAJ:"):
    assert guides.count(label)>=len(covered), label
assert 'ExerciseGuides.forId(ex.id,ex.description)' in ui
assert 'guide.setVisibility(View.GONE)' in ui
assert 'guide.setVisibility(show?View.VISIBLE:View.GONE)' in ui
assert 'super(context, "forma_workouts.db", null, 9);' in (root/"app/src/main/java/pl/pitsmich/forma/WorkoutDb.java").read_text(encoding="utf8")
print("FORMA 0.10.1: "+str(len(covered))+" guides, catalog coverage, collapsible UI and DB compatibility OK.")
