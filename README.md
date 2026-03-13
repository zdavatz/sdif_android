# SDIF Android

**Swiss Drug Interaction Finder** — Android app for checking drug interactions using Swiss pharmaceutical data.

## Features

- **Interaktions-Check**: Search drugs by brand name or substance, add to basket, and check interactions between them using 4 detection strategies (substance match, ATC class-level, CYP enzyme, EPha curated)
- **Klinische Suche**: Full-text clinical search with term suggestions and paginated results
- **ATC-Klassen**: Sortable overview table of ATC drug class interactions

## Tech Stack

- Kotlin + Jetpack Compose + Material 3
- SQLite database (downloaded from pillbox.oddb.org)
- No external dependencies beyond AndroidX/Compose
- Min SDK 26 (Android 8.0), Target SDK 35

## Build

```bash
./gradlew assembleDebug
./gradlew installDebug
```

## Database

The app uses a SQLite database downloaded from `http://pillbox.oddb.org/interactions.db`. Optionally bundle `interactions.db` in `app/src/main/assets/` as a fallback. The database can be updated from within the app via Settings.

## License

GPLv3 — see [LICENSE](LICENSE)
