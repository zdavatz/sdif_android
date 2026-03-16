# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SDIF (Swiss Drug Interaction Finder) — Android port of the iOS app. Checks drug interactions using a local SQLite database downloaded from pillbox.oddb.org. Licensed under GPLv3.

## Build & Development

- **Build:** `./gradlew assembleDebug`
- **Run tests:** `./gradlew test`
- **Run single test:** `./gradlew test --tests "org.oddb.sdif.ClassName.testName"`
- **Lint:** `./gradlew lint`
- **Install on device:** `./gradlew installDebug`

Before first build, ensure Gradle wrapper is installed: `gradle wrapper` (requires Gradle 8.11+).

## Architecture

- **Kotlin + Jetpack Compose + Material 3** — no XML layouts
- **Package structure:**
  - `org.oddb.sdif` — MainActivity entry point
  - `org.oddb.sdif.data` — Models, DatabaseManager (singleton), InteractionChecker
  - `org.oddb.sdif.ui` — Compose screens, theme
- **Database:** SQLite via Android's built-in `SQLiteDatabase`, read-only. Auto-downloaded on first launch to `context.filesDir/interactions.db` (no bundled DB in APK)
- **Firebase Crashlytics** for crash reporting (no Analytics, to avoid AD_ID permission)
- **Threading:** Kotlin coroutines with `Dispatchers.IO` for database operations

## State Management

- Basket and interaction results are hoisted to `MainScreen` so they persist across tab switches
- Each tab screen receives state via parameters and reports changes via callbacks

## Key Screens (3 tabs)

1. **BasketCheckScreen** — Drug search + basket + interaction check (4 strategies: substance, class-level, CYP, EPha)
2. **ClinicalSearchScreen** — Full-text search with term suggestions and paginated results
3. **ATCClassScreen** — Sortable ATC drug class interaction overview table (pre-lowercased texts for performance)

## iOS Counterpart

The iOS source lives at `/home/zdavatz/software/sdif_ios`. Both apps share the same SQLite database schema and interaction detection algorithms. Keep feature parity when modifying either.

## Release & Distribution

- **Current version:** 1.0.1 (versionCode 2)
- **Signing:** `signing.properties` (gitignored) points to `privateKeySDIF.store`
- **Upload script:** `./apkup_bundle` — cleans, builds AAB, uploads to Google Play via `android-bundle-uploader`
- **Service account:** `sdif.json` (gitignored) — same service account as generika
- **Screenshots:** `screenshots/` directory contains Play Store screenshots

## Database

- Downloaded from `http://pillbox.oddb.org/interactions.db` (HTTP, not HTTPS)
- `network_security_config.xml` allows cleartext for this domain
- Tables: `drugs`, `interactions`, `epha_interactions`, `substance_brand_map`, `class_keywords`, `cyp_rules`
- All UI text is in German (de_CH)
