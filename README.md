# AlwaysBlockPhone

AlwaysBlockPhone makes distracting apps **intentionally accessible instead of instantly accessible**.

It is an open-source intentional-access layer for Android, built around the idea behind [AlwaysBlock](https://github.com/tavinathanson/alwaysblock) (a macOS website blocker): deliberate access instead of instant access.

> **Status: experimental (v0.1).** It works, it is small, and it has rough edges. Read [Limitations](#limitations) before relying on it.

## The problem

Most blockers are on/off switches. You turn one on when you feel strong, turn it off when you don't, forget to turn it back on, and repeat. Permanent blocking fails differently: some distracting apps are also genuinely useful (Instagram for work, Chrome for the one site that needs it), so you end up uninstalling the blocker.

AlwaysBlockPhone takes a middle path:

- Friction apps are **blocked by default**. You never have to remember to turn blocking on.
- You can always get in, **deliberately**: request access, wait a few minutes, then get a time-limited session.
- When the session ends the app is **blocked again automatically**, and a **cooldown** stops you from immediately starting another one.

The wait is the point. It is long enough to break the reflex and short enough that you don't tear the whole system out.

```
blocked → request access → wait (e.g. 5 min) → open (e.g. 20 min) → blocked again → cooldown (e.g. 30 min)
```

## How it works

There are two layers, driven by one policy file.

1. **Structure** (what exists where). Some apps should not be on the phone at all (`absent`, e.g. YouTube); some are fine (`normal`); some get friction. On GrapheneOS you can put friction apps in a separate user profile, end that profile's session when you are done so its apps cannot run, and keep its notifications from following you. This is set up by hand and checked by [`bin/verify`](bin/verify).
2. **Time** (when friction apps may open). The AlwaysBlockPhone app uses an Android Accessibility service to notice when a friction app comes to the front. Without an active session it sends you Home and shows its own screen, where you can request access.

```
policy/policy.conf ──┬── Android app: wait → session → re-block → cooldown
                     └── bin/verify:  is the device set up the way the policy says?
```

More in [docs/design.md](docs/design.md), including why Accessibility was chosen over usage access, overlays, device owner mode and VPN filtering.

## Platform support

| Tier | Platform | What you get |
|---|---|---|
| Reference | **GrapheneOS** (developed against a Pixel 9a) | Blocker app plus structural friction: profiles, End session, disabling apps, per-profile Google Play, no notification forwarding. |
| Supported goal | AOSP / standard Android | Blocker app with standard Android APIs. No GrapheneOS-specific structure. |
| Best effort | OEM Android | Should work. Some OEMs kill or restrict Accessibility services aggressively. |

The app uses only standard Android APIs; nothing in it is GrapheneOS-specific. Minimum Android 9 (API 28).

## Privacy

- The app has **no internet permission**. It cannot send anything anywhere.
- No telemetry, analytics, ads, accounts, servers, crash reporting or backups.
- The Accessibility service receives only "window changed" events and **cannot read screen content**. It uses the foreground app's package name, keeps the latest one in memory, and never stores or logs it.
- Stored state: three timestamps per friction app with a live session. No usage history.

Details: [docs/privacy.md](docs/privacy.md). Honest limits: [docs/threat-model.md](docs/threat-model.md).

## Building

Requirements: JDK 17 or newer and the Android SDK with platform 37 (Android Studio provides both). Point Gradle at the SDK with `ANDROID_HOME` or an untracked `local.properties` containing `sdk.dir=...`.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`, signed with your local debug key. There are no release builds or published APKs yet.

## Configuring

Edit the policy, then rebuild and reinstall. There is no in-app settings screen on purpose: lowering a wait time should take more effort than opening a menu.

```sh
cp policy/policy.conf policy/local.conf   # local.conf is gitignored and wins over policy.conf
$EDITOR policy/local.conf
```

```
# id          access      package                       wait  duration  cooldown
signal        normal      org.thoughtcrime.securesms
instagram     friction    com.instagram.android         5     20        30
chrome        friction    com.android.chrome            5     15        30
youtube       absent      com.google.android.youtube
```

Times are minutes. Access levels: `normal`, `friction`, `absent`, `undecided`. The unit tests parse your `local.conf` too, so a typo fails the build instead of silently disabling blocking.

## Installing on a phone

1. On the phone: enable Developer options and USB debugging (temporarily).
2. On your computer:

   ```sh
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

3. On the phone: open AlwaysBlockPhone, tap **Open Accessibility settings**, and turn on the AlwaysBlockPhone service. If Android says the setting is restricted, go to Settings > Apps > AlwaysBlockPhone, open the ⋮ menu, choose **Allow restricted settings**, and try again.
4. Open a friction app. You should land on the AlwaysBlockPhone screen instead.

### Setup on GrapheneOS

Installing GrapheneOS on a Pixel 9a from a Mac: [docs/install-grapheneos.md](docs/install-grapheneos.md).

A suggested starting point (keep profiles to a minimum, see [design.md](docs/design.md#profiles)):

1. **Owner profile**: everyday `normal` apps. No YouTube, ideally no Chrome.
2. **One friction profile** (Settings > System > Users): Instagram, Chrome, and sandboxed Google Play only if they need it. Leave notification forwarding off. Once it is set up, disable app installation for it from the Owner.
3. Install AlwaysBlockPhone **in every profile that holds a friction app** and turn on its Accessibility service **inside that profile**. Android only delivers Accessibility events within a profile. With the profile running:

   ```sh
   adb shell pm list users
   adb install --user <profile-id> app/build/outputs/apk/debug/app-debug.apk
   ```

   Or use GrapheneOS's "install available apps" for that profile from the Owner.
4. Use **End session** (power menu or user switcher) when you leave the friction profile, so none of its apps can run.

### Verifying the setup

With USB debugging on and the phone connected:

```sh
bin/verify --expect-device tegu
```

```
Policy (local.conf)
  ✓ signal available (user 0)
  ✓ instagram in user 10 (Friction) is guarded by the AlwaysBlockPhone accessibility service
  ✓ youtube absent from all profiles
  ⚠ user 10 (Friction) holds friction apps and is running; End session when done

Bypass paths and things this cannot check
  ⚠ browser app.vanadium.browser in user 0 (Owner) can still reach the web versions of blocked apps
  ⚠ account sign-in, 2FA and banking enrollment state cannot be verified from here
```

`bin/verify` is read-only: it never installs, removes, disables, wipes or changes anything. It handles a missing `adb`, no device, an unauthorized device, and several devices (set `ANDROID_SERIAL`). Exit code 0 means no violations, 1 means violations, 2 means it could not check.

When you are done: **revoke USB debugging authorizations and turn USB debugging off.** Nothing in normal use needs ADB.

## Limitations

- **Not bypass-proof, by design.** You can turn off the Accessibility service, uninstall the app, use Safe Mode, another profile, a browser, or ADB. See [docs/threat-model.md](docs/threat-model.md).
- **Browsers are the big gap.** v1 blocks apps, not websites. Making Chrome a friction app helps; Vanadium (GrapheneOS's browser and system WebView) stays available.
- **Custom Tabs.** If Chrome is a friction app, Chrome Custom Tabs opened by other apps (often used for sign-in) are blocked too, because they run as Chrome. Request a session first, or make a different browser the default.
- **Wall-clock based.** Setting the clock forward skips a wait.
- **Notifications** from friction apps still appear unless you turn them off or keep the app in a profile without notification forwarding.
- **Per profile.** The blocker must be installed and enabled in each profile with a friction app.
- **No daily budgets, peek or bypass modes yet.** See [design.md](docs/design.md#not-in-v1).
- **Tested** with unit tests for the policy and timing logic, a build, lint, and a simulated `adb`. Behavior on a real device is the next thing to confirm.

## Contributing

Issues and pull requests are welcome. Keep it small:

- Run `./gradlew testDebugUnitTest lintDebug assembleDebug` and `shellcheck bin/verify` before sending a change.
- Timing and policy logic belongs in `app/src/main/java/.../policy/`, as pure Kotlin with unit tests.
- No network access, analytics, accounts or new dependencies without a strong reason.
- Never commit personal data: real app lists (use `policy/local.conf`), device serials, account names, logs, screenshots or backups.

## License

[MIT](LICENSE)
