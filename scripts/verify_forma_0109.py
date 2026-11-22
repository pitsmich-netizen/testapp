#!/usr/bin/env python3
"""Regression: legacy standalone rest countdown is retired safely in 0.10.10."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
rest=(root/"app/src/main/java/pl/pitsmich/forma/RestTimer.java").read_text(encoding="utf8")
service=(root/"app/src/main/java/pl/pitsmich/forma/WorkoutStatusService.java").read_text(encoding="utf8")
assert not (root/"app/src/main/java/pl/pitsmich/forma/RestCountdownNotification.java").exists()
assert "LEGACY_COUNTDOWN_ID = 6654" in rest
assert "notifications.cancel(LEGACY_COUNTDOWN_ID);" in rest
assert "cancelLegacyCountdown(context)" in rest
assert "setUsesChronometer(true)" in service
assert "setChronometerCountDown(true)" in service
assert "startForeground(ID,build(this))" in service
print("Retired duplicate countdown cleanup and unified timer OK.")
