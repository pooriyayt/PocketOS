# Security Policy

## Privacy-First Architecture

PocketOS is built around a simple principle:

> **"Your data stays on your device."**

The application functions completely offline without requiring accounts, remote databases, or sync servers.

---

## Security Architecture

### 1. Database Encryption at Rest
- Local data is stored in a SQLite database encrypted via **SQLCipher** (AES-256 in CBC mode with HMAC-SHA512 integrity checks).
- The encryption passphrase is generated using `SecureRandom` on first launch.
- The passphrase is encrypted and stored in `EncryptedSharedPreferences` backed by the **Android Keystore**, ensuring keys never leave hardware security modules on supported devices.

### 2. Encrypted Backups
- Encrypted export files (`.pocketos`) use **AES-256-GCM** authenticated encryption.
- Key derivation uses **PBKDF2WithHmacSHA256** with **100,000 rounds** and a 16-byte random salt.
- Every backup payload includes a 12-byte initialization vector (IV) and a 128-bit authentication tag to prevent tampering.

### 3. In-App Update Checks & Transparency
- The app checks only the official repository (`https://github.com/pooriyayt/PocketOS/releases`) over HTTPS.
- PocketOS never executes dynamic code or silently installs packages. When updates are available, release notes are presented and download links open transparently in the user's browser or download manager.
- Zero dropper capabilities: no package installer intents or package management permissions exist in the application.

### 4. Minimal Permissions Model
PocketOS only requests:
- `android.permission.INTERNET` (strictly for checking GitHub releases upon user request)
- `android.permission.ACCESS_NETWORK_STATE` (to detect connectivity before update checks)
- `android.permission.POST_NOTIFICATIONS` (for user-scheduled reminders and renewal alerts)
- `android.permission.SCHEDULE_EXACT_ALARM` (for precise notification delivery)
- `android.permission.RECEIVE_BOOT_COMPLETED` (to restore alarms after reboot)
- `android.permission.USE_BIOMETRIC` (for device biometric app lock)

No sensitive permissions (SMS, contacts, location, notification listeners, accessibility, install packages) are requested.

---

## Reporting a Vulnerability

If you discover a security vulnerability or bug in PocketOS:
1. Please **do not** open a public GitHub issue.
2. Report the vulnerability privately via GitHub Security Advisories or contact the repository owner at `https://github.com/pooriyayt/PocketOS/security/advisories`.
3. Provide detailed steps to reproduce the issue.
4. We will review and publish a fix promptly.
