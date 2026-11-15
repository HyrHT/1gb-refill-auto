# Refill Auto

A tiny Android app that texts **"Refill" to 80808** (Lidl Connect) for you.

- **When the carrier texts you** – Lidl Connect's "Deine Unlimited-1GB Option ist
  fast verbraucht … antworte einfach auf diese SMS mit Refill" message is
  recognised when it comes from `80808` and contains `mit Refill` or
  `fast verbraucht` (both changeable), and answered with "Refill" sent to
  `80808`. It must be the short form: the network rejects `+4980808`
  (error 112). Confirmations don't match, so they can't trigger another reply. A
  30-second cooldown stops it from ever replying in a loop.
- **On a timer** – flip "Send repeatedly" and it texts every minute (or every N
  minutes) until you switch it off. A notification stays up while the timer runs,
  and it restarts after a reboot.
- **Dual SIM:** replies go out on the SIM the carrier SMS arrived on, and the
  timer uses that SIM too.
- **Send now** button and an activity log showing every SMS and whether it
  actually went out.

## Install

1. Get the APK: download `RefillAuto-apk` from the latest run on this repo's
   **Actions** tab (unzip it), or build it yourself with `./gradlew assembleRelease`.
2. Copy `app-release.apk` to the phone and open it. Allow "install unknown apps"
   when asked.
3. Open **Refill Auto** and allow SMS and notification permissions.
   - Android 13+: if SMS permission is greyed out or blocked, go to
     *Settings → Apps → Refill Auto → ⋮ (top right) → Allow restricted settings*,
     then open the app again.
4. The first time it sends, Android asks "Refill Auto would like to send a
   message to 80808 — may cause charges". Tick **Remember my choice** and tap
   **Send**. (Or: *Settings → Apps → Special app access → Premium SMS access →
   Refill Auto → Always allow*.) Without this, every send waits for that prompt.
5. Tap **Let the timer run in the background** and choose *Allow*, otherwise the
   timer can pause while the screen is off. On Samsung/Xiaomi/Huawei also set the
   app's battery usage to *Unrestricted*.

## Notes

- Each SMS costs whatever your plan charges for an SMS to that number. The
  every-minute timer sends 1,440 texts a day.
- The app is sideload-only: Google Play doesn't allow this kind of SMS app.
- APKs from different builds (Actions vs. your own computer) are signed with
  different debug keys; uninstall the old one before installing the other.
