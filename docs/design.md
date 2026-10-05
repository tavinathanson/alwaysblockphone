# Design

AlwaysBlockPhone is "AlwaysBlock for smartphones": deliberate access instead of instant access. This note explains what it borrows from [AlwaysBlock](https://github.com/tavinathanson/alwaysblock), how the policy is modeled, which Android mechanisms enforce it, and what is left for later.

GrapheneOS facts below come from its official [features](https://grapheneos.org/features) and [usage](https://grapheneos.org/usage) pages, checked 2026-10-05.

## What carries over from AlwaysBlock

AlwaysBlock is a macOS website blocker that is always on. Its behavior, not its proxy implementation, is what matters here.

| AlwaysBlock idea | On a phone | In v1? |
|---|---|---|
| **Default block**: the friction is always there, no need to turn it on | Transfers directly. A friction app is blocked unless a session is active. | Yes |
| **Timed intentional access**: request, wait, then a time-limited window | Transfers directly, and phones need it more: unlocking is a reflex. | Yes |
| **Automatic restoration to blocked** | Transfers. Status is derived from timestamps, so access ends on time even if nothing runs at that moment. | Yes |
| **Cooldown** after a session ends | Transfers. Stops back-to-back sessions. | Yes |
| **One source of truth / policy separate from enforcement** | Transfers, and matters more: a phone has several enforcement layers (OS structure, the blocker app, maybe a future DNS layer). | Yes: one policy file read by the app and by `bin/verify` |
| **Status / verification** | Transfers, split in two: the in-app status screen and the host-side `bin/verify`. | Yes |
| **Daily budget** | Transfers. | Later |
| **Quick / peek session** (short, low-wait access) | Transfers, but every extra profile is another way in. Add only when real use shows the need. | Later |
| **Emergency bypass** with its own long cooldown | Transfers. On a phone the honest bypass already exists (turn off the Accessibility service), so a built-in one is mostly UX. | Later |
| **Concurrent penalty, queueing, tags** | Weak fit. Phones rarely open several friction apps at once. | No |
| System proxy, sudo, LaunchDaemons, Chrome extension | Do not transfer. Android has different primitives. | No |
| Hostname-level website blocking | Partly transfers, through a local VPN. Real cost, see below. | No |

What does not transfer is the enforcement surface. On macOS, one proxy sees all web traffic. On Android, the useful choke points are **which app is on screen** and **what is installed in which profile**, plus OS controls that only the user can flip.

## Policy model

Each app gets an access level:

| Level | Meaning | Who enforces |
|---|---|---|
| `normal` | Easy everyday access (maps, messaging, password manager, authenticator, camera, transit, banking, utilities). | Nothing to enforce; `bin/verify` reports presence. |
| `friction` | Useful but deliberately inconvenient (for example Instagram for work, Chrome for compatibility). | The blocker app (wait, session, cooldown); optionally a separate GrapheneOS profile. |
| `absent` | Should not exist on the device (canonical example: YouTube). | You, by not installing it; `bin/verify` flags violations in every profile. |
| `undecided` | No policy yet, on purpose. | Nothing. |

The policy is a small whitespace table in [`policy/policy.conf`](../policy/policy.conf) (a generic example). Your own goes in `policy/local.conf`, which is gitignored. Both the app (as a built-in asset) and `bin/verify` read the same file. Changing it means rebuilding the app, which is itself friction: lowering a wait time on impulse takes a laptop and a cable.

YAML was considered and dropped: it would need a parser dependency in the app and in bash. The table is legible and needs ten lines of parsing.

### Friction lifecycle

```
blocked ──request──▶ waiting ──wait ends──▶ active ──duration ends / "End now"──▶ cooldown ──▶ blocked
              ▲          │
              └─cancel───┘   (cancelling a wait costs nothing: no access happened)
```

Rules, all in [`Session.kt`](../app/src/main/java/io/github/tavinathanson/alwaysblockphone/policy/Session.kt):

- A request is accepted only from **blocked**. Repeated taps never reset or shorten anything.
- State is three wall-clock timestamps per app (`requestedAt`, `startsAt`, `endsAt`). Status is computed from them and the rule's timing, so access ends on schedule after reboots, process death or a killed service.
- Missing or corrupt state decodes to **blocked**.
- If the clock moves backwards past the request time, the session is treated as ended now: a fresh cooldown, never fresh access.
- Moving the clock **forward** skips waits. This is accepted; see the [threat model](threat-model.md).

### Capabilities, not just packages

Removing an app is not removing a capability:

- Instagram app blocked, but instagram.com one tap away in a browser.
- YouTube absent, but Shorts in any browser.
- Gmail absent, but another always-notifying mail app recreates the habit.

v1 models packages only. `bin/verify` warns when a browser that is not a friction app is installed, because it can reach the web version of anything. A later policy version could name capabilities (`short-form-video`, `web-browsing`, `inbox`) and map packages and domains onto them. Not built yet: it needs a website backend to mean anything.

## Enforcement options on Android

The question: what can a normal, unprivileged app actually enforce? Least privilege that works wins.

| Mechanism | Can do | Cannot do / cost |
|---|---|---|
| **AccessibilityService** (chosen) | Gets an event with the package name whenever a window comes to the foreground. Can press Home and show its own screen; bound accessibility services are exempt from background activity start limits. Event driven, no polling. Can be configured to receive **only** window state changes and **no** window content. | A powerful permission in general, which users rightly distrust. The user can turn it off at any time. Android may label it "restricted" for sideloaded apps. Works per profile only. Cannot see inside the browser without reading screen content (which we refuse). |
| **Usage access** (`PACKAGE_USAGE_STATS`) | Lets an app query recent foreground events. | Detection only, and only by polling. Acting on it needs an overlay or a background activity start, which Android restricts. More moving parts than Accessibility, with similar user trust cost. |
| **Overlays** (`SYSTEM_ALERT_WINDOW`) | Draw over other apps. | Not detection. Usually paired with usage access. Easy to dismiss, battery cost, more permissions. |
| **Device Owner** | `setPackagesSuspended`, hide apps, lock task, manage users. Strongest standard enforcement. | Must be provisioned on a fresh device (or via ADB with no accounts). Only one per device; heavy to undo; blurs into hostile device management. Out of scope for v1. |
| **Profile Owner** (work profile) | Can suspend apps **inside its own work profile**: the app shows "paused" and cannot open. No Accessibility needed. | Needs a work profile created by the app; does not cover apps outside it; GrapheneOS's stronger isolation story is secondary users, not work profiles. **Most promising future backend.** |
| **VPN** (`VpnService`, local only) | DNS or hostname filtering per profile, the phone analogue of AlwaysBlock's proxy. Could block instagram.com in browsers. | Only one VPN per profile, so it conflicts with a real VPN. Sees hostnames, not URLs. Significant code and battery cost. Future website backend, not v1. |
| **`pm suspend` / `pm disable-user` over ADB** | Strong per-app blocking. | Needs ADB for every change, so permanent Developer Options and USB debugging. Rejected as a runtime mechanism. |
| **Digital Wellbeing app timers** | Daily limits on stock Pixel OS. | Google app, not a public API, not on GrapheneOS. |
| **GrapheneOS controls** (below) | Strong structural policy. | User-operated. A normal app cannot end a profile session, disable another app, or revoke its Network permission. |

AccessibilityService is the only standard, unprivileged, event-driven way to react to "this app just opened" without ADB or device management. The service in this project:

- subscribes to `typeWindowStateChanged` only;
- sets `canRetrieveWindowContent="false"`, so it cannot read text or views;
- reads one field, the event's package name, and keeps only the latest one in memory;
- stores, logs and sends nothing derived from Accessibility (the app has no network permission at all).

### How blocking works

1. A friction app's window comes to the foreground.
2. If there is no active session, the service presses **Home** and opens the AlwaysBlockPhone screen for that app, where you can request access.
3. During an active session the service schedules a recheck for the moment the session ends. It also rechecks when the screen turns on, because timers pause in deep sleep. If the app is still in front then, it is sent away.

## GrapheneOS as structural enforcement

GrapheneOS handles coarse structure; the blocker handles time.

| GrapheneOS feature | What it gives AlwaysBlockPhone |
|---|---|
| [Improved user profiles](https://grapheneos.org/features#improved-user-profiles) | Isolated workspaces with their own apps and data. Apps cannot see apps in other profiles. Up to 31 secondary profiles. |
| [End session](https://grapheneos.org/features#end-session) | Ending a secondary profile's session makes it inactive, so **none of its apps can run**, and puts its data back at rest. From the power menu or user switcher. Not possible for Owner without a reboot. |
| [Disabling app installation](https://grapheneos.org/features#disabling-app-installation) | After setting up a profile, the Owner can stop it installing more apps. |
| [Install available apps](https://grapheneos.org/features#install-available-apps) | The Owner can install an app already present in another profile without downloading it again. |
| [Notification forwarding](https://grapheneos.org/features#notification-forwarding) | Off by default. Leave it off in the friction profile so its apps cannot pull you in. |
| [User installed apps can be disabled](https://grapheneos.org/features#user-installed-apps-can-be-disabled) | A disabled app cannot run at all, without losing its data. Stricter than force stop. |
| [Network permission](https://grapheneos.org/features#network-permission-toggle) | Revoking it makes the network look down for that app. |
| [Sandboxed Google Play](https://grapheneos.org/usage#sandboxed-google-play) | Regular sandboxed apps, installed per profile and only usable by apps in that profile. |

None of these are callable by a normal app. They are documented setup steps that `bin/verify` checks over ADB.

### Profiles

Suggested arrangement, with as few profiles as possible:

```
Owner              normal apps, the blocker app (if any friction app lives here)
Friction           Instagram, Chrome, sandboxed Google Play if they need it,
                   the blocker app with its Accessibility service on
(optional)         a compatibility/banking profile, only if a bank app forces it
```

Tradeoffs:

- **Switching cost** is real friction: a few seconds plus the profile's lock screen. Good for friction apps, bad for anything used daily.
- **End session** after use means its apps cannot run, sync or notify. Next time, the profile must be unlocked again.
- **Notifications** from a background profile are not shown unless forwarding is enabled in that profile. Leaving it off is the point.
- **Google Play** is per profile. Put it only where an app needs it.
- **The blocker is per profile too.** Android delivers Accessibility events only within the profile the service runs in. Install AlwaysBlockPhone in every profile that holds a friction app, and turn its service on there.
- **Usability**: if the setup is so annoying that you turn it all off, it failed. Start with one friction profile, or with no extra profile and just the blocker, and add structure only when the blocker alone is not enough.

### Chrome and browsers

A browser defeats per-app policy for anything with a website. Levels, from cheapest:

1. Chrome absent from the everyday profile. GrapheneOS ships Vanadium as both the browser and the system WebView, so there is always a browser; don't disable Vanadium (apps rely on its WebView).
2. Chrome only in the friction profile.
3. Blocker gates Chrome in the foreground (v1 does this when Chrome is a friction rule). Side effect: Chrome Custom Tabs opened by other apps, often for sign-in, run as Chrome and are blocked too.
4. Domain or URL blocking. Not in v1: reading URLs from the browser UI means reading screen content through Accessibility, which is brittle across browser versions and against this project's privacy line. A local VPN with hostname filtering is the reliable standard mechanism, with the costs listed above.

### Instagram

The first friction case, and the shape v1 is built around: blocked, request, wait, session, automatic re-block, cooldown. Put it in the friction profile and leave notification forwarding off for the strongest version.

### YouTube

The canonical `absent` app. Do not install it, including as part of Google setup. `bin/verify` checks every profile and fails if it is present (even disabled).

## Banking apps

Be conservative:

- **Installed is not compatible.** A bank app may install fine and still refuse to run.
- Many need **Play services** in the same profile; GrapheneOS's [banking notes](https://grapheneos.org/usage#banking-apps) cover this.
- **Play Integrity** and app-specific checks change without notice. An app that works today may not tomorrow.
- **Authentication, MFA and device enrollment stay manual.** Nothing here touches them, and `bin/verify` cannot see them.
- Never store bank credentials or session state anywhere in this project.

## Setup lifecycle

```
install GrapheneOS (docs/install-grapheneos.md)
enable Developer options + USB debugging, temporarily
install the blocker, run bin/verify
finish manual configuration (profiles, sessions, notification forwarding)
run bin/verify again until clean
revoke USB debugging authorizations, turn USB debugging off
use the phone normally
```

Nothing in normal use needs ADB or Developer options. ADB is an occasional admin tool.

## Architecture

```
policy/policy.conf (or local.conf)      one source of truth
        │
        ├── app: Policy.kt + Session.kt  pure Kotlin, unit tested, no Android imports
        │      ├── Store.kt              loads the policy, persists three timestamps per app
        │      ├── BlockerService.kt     Accessibility backend
        │      └── MainActivity.kt       status + request UI
        │
        └── bin/verify                   GrapheneOS / Android structural checks over ADB
```

A future backend (profile-owner suspension, local VPN) would read the same policy and call the same `Sessions` logic.

## Not in v1

Daily budgets, peek and bypass profiles, capability-level rules, website blocking, Device or Profile Owner enforcement, notifications when a wait finishes, an in-app policy editor (on purpose), and release signing.
