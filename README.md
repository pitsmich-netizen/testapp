# FORMA — dziennik treningowy na Androida

Prywatna aplikacja offline do treningu Upper / Legs / Upper, z zapisem ciężarów, powtórzeń, historii i progresu.

## Funkcje
- Wbudowany plan Góra A / Nogi / Góra B (opis każdego ćwiczenia, serie, zakres powtórzeń i przerwy)
- Zapisywanie każdej wykonanej serii do lokalnej bazy SQLite
- Podpowiedzi ostatnich ciężarów i powtórzeń
- Historia treningów i śledzenie progresu
- Eksport danych treningowych do CSV
- Bez logowania, reklam, Internetu i dodatkowych uprawnień

## Kompilacja
Projekt Android (Java, minSdk 26, targetSdk 35) z AGP 8.9.2 i Gradle 8.11.1. Na GitHub Actions workflow **Android APK** buduje instalacyjne APK (`app-debug.apk`) dostępne w artefaktach przebiegu.

Lokalnie można otworzyć projekt w Android Studio i uruchomić `gradle assembleDebug` przy dostępnej wersji Gradle.

Uwaga: dane znajdują się wyłącznie na urządzeniu; przed odinstalowaniem użyj eksportu CSV.
