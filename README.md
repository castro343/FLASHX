# FLASHX — Android flashlight controller

A native Android starter app for torch ON/OFF, supported torch intensity, and timed light patterns.

## Build and install

GitHub Actions builds the debug APK on every push to `main`, or when manually started from Actions → Build FLASHX APK. Download the `FLASHX-debug-apk` artifact from a successful run, extract it, and install `app-debug.apk` on Android.

## Features

- Native Android torch control
- Torch intensity slider when Android 13+ and the phone expose multiple torch strength levels
- Flash interval control (100–2000 ms)
- Steady, strobe, SOS, double-pulse and beacon effects
- Automatically stops effects and switches the torch off when the app leaves the foreground

## Hardware and safety

Torch brightness adjustment depends on the phone's hardware and Android implementation. Some devices expose only ON/OFF. Flash timing is software-controlled and may vary slightly.

The app requests CAMERA permission because Android gates torch access through camera permission. It does not capture photos or record video.

Do not stare into the LED or shine it into anyone's eyes. Avoid prolonged high-frequency flashing: the LED can heat up, and flashing lights can trigger seizures in photosensitive people. Stop if the phone becomes hot.
