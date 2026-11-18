# FORMA — dziennik treningowy na Androida

Natywna aplikacja Android dla planu **Góra A / Nogi / Góra B** (3 treningi po około 60 minut), z naciskiem na klatkę i szerokość pleców.

## Funkcje (0.7.0)
- Edytowalny plan: zamiana i kolejność ćwiczeń, serie, zakres powtórzeń i czas przerw, bez zmian w starej historii.
- Ustawienia serii: opcjonalny RIR 0–4, notatki, serie rozgrzewkowe nie liczone do rekordów ani objętości.
- Kalendarz treningów, regularność, objętość oraz porównanie z poprzednim miesiącem.
- Pełna kopia JSON (bez haseł/tokenów), import łączący sesje, przywracanie edycji i ustawień.
- Dźwięk końca przerwy z przełącznikiem w ustawieniach.
- Możliwość zatwierdzenia lub odrzucenia ostrzeżenia o imporcie 18.11 bez usuwania serii.
- ±2,5 kg i pamięć ostatniej liczby serii oraz ciężaru w obrębie dnia treningowego.
- Timer przerw działający przy zablokowanym telefonie: systemowe alarmy, powiadomienie i wibracja (za zgodą użytkownika). Dokładność na Androidzie zależy od uprawnienia do dokładnych alarmów i oszczędzania energii.
- Opcjonalne automatyczne przejście do kolejnego ćwiczenia po wykonaniu wszystkich serii; propozycja nieznacznego zwiększenia ciężaru po uzyskaniu pełnego zakresu powtórzeń.
- Audyt pierwszego treningu z 18.11.2026: porównanie 18 pierwotnych serii z zapisanymi wpisami bez usuwania danych (np. 24 w historii).
- Kompaktowe klikalne karty planu i historii, mniejsze odstępy oraz osobne kolory dla pauzy, edycji, usuwania i zapisanych serii.
- Progres per dzień (Góra A / Nogi / Góra B) z kafelkami ćwiczeń, ostatnim treningiem, rekordami PR i trendem 6 sesji.
- Kompaktowy formularz serii i przycisk kopiowania poprzedniej zapisanej serii.
- True-black OLED, klasyczna zębatka ustawień u góry, pogrupowane ustawienia i wyszukiwarka ćwiczeń w wysuwanym panelu.
- Wstrzymywanie/wznawianie sesji, anulowanie sesji i automatyczne usuwanie pustych treningów.
- Edycja zakończonych treningów: zmiana daty, notatki, poszczególnych serii, dodawanie/usuwanie serii i kasowanie całej sesji.
- Naprawione OTA: FileProvider wskazuje katalog APK zamiast samego pliku, a instalator korzysta z systemowego ACTION_INSTALL_PACKAGE.
- Kopia treningów w Supabase obsługuje też kasowanie/edycję sesji i serii, zapobiegając ponownemu pojawianiu się usuniętych wpisów.
- Nowy przejrzysty interfejs z dolną nawigacją, dużymi polami ciężaru i powtórzeń, rozwijanymi ćwiczeniami i timerem zawsze widocznym podczas treningu.
- Kompletny plan 3 dni z opisami ćwiczeń, liczbą serii, zakresem powtórzeń i przerwami.
- Zapis ciężaru i powtórzeń **w każdej serii**; sugestie na podstawie ostatniej wykonanej sesji.
- Dodatkowe serie ponad plan, korekta zapisanej serii, usuwanie przez dłuższe przytrzymanie „Zmień”.
- Zapisywanie lokalnie w SQLite, bez konieczności logowania. Synchronizacja z chmurą jest opcjonalna.
- Historia treningów, podgląd serii i progres poszczególnych ćwiczeń.
- Timer przerw (autostart po zapisaniu serii; 60 / 90 / 120 s, +15 s, pomiń) oraz licznik czasu sesji.
- Eksport historii do CSV.
- **Import notatek:** sesja Góra A z 18.11.2026, 18 serii, z oryginalnymi ciężarami i uwagą „2 linki”.
- **OTA:** gotowe: najnowsze podpisane wydanie [FORMA 0.7.0](https://github.com/pitsmich-netizen/testapp/releases/tag/v0.7.0), automatyczne sprawdzanie nowszych wersji na GitHub Releases, weryfikacja SHA-256 i podpisu oraz instalacja za potwierdzeniem w Androidzie.

## Kopia treningów w Supabase (opcjonalna)
- Bez konta FORMA działa w pełni offline.
- Gdy potrzebujesz kopii w chmurze, w **Ustawieniach** wybierz **Zaloguj się / utwórz konto**.
- Rejestracja konta może wymagać potwierdzenia wiadomości e-mail.
- Po zalogowaniu możesz ręcznie zsynchronizować sesje; synchronizacja uruchamia się także po zakończeniu kolejnego treningu.
- Twoje dane przechowujemy w tabelach `public.forma_sessions` i `public.forma_sets`, z **RLS i powiązaniem z auth.uid()**.
- Token logowania jest zaszyfrowany lokalnie kluczem z Android Keystore; aplikacja nie posiada klucza `service_role`.
- Synchronizacja obejmuje ukończone sesje i serie (również zmiany/usunięcia, RIR, notatki, rozgrzewki i status audytu). Nie testowano jeszcze pełnego scenariusza logowania i przywracania na drugim fizycznym telefonie, dlatego zachowuj też plik JSON.\n- Własne plany i ustawienia telefonu nie są jeszcze synchronizowane automatycznie z Supabase — do pełnego przeniesienia użyj [kopii JSON](docs/BACKUP.md).
- Supabase ma w projekcie Retail QR osobne tabele — nie zmieniamy ich.

## Prywatność
- Wyniki przechowywane są na urządzeniu. Do Supabase trafiają tylko po dobrowolnym zalogowaniu i synchronizacji.
- Połączenie z Internetem służy do aktualizacji OTA oraz — wyłącznie po zalogowaniu — do kopii historii w Supabase.
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

### Naprawa OTA w 0.5.1

W 0.4.0 i 0.5.0 downloader nadal zapisywał APK jako `cache/forma-update.apk`, mimo że `FileProvider` udostępniał wyłącznie `cache/updates/`. Wersja 0.5.1 korzysta z jednej funkcji `updateFile()` dla pobrania i instalacji. Automatyczny test `scripts/verify_ota_paths.py` w CI i workflow publikacji chroni przed ponowną rozbieżnością. **Jednorazowo aktualizuj 0.4.0/0.5.0 do 0.5.1 ręcznie przez podpisany APK, bez odinstalowywania.**
