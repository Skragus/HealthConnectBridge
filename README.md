# HealthConnectBridge / WatchdogBridge

**Android Health Connect → ContextKernel historical sync bridge**  
Current source version: **2.0-historical** (`versionCode 2`). See [HISTORICAL_SYNC.md](HISTORICAL_SYNC.md) for the detailed migration, signing and rollback runbook.

HealthConnectBridge is the Android app behind WatchdogBridge. It reads Health Connect records, preserves original record timestamps and source identities, and sends a field-agnostic raw snapshot to the canonical [Skragus/contextkernel](https://github.com/Skragus/contextkernel) ingestion platform. The backend owns normalization, history and nutrition/weight/steps interpretation.

## Current sync behavior

- **One historical sync worker, not daily + hourly workers.** `HealthConnectDailyWorker` runs every **six hours** under WorkManager's unique `HealthConnectDailySync` schedule. Its default lookback is **seven days including today**, configurable from 1–30 days in the app.
- **Manual sync and backfill use the same implementation.** The UI offers **Sync History Now** and a custom date-range sync. Duplicate pending manual ranges are deduplicated; the in-process mutex serializes reads and uploads.
- **The old hourly worker is retired.** Startup and app-upgrade reconciliation cancel the previous intraday work and tags. `HealthConnectIntradayWorker` remains only as a migration tombstone. Legacy MacroDroid restart actions reconcile the single schedule; they do not recreate hourly jobs.
- **All new uploads go to `POST /v1/ingest/daily`.** The ContextKernel backend retains `/v1/ingest/intraday` as a compatibility alias for old installed APKs. The current app does not upload to that alias.
- **Paginated and partial reads are explicit.** The repository reads all Health Connect pages, records per-type completeness in `_sync`, and retains empty successful record types. A failed/incomplete read does **not** advance a complete local acknowledgment hash.
- **Existing local identity is preserved across an in-place upgrade.** The app keeps the previous `device_id`, preferences and Room storage. A historical-v4 hash namespace forces a fresh upload without wiping local data.

> **Known P2:** A 30-day lookback can exceed a normal WorkManager worker's execution window before all dates are reached. Track the resumable-work fix in [CK-265](https://plan.skragus.me/skragus-systems/issue/CK-265). The default seven-day schedule does not imply this longer-range problem is fixed; prefer shorter manual ranges until the ticket is resolved.

## Architecture

```text
Android Health Connect
  → HealthConnectRepository (raw records + paginated reads)
  → HealthConnectDailyWorker (six-hour schedule / manual history)
  → DataHasher + Room DailySyncState (complete-upload acknowledgments)
  → IngestApi POST /v1/ingest/daily
  → ContextKernel API compatibility ingress
  → ContextKernel canonical ingestion + PostgreSQL
```

The app sends the existing V3 field-agnostic payload (`schema_version`, `date`, `raw_json`, and `source` including `device_id` and `collected_at`). This is **not** a second nutrition database, and the bridge must not interpret missing records as deletions.

## Build and configuration

Open the repository in Android Studio or use the Gradle wrapper. The production flavor defaults to the existing ContextKernel ingress hostname; configure a custom URL and source credential locally if needed:

```properties
# local.properties (never commit real credentials)
apiKey="YOUR_SOURCE_API_KEY"
baseUrl="https://health-ingest-api-production.up.railway.app/"
```

The app also has a masked API-key setting. CI builds with no embedded production API key; its release APKs are **unsigned**. Production and staging are distinct Gradle flavors.

```sh
./gradlew --no-daemon testProdDebugUnitTest assembleProdRelease
```

Automated checks cover pagination and the WorkManager schedule migration. See [the Android CI workflow](.github/workflows/android.yml).

### Updating an existing installation safely

**Match the installed package ID and original signing certificate.** The current default production application ID is `com.example.healthconnectbridge`. Older WatchdogBridge installations may use `com.example.watchdogbridge`; use `-PbridgeApplicationId=com.example.watchdogbridge` for a compatible build, if that's the package actually installed.

1. Identify the installed package (`adb shell pm list packages`) and build the corresponding flavor/application ID.
2. Sign with the **same signing key** as the installed APK. CI's unsigned artifact is not a ready-to-install update.
3. Upgrade in place (`adb install -r <signed-apk>`), **without uninstalling or clearing app data**.
4. Open the app, check Health Connect permissions and API key, and run **Sync History Now**.
5. Confirm the six-hour periodic worker and successful `/daily` ingestion; retain the server's compatibility alias until older queued requests are accounted for.

The [ContextKernel documentation](https://github.com/Skragus/contextkernel/blob/main/docs/chatgpt-tools.md) records an in-place v2.0-historical install and successful sync on **2026-10-17**. That is historical deployment evidence, not proof of which APK/commit is currently installed on every device. Updating GitHub source does **not** update the phone automatically.

## Development and limitations

The current UI provides permissions, lookback and API-key settings, sync-now, basic WorkManager status, and date-range backfill. Do not rely on WorkManager's `SUCCEEDED` alone as independent proof that every Health Connect record was available. See [HISTORICAL_SYNC.md](HISTORICAL_SYNC.md) for detailed partial-read, retry and rollback behavior.

The standalone legacy `health-ingest-api` repository is retired; backend fixes belong in [`Skragus/contextkernel`](https://github.com/Skragus/contextkernel), and Android app fixes belong here.

## License

MIT
