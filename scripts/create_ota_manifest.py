#!/usr/bin/env python3
"""Create an HTTPS update manifest for a SIGNED release APK.
Usage:
  python3 scripts/create_ota_manifest.py app-release.apk 2 0.2.0 https://example.com/forma-v0.2.0.apk
"""
import hashlib
import json
import pathlib
import sys

if len(sys.argv) != 5:
    raise SystemExit("Usage: create_ota_manifest.py <apk> <versionCode> <versionName> <https-apk-url>")
apk_file, code, name, url = sys.argv[1:]
if not url.startswith("https://"):
    raise SystemExit("APK download URL must be HTTPS")
p = pathlib.Path(apk_file)
if not p.is_file():
    raise SystemExit(f"APK not found: {p}")
digest = hashlib.sha256(p.read_bytes()).hexdigest()
manifest = {
    "versionCode": int(code),
    "versionName": name,
    "apkUrl": url,
    "sha256": digest,
    "notes": "Aktualizacja aplikacji FORMA"
}
out = p.with_name("latest.json")
out.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(f"Created {out} with SHA-256: {digest}")
