# Test podpisywania FORMA

To sprawdzenie jest uruchamiane po skonfigurowaniu czterech sekretów GitHub Actions.

Workflow **Android APK** weryfikuje, czy można zbudować podpisane APK release, a wynik udostępnia w artefakcie `forma-release-signed-apk`.

Hasła, klucze i dane Base64 **nie są** zapisywane w repozytorium ani wyświetlane w logach.

Pomyślny etap `Build signed release APK` oraz istniejący artefakt `forma-release-signed-apk` potwierdzają, że sekrety są wystarczające do zbudowania pliku APK. Publikacja aktualizacji OTA odbywa się osobno przez workflow `Publish signed OTA release`.
