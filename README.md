# Hotspot Keeper

Turns your Wi-Fi hotspot back on automatically after airplane mode is switched off.
Uses Shizuku, so no root is needed.

## Get the APK (no Android Studio needed)
1. Create a free GitHub account and a new empty repository.
2. Upload everything in this folder (keep the `.github` folder).
3. Open the **Actions** tab, run **Build APK** (it also runs on every push).
4. When it finishes, open the run and download **HotspotKeeper-apk** (zip with `app-debug.apk`).
5. Install the APK on your phone.

## Set up on the phone
1. Start Shizuku (Wireless debugging method).
2. Open Hotspot Keeper, tap **Grant Shizuku permission**, then allow it.
3. Tap **Allow background running**.
4. Turn your hotspot off, keep the screen on, and tap **Test now**. It should turn the hotspot on.
5. Tap **Start keeper**. Now toggle airplane mode on and off and watch the hotspot come back.

## Automatic airplane toggle
Set **Every how many minutes**, **How many times** (0 = until stopped) and **Airplane mode stays on for**,
then tap **Start auto toggle**. The app switches airplane mode on, waits, switches it off, and the keeper
turns the hotspot back on. Tap **Stop auto toggle** to cancel. It does not resume after a reboot.
If the screen is off, the tile method wakes it first, but it cannot enter a PIN or pattern.
For unattended use with a lock screen, try the "Shell command only" method.

## Notes
- The default method taps the Quick Settings hotspot tile. The screen must be on and unlocked for it to work.
- If your tile has another name, change **Tile name to look for** (for example "Personal Hotspot").
- The shell command method (`cmd wifi start-softap`) starts the access point, but on some phones clients get no internet. Use the tile method then.
- After a reboot, Shizuku must be started again before the keeper can work.
