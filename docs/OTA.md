# Aktualizacje OTA — FORMA

## Stan
Klient OTA znajduje się w aplikacji od pierwszej wersji: sprawdza manifest HTTPS (ręcznie lub nie częściej niż raz na 24 h), porównuje `versionCode`, wyświetla informacje, pobiera APK, sprawdza SHA-256 oraz certyfikat podpisu i otwiera systemowy instalator Androida po akcji użytkownika.

**Publikowanie OTA nie jest jeszcze aktywne.** Repozytorium źródłowe `pitsmich-netizen/testapp` jest prywatne. Nie umieszczaj tokenu GitHub ani klucza podpisywania w APK.

## Wymagania do aktywacji OTA
1. Trwały klucz podpisywania APK (keystore). Wszystkie kolejne aktualizacje muszą mieć ten sam identyfikator aplikacji i certyfikat podpisu. Zachowaj kopię klucza w bezpiecznym miejscu.
2. Cztery sekrety GitHub Actions w **Settings → Secrets and variables → Actions**:
   `FORMA_KEYSTORE_BASE64` (keystore w base64), `FORMA_KEYSTORE_PASSWORD`, `FORMA_KEY_ALIAS`, `FORMA_KEY_PASSWORD`. Repozytorium buduje wtedy instalacyjny `forma-release-signed-apk`.
3. Publiczny adres HTTPS do plików APK i `latest.json`. Hosting może przechowywać wyłącznie pliki instalacyjne oraz JSON — kod źródłowy pozostaje prywatny.
4. W aplikacji wybierz **Ustawienia kanału OTA** i podaj URL do pliku `latest.json`. Następnie **Sprawdź aktualizacje**.

## Kontrakt manifestu
```json
{
  "versionCode": 2,
  "versionName": "0.2.0",
  "apkUrl": "https://your-updates-host.example/forma-v0.2.0.apk",
  "sha256": "<64 znaki hex SHA-256 podpisanego APK>",
  "notes": "Co nowego w tej wersji."
}
```

Po zbudowaniu podpisanego APK i umieszczeniu go w publicznym hostingu uruchom:
```sh
python3 scripts/create_ota_manifest.py app-release.apk 2 0.2.0 https://twoj-serwer.example/forma-v0.2.0.apk
```
Wgraj wygenerowany `latest.json` pod stały publiczny adres. Aby opublikować kolejną wersję, zwiększ `versionCode` w `app/build.gradle`, zbuduj podpisaną wersję tym samym keystore, wgraj APK i podmień manifest.

## Bezpieczeństwo
- Brak instalacji po cichu: Android wymaga zatwierdzenia.
- Weryfikacja integralności (SHA-256), nazwy pakietu, wersji i zgodności certyfikatu podpisu.
- Tylko HTTPS, limit rozmiaru pobierania 150 MB, bez przechowywania tokenów w aplikacji.
- Aplikacja może potrzebować jednorazowego zezwolenia Androida: **Zezwalaj z tego źródła**.
- Wersji debug APK z GitHub Actions nie używaj jako podstawy produkcyjnego OTA: jej podpis może się różnić między przebiegami. Instalację pierwszą i kolejne wykonuj z **podpisanego APK release**.
- Aktualizacje APK z tego mechanizmu są do dystrybucji prywatnej (poza Google Play). Jeżeli aplikacja trafi do Google Play, należy przejść na aktualizacje Play In-App Updates.
