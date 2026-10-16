# Historical Health Connect sync release

Version 2.0-historical, versionCode 2. The current repository application ID defaults to `com.example.healthconnectbridge` (prod). Earlier commits used `com.example.watchdogbridge`; the installed APK revision is unknown. Set `-PbridgeApplicationId=com.example.watchdogbridge` to build for that older installed package. The build produces unsigned releases for both IDs; install only the exact package already on the phone. The app's preferences filename, `device_id`, Room database name and table remain unchanged. Do not uninstall or clear app data during upgrade.

## One schedule and upload path

One periodic `HealthConnectDailyWorker` runs under the existing unique name `HealthConnectDailySync`, every six hours, covering a configurable 1–30 days including today (default seven). Custom ranges and manual requests use this same worker. A process-wide mutex serializes all reading/uploading; identical pending manual ranges are deduplicated. Unique periodic UPDATE prevents duplicate schedules.

`BridgeApplication` reconciles schedules on process start, `MY_PACKAGE_REPLACED` reconciles on upgrade, and legacy MacroDroid restart actions reconcile the same schedule. Cancellation explicitly waits for persisted `HealthConnectIntradaySync`, `intraday_sync`, and `manual_intraday_sync` requests to be canceled. The old intraday class is a migration tombstone only; it performs no Health Connect read, upload, notification or hash write. No new intraday work is scheduled. Old external MacroDroid actions are accepted for migration but cannot recreate an hourly schedule.

All uploads use `/v1/ingest/daily`. The server's temporary `/intraday` alias is only for the **old installed APK** and may be retired after migration and verification of pending old-client requests. Health record timestamps are preserved.

## Read correctness

Every provider page is retrieved until a null/blank token. Repeated tokens fail the type rather than loop. Empty successful types are serialized as empty arrays. `_sync` reports the requested scope and per-type completeness, page count, record count or bounded error category. Records are sorted by source ID before hashing. Failed paginated types are omitted (not represented as a complete empty array).

A partial snapshot may upload its successful types, but never advances the local acknowledged hash. Successful complete uploads update the existing Room row. The hash namespace changes from v3 to historical-v4, forcing one fresh synchronization after upgrade without wiping local data. Cancellation propagates correctly, and failed HTTP requests never acknowledge a snapshot. Five-minute exponential periodic backoff avoids tight read/retry loops. The server never infers deletion from absence.

## Build and install

CI runs `./gradlew --no-daemon testProdDebugUnitTest assembleProdRelease`. The uploaded release APKs are **unsigned**, contains no API key by default, and is not a ready-to-install signed update. WorkManager scheduling migration is tested with Robolectric; pagination is tested on the JVM. A real-device Health Connect/WorkManager smoke test remains required after installation.

For an actual upgrade, use the **same signing certificate as the existing installed APK**. Build/sign with the original Android Studio setup or original keystore; a newly generated debug/release key cannot replace the installed package. Do not uninstall to bypass a signature mismatch, because that changes the persisted device identity and loses local state.

1. Identify the installed package with `adb shell pm list packages` (look for `watchdogbridge` or `healthconnectbridge`), then select its matching artifact or Gradle property. Build/sign this branch using the original signing key. Existing `local.properties` `apiKey`/`baseUrl` values still work and never enter version control. The production base URL has a safe default; alternatively enter the API key into the app's masked settings field after installing.
2. Install over the existing package (`adb install -r <signed-apk>` or Android's normal upgrade flow).
3. Open the app once, review Health Connect permissions and API-key settings, and tap **Sync History Now**.
4. Check Sync Status: one historical periodic worker. Confirm `/daily` traffic and new staging on Railway, with no new `/intraday` requests after old queued attempts have drained.
5. Disable or rename external MacroDroid hourly triggers when convenient; they only reconcile the six-hour schedule now. Retain the backend alias until the migration is observed.

The current build fixes pre-existing namespace mismatches for generated R/BuildConfig and fully qualifies manifest component names. It keeps AGP 9 with its compatibility Kotlin DSL for the repository's existing Kotlin/KSP versions.

Rollback: retain the old signed APK and signing key. Android may reject a lower versionCode downgrade; produce a rollback build with a higher code using the same certificate rather than uninstalling. Backend compatibility supports both client versions and preserves all nutrition state.
