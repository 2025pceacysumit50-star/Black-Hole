# ShieldCheck — Android

Malware detection (on-device, no server needed) + chat fraud detection (needs
`backend/` running). Network filter, call analysis, and everything else from
the wider plan are intentionally left out of this build.

## What's actually in this zip

Source files only — `AndroidManifest.xml`, four Kotlin files, and
`app/build.gradle.kts`. **Not included:** the Gradle wrapper and launcher
icon assets, because those are binary files Android Studio generates when it
creates a project, not something to hand-write. Claiming this zip alone
builds with one command would be overselling it.

## Getting a real, installable .apk

I can't compile one directly — the environment I build in has a JDK but no
Android SDK, no Gradle, and no network access to fetch either, so there's
no toolchain here to produce a binary. Two real ways to get one, both start
with the same one-time step:

**Step 0 (required either way):** Install Android Studio (free) and do
File → New Project → Empty Activity. This auto-generates the one thing I
genuinely cannot hand-write correctly — the Gradle wrapper (`gradlew` +
its jar) — plus default launcher icons. Takes about two minutes.

**Path A — build locally:** Copy this project's files into the one Android
Studio just generated (steps below), then Build → Build Bundle(s)/APK(s) →
Build APK(s). Best if you want to actually run and debug it.

**Path B — let GitHub build it for you:** Same copy-in step, then push the
whole thing to a new GitHub repo. The included
`.github/workflows/build-apk.yml` auto-builds a real `.apk` on every push
using GitHub's own Android toolchain — not this sandbox — and you download
it from the repo's Actions tab as a build artifact. Good if you'd rather
not have Android Studio do the heavy lifting on your laptop every time.

## Setup (5 minutes)

1. In Android Studio: **New Project → Empty Activity**, package name
   `com.yourcompany.shieldcheck` (or your own — just update the package
   declarations at the top of each `.kt` file to match).
2. Copy `AndroidManifest.xml` over the generated one.
3. Copy the four `.kt` files into `app/src/main/java/com/yourcompany/shieldcheck/`,
   preserving the `network/` and `scan/` subfolders.
4. Merge the `dependencies { }` block from `app/build.gradle.kts` into the
   one Android Studio generated (don't replace the whole file — you'll lose
   the plugin/version config Android Studio needs).
5. Update `ScamCheckApiClient`'s `baseUrl` once the backend is deployed
   somewhere other than localhost.

## Try it end to end

Run the app, hit **Scan This Phone** to see the malware heuristic run for
real against whatever's actually installed. For chat fraud detection, either
paste text directly into the app, or — with the backend running — select
text or a screenshot in any other app, hit Share, and choose ShieldCheck
from the sheet.

## What changed after real device testing

- **Malware scan false positives, fixed.** Real testing flagged Truecaller,
  Airtel, Swiggy, and slice — all for requesting SMS + overlay permissions
  together, which turned out to be extremely common among legitimate
  Indian apps (OTP autofill + any floating UI). The heuristic now
  requires accessibility-service abuse to be part of the match, which is
  the actual banking-trojan pattern and a much rarer, more specific signal.
- **Scanning is now automatic, not manual-only.** `PackageAddedReceiver`
  scans a new app the instant it's installed; `ScanWorker` runs a daily
  sweep as a safety net (catches permission changes from updates, and
  anything installed before ShieldCheck was). Flagged apps trigger a
  notification either way — new files: `Notifier.kt`,
  `PackageAddedReceiver.kt`, `ScanWorker.kt`. If you're merging into an
  existing project rather than starting fresh, these three are additions,
  not replacements; `MalwareScanner.kt`, `MainActivity.kt`,
  `AndroidManifest.xml`, and `build.gradle.kts` are updated in place.
- **New dependency:** `androidx.work:work-runtime-ktx` for the periodic
  scan — added to `build.gradle.kts`.

## Known limitations, honestly

- **Not compiler-verified.** This sandbox has no Android SDK or Gradle
  toolchain to build against, so this hasn't been through an actual compile
  — it's been checked carefully against known-correct APIs, not run.
  Expect to fix at least a small thing on first build.
- **The malware scan can still have false positives** — it's a heuristic,
  not a signature engine. Legitimate remote-support or screen-reader apps
  genuinely need accessibility service, and will still get flagged if
  they're also sideloaded or combine it with SMS/overlay. Treat findings
  as "worth a look," not verdicts.
- **The periodic scan runs every 24 hours regardless of battery state** —
  fine for testing, but worth adding battery/charging constraints
  (`setRequiresCharging`, `setRequiresBatteryNotLow` on the
  `PeriodicWorkRequestBuilder`) before this goes to real users.
- **`QUERY_ALL_PACKAGES`** needs a declaration form on Google Play before
  publishing. Security apps are an approved category for it, so this isn't
  a fight with policy — just a form to fill out.
