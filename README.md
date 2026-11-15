# CleanYourTV

A clean, Apple TV-style home screen for Android TV, plus ADB scripts to remove bloatware.
Target device: AXEN 43J9000 (Android TV).

## What the launcher does
- Replaces the Home screen (HOME + LEANBACK_LAUNCHER intent filter).
- Shows only your installed apps. There are no sponsored rows and no ads.
- Big focus animations (scale + border) and a wallpaper that tints itself from the focused app's icon.
- Clock and date at the top. Back is ignored, as a launcher should.

## Build the APK
Push this repo to GitHub. The workflow in `.github/workflows/build.yml` builds a debug APK on every push.
Download it from the Actions run, under Artifacts: `cleanyourtv-debug-apk`.
(Or open the folder in Android Studio and press Run.)

## Install on the TV (PC, same WiFi)
```
adb connect <TV-IP>:5555          # Wireless debugging: adb pair <IP>:<pair-port> first
scripts/tv.sh info
scripts/tv.sh install app-debug.apk
scripts/tv.sh set-home            # or press Home and choose CleanYourTV -> Always
```

## Remove bloatware (reversible, no root)
```
scripts/tv.sh list                # creates packages_all.txt
# review the list, fill scripts/packages.txt
scripts/tv.sh remove scripts/packages.txt
scripts/tv.sh restore scripts/packages.txt   # undo
```
Only disable the stock launcher after the new one works on Home:
`scripts/tv.sh disable-stock <stock-launcher-package>`.
