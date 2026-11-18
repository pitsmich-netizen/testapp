# FORMA — pełna kopia zapasowa i przywracanie

Od wersji **0.7.0** możesz utworzyć plik JSON w **Ustawienia → Konto i dane → Zapisz pełną kopię JSON**. Plik zawiera wszystkie sesje (także rozpoczętą), każdą zapisaną serię, ciężary, powtórzenia, RIR, oznaczenia rozgrzewki, notatki, daty, status audytu 18.11, zmodyfikowane plany i preferencje timera.

**Nie zawiera** haseł, danych logowania, tokenów Supabase ani klucza podpisywania APK.

## Przywracanie na nowym telefonie

1. Zainstaluj podpisany APK FORMA 0.7.0 lub nowszy.
2. Skopiuj plik JSON do telefonu.
3. Otwórz **Ustawienia → Konto i dane → Przywróć kopię JSON**.
4. Sprawdź liczbę sesji i serii na ekranie potwierdzenia, a następnie wybierz **Połącz dane**.
5. Zweryfikuj historię, progres i własne plany.

**Zasady przywracania:** import jest transakcyjny (niepoprawna kopia nie zapisze połowicznych danych). Istniejące sesje o tym samym `cloud_id` są pomijane, z jednym wyjątkiem: startowy import z 18.11.2026 zostaje zastąpiony jego wersją z Twojego pliku, dzięki czemu dodatkowe ręcznie uzupełnione serie nie znikną. Dni planu i preferencje zostaną przywrócone z kopii. Rozpoczęta sesja z kopii po przywróceniu będzie wstrzymana; jeśli telefon ma już aktywny trening, import pomija drugą aktywną sesję.

**Prywatność:** plik JSON nie jest szyfrowany i zawiera Twoją historię treningową. Przechowuj go w miejscu niedostępnym dla innych. Jeśli przesyłasz go przez chmurę, używaj zaufanego miejsca lub zaszyfrowanego archiwum.

## Supabase

Opcjonalna synchronizacja Supabase przechowuje ukończone treningi, serie, RIR, notatki, oznaczenia rozgrzewek oraz status audytu. Każdy wiersz jest chroniony RLS powiązanym z zalogowanym `auth.uid()`.

**Ważne:** na razie automatyczna synchronizacja Supabase nie obejmuje własnej kolejności ćwiczeń, parametrów edytowanego planu i preferencji telefonu. Do **pełnego** przeniesienia danych między urządzeniami użyj pliku JSON. To też zapewnia niezależną kopię offline.

## CSV

Eksport CSV pozostaje dostępny do otwierania historii w arkuszu kalkulacyjnym. Zawiera też RIR, notatki i informację o rozgrzewce. CSV jest przeznaczony do analizy, **nie** do odtwarzania całej bazy. Do przywracania używaj JSON.
