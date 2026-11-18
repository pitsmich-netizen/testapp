#!/usr/bin/env python3
"""Fail CI when the downloader's cache directory disagrees with FileProvider."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
java = (ROOT / "app/src/main/java/pl/pitsmich/forma/Updater.java").read_text(encoding="utf-8")
paths = ET.parse(ROOT / "app/src/main/res/xml/update_paths.xml").getroot()
manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"

cache_entries = [(x.attrib.get("name"), x.attrib.get("path"))
                 for x in paths.findall("cache-path")]
assert cache_entries == [("ota_updates", "updates/")], (
    f"FileProvider must expose ONLY cache/updates/: {cache_entries}")
assert java.count('new File(new File(activity.getCacheDir(),"updates"),"forma-update.apk")') == 1, (
    "Updater.updateFile must construct cache/updates/forma-update.apk exactly once")
assert 'new File(activity.getCacheDir(),"forma-update.apk")' not in java, (
    "An OTA path still incorrectly targets cache/forma-update.apk")
assert re.search(r'private File updateFile\(\)\s*\{\s*return new File\(', java), (
    "Missing shared OTA path helper")
assert re.search(r'File target\s*=\s*updateFile\(\)', java), (
    "OTA downloader must use updateFile()")
assert re.search(r'File apk\s*=\s*updateFile\(\)', java), (
    "After-permission installer must use updateFile()")
assert 'FileProvider.getUriForFile(' in java and 'verifyApk(target,release.versionCode)' in java, (
    "Required FileProvider + APK validation were removed")

provider = manifest.find(".//provider")
assert provider is not None, "AndroidManifest lacks FileProvider"
assert provider.attrib.get(ns + "authorities") == "${applicationId}.updates", (
    "Unexpected FileProvider authority")
meta = provider.find("meta-data")
assert meta is not None and meta.attrib.get(ns + "resource") == "@xml/update_paths", (
    "FileProvider uses a different paths XML")
print("OTA paths validated: downloader and permission resume -> cache/updates/forma-update.apk")
print("Provider configuration validated: cache-path updates/, authority and XML resource")
