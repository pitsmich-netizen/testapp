# Aktualizacje OTA — FORMA

## Co już działa
Klient aplikacji sprawdza aktualizacje na starcie (nie częściej niż raz na 24 h) i na żądanie. Weryfikuje `versionCode`, pobiera podpisane APK po HTTPS, sprawdza SHA-256 i certyfikat podpisu oraz uruchamia systemowy instalator po akcji użytkownika.

**Domyślny kanał OTA** jest skonfigurowany w aplikacji:
`https://github.com/pitsmich-netizen/testapp/releases/latest/download/latest.json`

Kod źródłowy pozostaje na GitHubie, a nową wersję udostępniamy jako GitHub Release z dwoma plikami: `forma-vX.Y.Z.apk` i `latest.json`. Nie ma konieczności ręcznego wpisywania adresu w aplikacji.

**Pierwsze podpisane wydanie FORMA 0.2.0 zostało opublikowane 18.11.2026:** [GitHub Release v0.2.0](https://github.com/pitsmich-netizen/testapp/releases/tag/v0.2.0). Zainstaluj `forma-v0.2.0.apk` z tego wydania, nie debug APK. Mechanizm OTA będzie zgłaszał dopiero kolejne wersje z wyższym `versionCode`; system Android zawsze wymaga zaakceptowania instalacji.

## Stan klucza podpisywania (18.11.2026)

Klucz podpisywania FORMA został już wygenerowany i bezpiecznie zapisany w **Supabase Vault** projektu `Retail QR Backend`. Nazwy dwóch sekretów:

- `forma_android_signing_keystore_b64` — zaszyfrowana kopia keystore w Base64,
- `forma_android_signing_password` — hasło keystore i klucza.

Obie wartości są dostępne tylko dla administracyjnych operacji projektu. **Nie kopiuj ich do repozytorium, issue ani rozmowy.** Po stronie GitHub skonfiguruj sekrety Actions wskazane niżej. Nie ma obecnie automatycznego transferu danych z Vault do GitHub Actions. Po konfiguracji zachowaj również własną bezpieczną kopię klucza.

## Jednorazowe przygotowanie podpisywania

1. Klucz **już istnieje w Supabase Vault**. Jeśli potrzebujesz odrębnego klucza w przyszłości, użyj jednego trwałego keystore; przykładowa komenda (NIE wykonuj jej ponownie dla obecnej aplikacji):
   ```bash
   keytool -genkeypair -v -keystore forma-release.jks -alias forma -keyalg RSA -keysize 3072 -validity 10000
   ```
   Do kolejnych aktualizacji korzystaj z **obecnego klucza**, a nie z nowego. Zrób jego bezpieczną kopię poza repozytorium.
2. W GitHub `testapp → Settings → Secrets and variables → Actions` dodaj cztery sekrety. Dane dwóch pierwszych sekretów odczytaj wyłącznie z Supabase Vault:
   - `FORMA_KEYSTORE_BASE64` — zawartość pliku `forma-release.jks` zakodowana Base64 w **jednym wierszu**
   - `FORMA_KEYSTORE_PASSWORD` — hasło magazynu kluczy
   - `FORMA_KEY_ALIAS` — `forma`
   - `FORMA_KEY_PASSWORD` — hasło klucza
   Przykład uzyskania Base64 na Linux/macOS: `base64 -w0 forma-release.jks` (na macOS: `base64 < forma-release.jks | tr -d '\\n'`).
3. Otwórz **Actions → Publish signed OTA release → Run workflow**. Workflow odczytuje `versionCode` i `versionName` z `app/build.gradle`, buduje podpisany release, generuje sumę SHA-256 i publikuje dwa pliki na GitHub Releases.
4. Zainstaluj na telefonie APK **z wydania GitHub Releases** (nie wersję debug). Kolejne wersje zwiększają `versionCode`, utrzymują package ID i są podpisywane **tym samym kluczem**.
5. Po udostępnieniu nowego wydania aplikacja sama wykryje aktualizację przy otwarciu lub po naciśnięciu **Sprawdź aktualizacje**. Instalację musi zaakceptować użytkownik w Androidzie.

## Manifest JSON
```json
{
  "versionCode": 2,
  "versionName": "0.2.0",
  "apkUrl": "https://github.com/pitsmich-netizen/testapp/releases/download/v0.2.0/forma-v0.2.0.apk",
  "sha256": "<64 znaki hex prawdziwego SHA-256 podpisanego APK>",
  "notes": "Nowości w aktualizacji"
}
```

## Zasady bezpieczeństwa i prywatności
- Nigdy nie publikuj keystore lub haseł w repozytorium (repo jest publiczne!).
- Jedynie pliki release APK oraz manifest JSON są publikowane publicznie.
- Pliki treningowe pozostają na telefonie; nie synchronizujemy ich z GitHubem.
- **Zestaw startowy treningu z 18.11.2026 jest umieszczony w kodzie źródłowym pierwszej wersji, dlatego jego ciężary są widoczne w publicznym repozytorium.** Po udostępnieniu aplikacji dalsze wpisy pozostają wyłącznie lokalnie.
- Android wymaga zezwolenia na instalację aplikacji z danego źródła. Nie instalujemy aktualizacji po cichu.
- Jeśli planujesz publikację w Google Play, zamiast tego mechanizmu OTA użyj Play In-App Updates.

## Naprawa OTA w 0.4.0

Wersje 0.2.0 i 0.3.0 miały błędną konfigurację `FileProvider`: korzeń `cache-path` wskazywał plik `forma-update.apk`, a nie katalog. Na niektórych telefonach prowadziło to do `StringIndexOutOfBoundsException` (`length=51; index=52`) przed uruchomieniem instalatora. Od wersji 0.4.0 APK jest pobierany do katalogu `cache/updates/`, który jest prawidłowo udostępniany przez `FileProvider` oraz `ACTION_INSTALL_PACKAGE`.

**Przejście 0.3.0 → 0.4.0:** jeśli OTA zgłasza poprzedni błąd, pobierz APK 0.4.0 z GitHub Releases i otwórz go ręcznie. Nie odinstalowuj 0.3.0; instalacja podpisanego APK na istniejącą aplikację zachowa jej lokalną bazę. Aktualizacje z 0.4.0 na nowsze powinny używać już poprawionego mechanizmu.
