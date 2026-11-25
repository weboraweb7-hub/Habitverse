# HabitWorks

A fast, completely free, offline-first daily habit tracker built with **Kotlin**, **Jetpack Compose (Material 3)**, **Hilt**, **Room**, **Jetpack Glance**, **WorkManager**, and **Google AdMob + UMP**.

No login, no cloud lock-in, no paywalls, and unlimited habits.

---

## Features
- **3-Tab Flow (`Home | Tasks | Progress`):** Opens straight to Home (with an optional local first-name greeting dialog).
- **Momentum TimeRail & 10-Year History:** Smooth, center-snapping horizontal date strip (`today - 3650` to `today + 14`) synced with a daily `HorizontalPager`, plus a long-press / tap monthly calendar sheet and floating **Today** pill.
- **Reward Loop (XP, Levels, Flame Skins & Chests):** Earn XP for daily check-ins, tiny wins, and perfect days; evolve your Canvas Streak Flame with custom skins; earn automatic Streak Shields every 7 unbroken days (capped at 3); unlock 24 local achievement badges; and open a daily reward chest upon 100% completion.
- **Comeback & Tiny Wins:** Encouraging *"Never miss twice"* comeback banner with `+25 XP` bonus, plus long-press **"Just do the tiny version"** check-ins so low-energy days keep momentum alive.
- **Natural-Language Quick-Add & Habits Upgrade:** Type e.g. `"Drink 8 glasses of water at 9am daily"` with live parsed preview chips (`+5 XP` daily plant bonus), cue prompts, habit stacking chains (`"After <parent>"`), timed challenges (`7 / 21 / 30 / 66` days), smart reminder suggestions, swipe-to-archive with Undo, and long-press Duplicate / Reorder.
- **Habit Types, Grouping & Categories:** Yes/No check-in, Count Target (e.g., 8 glasses), and live Timer habits grouped by Morning, Afternoon, Evening, or Anytime across 7 categories.
- **Starter Packs:** 8 one-tap curated routines including Fitness, Study, Sleep, Deep Work, Hydration, Digital Detox, Student Exam Week, and Gentle Mornings.
- **Progress, Consistency Sky & Weekly Recap:** Mood & energy daily check-in with on-device correlation insights, 90-day interactive **Consistency Sky** Canvas starfield, 4-card **Weekly Recap ("Sunday Story")** with PNG share export, **Habit Strength** scores, **Badge Vault (24 badges)**, weekly bar chart, monthly heatmap, Daily Sheet, and CSV export.
- **Gentle Streak-at-Risk Nudge & Widgets:** Local WorkManager `20:30` check respecting `22:00–07:00` quiet hours with rotating warm copy, plus two Jetpack Glance home-screen widgets (Interactive Today Checklist & 2×2 Streak + Today Summary).
- **Data Safety:** 100% on-device storage (Room v3 + DataStore), Android Auto Backup, SAF JSON backup export/import (supports schema v1, v2, and v3), and one-tap **Delete all my data**.

---

## Architecture & Package Structure

```text
com.example/
├── ads/          # AdConfig (Google TEST IDs marked with TODO), UMP consent, Banner, Interstitial & RewardedAd
├── data/
│   ├── backup/   # BackupManager (SAF JSON v1/v2/v3 export/import, atomic restore, full wipe)
│   ├── local/    # Room v3 DB (MIGRATION_1_2, MIGRATION_2_3), Entities, DAOs, QuotesAssetLoader
│   ├── prefs/    # DataStore user preferences (local name, reminders, themes, flame skins, freeze tokens)
│   └── repository/
├── di/           # Hilt @Singleton module
├── domain/
│   ├── model/    # Domain models, StarterPacks, LocalInsights, RepeatDays
│   └── usecase/  # HabitCalculator, RewardEngine (XP/Chests/Strength/Badges), QuickAddParser, CsvExporter
├── ui/
│   ├── home/     # TimeRail, HorizontalPager, RewardComponents, Mood selector, count/timer controls
│   ├── tasks/    # QuickAddBar, SmartReminderCard, category filters, starter packs, habit editor, swipe/reorder
│   ├── progress/ # WeeklyRecapSection + Bitmap share, ConsistencySkyCard, HabitStrength, BadgeVault, CSV
│   ├── profile/  # Local settings, rewarded themes & freeze, vacation mode, JSON backup/restore, wipe
│   └── theme/    # 4 Material 3 themes (Forest Teal + 3 rewarded-ad unlock themes)
├── widget/       # Jetpack Glance HabitWorksWidget (checklist) & StreakTodayWidget (2x2 summary)
└── work/         # DailyReminderWorker (habit reminders + Streak-at-Risk nudge), NotificationActionReceiver
```

---

## Production Release Setup

### 1. Package Name (`applicationId`)
Update `applicationId` in `app/build.gradle.kts` from `com.REPLACE_ME.habitworks` to your unique reverse-domain package name before uploading to Google Play.

### 2. Google AdMob & UMP Consent Setup
`app/src/main/java/com/example/ads/AdManagers.kt` (`AdConfig`) and `app/src/main/AndroidManifest.xml` currently use official **Google AdMob TEST IDs**:
- Test App ID (`AndroidManifest.xml`): `ca-app-pub-3940256099942544~3347511713`
- Test Adaptive Banner ID (`BANNER_HOME_AD_UNIT_ID`, `BANNER_PROGRESS_AD_UNIT_ID`): `ca-app-pub-3940256099942544/9214589741`
- Test Interstitial ID (`INTERSTITIAL_ALL_DONE_AD_UNIT_ID`): `ca-app-pub-3940256099942544/1033173712`
- Test Rewarded ID (`REWARDED_AD_UNIT_ID`): `ca-app-pub-3940256099942544/5224354917`

Before releasing:
1. Create an app in [Google AdMob](https://admob.google.com/) and create Banner, Interstitial, and Rewarded ad units.
2. Replace `APPLICATION_ID` in `app/src/main/AndroidManifest.xml` and the constants in `AdConfig` (`TODO` markers).
3. Publish GDPR and US privacy messages in AdMob → **Privacy & messaging** for the bundled UMP SDK.

### 3. Building & Deploying to Google Play Console (`.aab`)
1. Generate an upload keystore:
   ```bash
   keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   ```
2. Export signing env vars (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`).
3. Build the Android App Bundle:
   ```bash
   gradle :app:bundleRelease
   ```
4. Upload `app/build/outputs/bundle/release/app-release.aab` in Google Play Console, host `PRIVACY_POLICY.md` at a public HTTPS URL, and declare **Ads: Yes** (no user accounts or purchases).

---

## Running Tests
```bash
gradle :app:testDebugUnitTest
```
