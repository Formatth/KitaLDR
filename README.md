# KitaLDR ❤️

> A tiny private space for two people who are far apart.

KitaLDR is a solo-dev Android project made as a personal LDR companion. The first milestone is deliberately small: connect two devices safely, then let one partner send lightweight remote actions such as **Poke**, **Miss You**, and **Wake Up**.

## Current status

- [x] Android project scaffold
- [x] Jetpack Compose UI
- [x] Pairing UI prototype
- [x] Disconnect/reset flow in prototype
- [x] GitHub Actions debug APK build
- [x] FCM token registration integration
- [ ] Firebase Authentication
- [ ] Secure one-couple-per-device pairing
- [ ] Firebase Cloud Messaging (FCM) remote push delivery
- [ ] Real remote vibration
- [ ] Wake-up notification/sound
- [ ] Local reminders
- [ ] Anniversary polish

## Pairing design

The final pairing system will use a short-lived pairing code. A code will not permanently bind a device. After a couple disconnects, both devices can pair again using a fresh code.

The backend will enforce:

1. One active partner per user/device.
2. Pairing codes expire and are single-use.
3. Remote actions are accepted only from the two members of the active pair.
4. Disconnect invalidates the active relationship and its remote-command path.
5. FCM device tokens are never used as public identifiers.

This is important because KitaLDR is intended to be safe even if a few other people install the app.

## Tech direction

- Kotlin
- Jetpack Compose
- Firebase Authentication
- Cloud Firestore
- Firebase Cloud Messaging
- Android notification/vibration APIs
- GitHub Actions for builds
- Vercel only if a static web page is useful later

No VPS is planned for the MVP.

## Development

The repository currently builds a debug APK through GitHub Actions. Firebase is intentionally not wired into the first scaffold yet, so no `google-services.json` or private Firebase credentials belong in Git.

### Build locally

Open the project in Android Studio and let Gradle sync. The project uses JDK 17 and Gradle 8.7.

### Build with GitHub Actions

Every push to `main`, pull request, or manual workflow run builds `assembleDebug` and uploads the APK as the `KitaLDR-debug` artifact.

## Privacy goal

KitaLDR is designed around a simple rule:

> **Your action goes only to your person.** ❤️

The MVP will avoid unnecessary tracking, public user discovery, location tracking, or a permanent pairing code.

---

Made with ❤️ for an LDR anniversary project.
