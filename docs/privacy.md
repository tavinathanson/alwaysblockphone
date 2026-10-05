# Privacy

AlwaysBlockPhone asks for an Accessibility service, one of the most powerful permissions on Android. This page says exactly what it does with it.

## Promises

- **No network access.** The app does not request the `INTERNET` permission, so Android will not let it open a network connection.
- **No telemetry, analytics, ads, crash reporting, accounts, servers or cloud services.**
- **Local only.** Everything works offline.
- **No backups of its state.** Backup and device transfer are disabled for the app's data.

## What the Accessibility service sees

Configured in [`accessibility_service.xml`](../app/src/main/res/xml/accessibility_service.xml):

- **Event type:** only "window state changed" (a new window or app came to the front).
- **Window content:** disabled (`canRetrieveWindowContent="false"`). The service cannot read text, views, or what is on screen.
- **Used field:** the package name of the app in front, for example `com.instagram.android`.

What happens with that package name:

- It is compared against the friction rules in the built-in policy.
- The most recent one is held **in memory only**, so the blocker can recheck when a session expires.
- It is never written to storage, logged, or sent anywhere.

Android does attach some other fields to these events (such as a window title). The code does not read them.

## What is stored

Private app storage holds, per friction app with a live session, three timestamps: when access was requested, when it opens, when it ends. Sessions are deleted once their cooldown is over. There is no history of app usage, no browsing history, and nothing about other apps.

## Host tool

`bin/verify` runs on your computer, reads device state over ADB (package lists per profile, a few settings and system properties), prints results to your terminal, and stores nothing. It does not print the device serial number. Its output names your profiles and installed apps; don't paste it publicly without reviewing it.

## Verifying these claims

- `app/src/main/AndroidManifest.xml`: no permissions requested. The built APK's merged manifest adds one permission from AndroidX, a signature permission private to this app that AndroidX uses for its own internal broadcast receivers. Check with `aapt2 dump permissions` or Android Studio's APK analyzer.
- `app/src/main/res/xml/accessibility_service.xml`: the service configuration above.
- `app/src/main/java/.../BlockerService.kt`: the only code that touches Accessibility events.
