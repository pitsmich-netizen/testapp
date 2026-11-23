#!/usr/bin/env python3
"""Regression: restore ordinary live rest countdown from 0.10.9."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
rest=(root/"app/src/main/java/pl/pitsmich/forma/RestTimer.java").read_text(encoding="utf8")
regular=(root/"app/src/main/java/pl/pitsmich/forma/RestCountdownNotification.java").read_text(encoding="utf8")
service=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutStatusService.java").read_text(encoding="utf8")
assert "RestCountdownNotification.show(context,end)" in rest
assert "RestCountdownNotification.cancel(context)" in rest
assert "setUsesChronometer(true)" in regular
assert "setChronometerCountDown(true)" in regular
assert "manager.notify(ID,notification)" in regular
assert "setUsesChronometer(true)" not in service
print("FORMA normal rest timer regression passed.")
