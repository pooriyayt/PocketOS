# Contributing to PocketOS

Thank you for your interest in improving PocketOS! PocketOS is a privacy-first, local-first Android application.

---

## Code of Conduct

Please maintain a respectful, constructive, and inclusive community environment.

---

## Development Setup

1. **Prerequisites**:
   - JDK 17 or JDK 21
   - Android SDK (API 36 / 37)
   - Android Studio Ladybug (or newer)
2. **Build and Test**:
   ```bash
   ./gradlew compileDebugKotlin
   ./gradlew assembleDebug
   ```

---

## Contribution Guidelines

1. **Local-First Integrity**:
   - Never add dependencies on remote user accounts or telemetry trackers.
   - User data must always remain encrypted and on-device.
2. **Code Style**:
   - Follow idiomatic Kotlin coding conventions.
   - Use Jetpack Compose Material 3 and follow the Emerald luxury design tokens defined in `app/src/main/java/app/pocketos/ui/theme`.
3. **Commit Messages**:
   - Use standard conventional commit format: `feat:`, `fix:`, `refactor:`, `docs:`, `chore:`.
4. **Pull Requests**:
   - Ensure `./gradlew compileDebugKotlin` and `./gradlew assembleDebug` pass without warnings or errors.
   - Test UI responsiveness on both Dark and Light themes.
