# Threat model

AlwaysBlockPhone is **intentional friction, not a lock.** It is built for one person who owns their phone and wants reflexive use to be slower than deliberate use. It is not parental control, not device management, and not resistant to its own owner.

## What it is meant to stop

- Opening a friction app out of habit, in the half second before thinking.
- Forgetting to re-block an app after using it.
- Back-to-back sessions (cooldown).
- Lowering a wait time on impulse (the policy is built into the app; changing it needs a rebuild).

## What it does not stop

Anyone who controls the device can get around it, deliberately. That is acceptable: deliberate is the goal. Known ways around it:

| Bypass | Notes |
|---|---|
| Turn off the Accessibility service | One toggle. The app shows a warning when it is off. |
| Uninstall the app, or clear its data | Clearing data also wipes active cooldowns. |
| Safe Mode | Third-party Accessibility services do not run. |
| Another profile | The blocker only acts in profiles where it is installed and enabled. |
| A browser | Web versions of blocked apps. `bin/verify` warns about installed browsers. In-app WebViews can also load web content. |
| Move the clock forward | Skips a wait. Moving it backwards is handled safely (a fresh cooldown). |
| ADB | `pm`, `am` and friends can undo anything. Keep USB debugging off. |
| Split screen, picture-in-picture, notifications | The blocker reacts to windows coming to the front; content from a friction app can still surface through its notifications or floating windows. Turn off its notifications, or keep it in a profile without notification forwarding. |
| Brief flashes | The friction app may be visible for a moment before Home is pressed. |
| A window that does not report a change | If a session ends while the notification shade or another overlay was the last reported window, the block lands on the next window change or screen-on. |

Future Device Owner or Profile Owner enforcement could make some of these harder. It is deliberately not in v1.

## Security of the app itself

- No network permission: a compromised or malicious build of this app still could not send data off the device. (Check the manifest of any build you install.)
- Exported components: the launcher activity, the Accessibility service (only the system can bind to it), and AndroidX's profile installer receiver, which requires the `DUMP` permission that only the system and ADB shell hold.
- The repository contains no secrets. Debug builds are signed with your local debug key, which is never committed.

## Out of scope

Protecting against other people with access to the phone, malware, or a compromised OS. For OS and hardware integrity, rely on GrapheneOS verified boot and the Auditor app (see [install-grapheneos.md](install-grapheneos.md)).
