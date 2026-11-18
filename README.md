# FORMA — dziennik treningowy na Androida

Natywna aplikacja Android dla planu **Góra A / Nogi / Góra B** (3 treningi po około 60 minut), z naciskiem na klatkę i szerokość pleców.

## Funkcje (0.1.0)
- Kompletny plan 3 dni z opisami ćwiczeń, liczbą serii, zakresem powtórzeń i przerwami.
- Zapis ciężaru i powtórzeń **w każdej serii**; sugestie na podstawie ostatniej wykonanej sesji.
- Dodatkowe serie ponad plan, korekta zapisanej serii, usuwanie przez dłuższe przytrzymanie „Zmień”.
- Zapisywanie lokalnie w SQLite, bez konta i bez zdalnego przechowywania danych treningowych.
- Historia treningów, podgląd serii i progres poszczególnych ćwiczeń.
- Timer przerw (autostart po zapisaniu serii; 60 / 90 / 120 s, +15 s, pomiń) oraz licznik czasu sesji.
- Eksport historii do CSV.
- **Import notatek:** sesja Góra A z 18.11.2026, 12 serii, z oryginalnymi ciężarami i uwagą „2 linki”.
- **OTA:** sprawdzanie nowych wersji z publicznych GitHub Releases. Wymagane jednokrotne bezpieczne skonfigurowanie podpisywania APK i opublikowanie pierwszego podpisanego wydania.

## Prywatność
- Wyniki przechowywane są na urządzeniu. Nie przesyłamy ich do żadnego serwera.
- Aplikacja ma dostęp do Internetu **wyłącznie w celu sprawdzania i pobierania aktualizacji OTA**.
- Przed odinstalowaniem eksportuj dane do CSV (backup Androida celowo wyłączony).
- Nie loguje kont, nie wyświetla reklam.

## Budowanie APK
Projekt Android (Java, minSdk 26, targetSdk 35; AGP 8.9.2; Gradle 8.11.1).
- Workflow **Android APK** kompiluje testowy plik `app-debug.apk` (GitHub Actions → artefakt `forma-debug-apk`).
- Do produkcyjnego OTA używaj **podpisanego pliku release**, nie debug APK.
- Po skonfigurowaniu czterech sekretów podpisywania workflow **Publish signed OTA release** tworzy GitHub Release z APK i `latest.json`.

Instrukcja aktywacji OTA: [docs/OTA.md](docs/OTA.md).

## Zdrowie
Plan nie stanowi diagnozy ani terapii. Przy nawracającym bólu lędźwi dobieraj bezbolesne warianty i rozważ konsultację z fizjoterapeutą.
