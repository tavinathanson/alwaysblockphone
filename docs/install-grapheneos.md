# Installing GrapheneOS on a Pixel 9a from macOS

A condensed walkthrough of the official [GrapheneOS CLI install guide](https://grapheneos.org/install/cli) for one specific case: a **Pixel 9a** flashed from a **Mac**. It adds a backup checklist, labels, and checkpoints. It does not add, remove or reorder any install step.

> **The official guide wins.** If anything here disagrees with [grapheneos.org/install/cli](https://grapheneos.org/install/cli), follow the official guide. GrapheneOS asks people to follow its instructions "to the letter without skipping, reordering or adding any steps". Re-read it on the day you install; versions and hashes below were checked on **2026-10-05** and will go stale.
>
> GrapheneOS also has an official [web installer](https://grapheneos.org/install/web), which is easier. This page covers the CLI route.

Labels used below:

- 📱 **Phone**: something you do on the Pixel.
- 💻 **Mac**: a command you run in Terminal.
- ⚠️ **WIPES DATA**: the step erases everything on the phone.
- ✅ **Checkpoint**: confirm this before moving on.

## Facts verified for this device (2026-10-05)

| Item | Value | Source |
|---|---|---|
| Device codename | `tegu` (Pixel 9a) | [releases page](https://grapheneos.org/releases) |
| Supported macOS | Sonoma (14), Sequoia (15), Tahoe (26) | [CLI guide, prerequisites](https://grapheneos.org/install/cli#prerequisites) |
| fastboot minimum | 35.0.1 | [CLI guide](https://grapheneos.org/install/cli#obtaining-fastboot) |
| platform-tools download | `platform-tools_r35.0.2-darwin.zip` | CLI guide |
| platform-tools SHA-256 | `1820078db90bf21628d257ff052528af1c61bb48f754b3555648f5652fa35d78` | CLI guide |
| Current stable release | `2026100200` (from `https://releases.grapheneos.org/tegu-stable`) | release channel file |
| Factory image file | `tegu-install-2026100200.zip` (+ `.zip.sig`) | releases server |
| Factory images signer | `contact@grapheneos.org ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIIUg/m5CoP83b0rfSCzYSVA4cw4ir49io5GPoxbgxdJE` | CLI guide |
| Pixel 9a verified boot key hash | `0508de44ee00bfb49ece32c418af1896391abde0f05b64f41bc9a2dfb589445b` | CLI guide |

To see the current release on install day:

```sh
curl -s https://releases.grapheneos.org/tegu-stable
# prints: <VERSION> <timestamp> tegu stable
```

Use that `VERSION` wherever this page says `2026100200`.

## Before you wipe: backup checklist

Unlocking, flashing and locking each erase the phone. Do this first. Keep the results somewhere private, never in this repository.

- [ ] **Google / Pixel backup** is current (Settings > System > Backup), if you rely on it.
- [ ] **Photos and files** are copied off the phone or confirmed in your cloud storage.
- [ ] **Authenticator / 2FA**: every account using an authenticator app on this phone has a working alternative: exported/transferred codes, recovery codes stored safely, or a second factor you can still use. Test at least one before wiping.
- [ ] **Messaging**: backups made where the app needs one (for example Signal's own backup or transfer, WhatsApp backup). Know which apps can and cannot restore history.
- [ ] **Banking and device enrollment**: know which banking or work apps bind to the device, need re-enrollment, or may not run on GrapheneOS. Have a way to log in and re-verify without this phone (see [design.md](design.md#banking-apps)).
- [ ] **eSIM**: if you use one, check with your carrier how to move or re-download it. A wipe can remove it.
- [ ] **Passwords**: your password manager is synced and you can log in to it from another device.
- [ ] The phone is **not a carrier-locked variant** (see Prerequisites below).

✅ Checkpoint: you could lose this phone right now without losing anything that matters.

## 0. Prerequisites

From the official guide:

- A computer with at least **2 GB free memory** and **32 GB free storage**.
- A good **USB-C cable**, ideally the one packaged with the device. Connect directly to the Mac, **no USB hub**.
- Install from macOS on bare metal, **not a virtual machine**.
- macOS is **up to date** and is Sonoma 14, Sequoia 15 or Tahoe 26.
- Avoid **carrier variants**; their bootloader unlocking may be disabled.
- Best practice: **update the stock OS** on the phone first so it has current firmware.

## 1. Enabling OEM unlocking

📱 **Phone**

1. Settings > About phone > tap **Build number** repeatedly until developer mode is enabled.
2. Settings > System > Developer options > turn on **OEM unlocking**. This needs internet access on models that can be sold carrier locked.

✅ Checkpoint: OEM unlocking is on. If the toggle is greyed out, stop: the device may be carrier locked.

## 2. Opening terminal

💻 **Mac**: open Terminal. **Use this same window for the whole install.** Closing it loses the `PATH` setup from step 3.

Work in an empty folder (anywhere outside this repository), for example:

```sh
mkdir -p ~/grapheneos-install && cd ~/grapheneos-install
```

## 3. Obtaining fastboot (standalone platform-tools)

💻 **Mac**: download, verify and extract the official standalone platform-tools:

```sh
curl -O https://dl.google.com/android/repository/platform-tools_r35.0.2-darwin.zip
echo 'SHA256 (platform-tools_r35.0.2-darwin.zip) = 1820078db90bf21628d257ff052528af1c61bb48f754b3555648f5652fa35d78' | shasum -c
tar xvf platform-tools_r35.0.2-darwin.zip
```

✅ Checkpoint: `shasum` prints `platform-tools_r35.0.2-darwin.zip: OK`. If not, stop.

Add them to `PATH` for this shell:

```sh
export PATH="$PWD/platform-tools:$PATH"
```

## 4. Checking fastboot version

💻 **Mac**

```sh
fastboot --version
```

✅ Checkpoint: version is at least **35.0.1** and "Installed as" points into your `platform-tools` folder (not Homebrew or another copy). Expected for these instructions: `fastboot version 35.0.2-12147458`.

The official guide's "Flashing as non-root" and "fwupd" sections are for Linux only. Nothing to do on macOS.

## 5. Booting into the bootloader interface

📱 **Phone**: reboot (or power off and power on) while **holding volume down** until the bootloader appears.

✅ Checkpoint: you see a **red warning triangle** and **"Fastboot Mode"**. Do **not** press power to select "Start"; the phone must stay in Fastboot Mode.

## 6. Connecting the device

📱 **Phone** to 💻 **Mac**: connect the cable directly. No driver is needed on macOS.

## 7. Unlocking the bootloader ⚠️ WIPES DATA

💻 **Mac**

```sh
fastboot flashing unlock
```

📱 **Phone**: use a volume button to select accepting it, then the power button to confirm. **This wipes all data.**

Run this yourself. Nothing in this repository automates it.

## 8. Obtaining OpenSSH

macOS includes OpenSSH. Nothing to do.

## 9. Obtaining factory images

💻 **Mac**: download the signing key:

```sh
curl -O https://releases.grapheneos.org/allowed_signers
cat allowed_signers
```

✅ Checkpoint: the content is exactly:

```
contact@grapheneos.org ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIIUg/m5CoP83b0rfSCzYSVA4cw4ir49io5GPoxbgxdJE
```

The guide lists other places to cross-check the key (GrapheneOS on Bluesky, X and GitHub).

Download the Pixel 9a factory images (replace the version if `tegu-stable` shows a newer one):

```sh
curl -O https://releases.grapheneos.org/tegu-install-2026100200.zip
curl -O https://releases.grapheneos.org/tegu-install-2026100200.zip.sig
```

Verify the signature:

```sh
ssh-keygen -Y verify -f allowed_signers -I contact@grapheneos.org -n "factory images" -s tegu-install-2026100200.zip.sig < tegu-install-2026100200.zip
```

✅ Checkpoint: output is exactly:

```
Good "factory images" signature for contact@grapheneos.org with ED25519 key SHA256:AhgHif0mei+9aNyKLfMZBh2yptHdw/aN7Tlh/j2eFwM
```

Anything else: stop and do not flash.

## 10. Flashing factory images ⚠️ WIPES DATA

💻 **Mac**

```sh
tar xvf tegu-install-2026100200.zip
cd tegu-install-2026100200
bash flash-all.sh
```

📱 **Phone**: don't touch it until the script finishes. It flashes firmware, reboots into the bootloader and flashes the OS on its own.

✅ Checkpoint: the script finished without errors and the phone is back in Fastboot Mode. If it failed, keep the full terminal output; the official guide asks for it when you request help on the GrapheneOS chat.

Run the script yourself. Nothing in this repository automates flashing.

## 11. Locking the bootloader ⚠️ WIPES DATA

Do this **before using the phone**, because locking wipes again. Locking enables full verified boot.

💻 **Mac**

```sh
fastboot flashing lock
```

📱 **Phone**: select accepting it with a volume button, confirm with power.

✅ Checkpoint: the bootloader screen reports the device as locked.

## 12. Post-installation

📱 **Phone**

1. **Booting**: press power with **Start** selected.
2. **Disabling OEM unlocking**: the last setup screen has an OEM unlocking toggle, checked by default, that disables OEM unlocking. Leave it checked (recommended).
3. **Verifying installation**: on boot the phone shows a yellow notice with the verified boot key hash. For the Pixel 9a it must be:

   ```
   0508de44ee00bfb49ece32c418af1896391abde0f05b64f41bc9a2dfb589445b
   ```

   For stronger verification, use the [Auditor app](https://attestation.app/tutorial) with a second Android device.

✅ Checkpoint: GrapheneOS boots, the hash matches, OEM unlocking is off.

After this, clean up the Mac side: `~/grapheneos-install` contains nothing secret but you no longer need it.

## Returning to the stock OS

From the [official guide](https://grapheneos.org/install/cli#replacing-grapheneos-with-the-stock-os): installing the stock OS from the stock factory images is the same process as above. One extra step is needed first, because GrapheneOS flashed a non-stock verified boot key:

1. 📱 Enable OEM unlocking (step 1), boot into Fastboot Mode (step 5), connect (step 6).
2. 💻 Unlock the bootloader if it is locked (step 7). ⚠️ **WIPES DATA**
3. 💻 With the bootloader unlocked, erase the custom verified boot key:

   ```sh
   fastboot erase avb_custom_key
   ```

4. 💻 Flash Google's stock factory images for `tegu` (from Google's factory images page), then lock (step 11). ⚠️ **WIPES DATA**

## Next

Set up profiles and AlwaysBlockPhone: see the [README](../README.md#setup-on-grapheneos) and [design.md](design.md).
