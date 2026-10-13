# Security Policy

## Privacy-First Architecture

PocketOS is engineered around a core, uncompromising principle:

> **"Your data stays on your device."**

The application functions completely offline without requiring user accounts, remote databases, telemetry SDKs, or cloud sync servers.

---

## Security Architecture & Cryptographic Safeguards

### 1. Cryptographic Standards at Rest

| Component | Standard / Algorithm | Key Derivation & Management | Protection Level |
| :--- | :--- | :--- | :--- |
| **Local Database** | SQLCipher (AES-256-CBC + HMAC-SHA512) | Hardware-backed `MasterKey` in Android Keystore | Hardware Security Module (HSM / StrongBox) |
| **Passphrase Storage** | `EncryptedSharedPreferences` (AES-256-SIV / GCM) | Keystore-backed key wrapping via Android Jetpack Security | Protected against device extraction and root inspect |
| **Encrypted Backups** | AES-256-GCM (Authenticated Encryption) | PBKDF2WithHmacSHA256 (100,000 iterations + 16-byte salt) | Tamper-proof 128-bit authentication tag |
| **App Lock Vault** | PBKDF2 + SHA-256 with cryptographically secure salt | Per-device salt generated via `SecureRandom` | Protection against offline brute-force attacks |

### 2. Permissions Model

PocketOS operates under a strict principle of least privilege. The application does not collect device telemetry, identity tokens, or advertising identifiers.

| Permission | Technical Name | Operational Scope & Purpose |
| :--- | :--- | :--- |
| **Internet Access** | `android.permission.INTERNET` | Strictly for querying official GitHub Release API for version updates. |
| **Network State** | `android.permission.ACCESS_NETWORK_STATE` | Verifies network availability before making release check calls. |
| **Notifications** | `android.permission.POST_NOTIFICATIONS` | Delivers local user-configured reminders, installment alerts, and subscription warnings. |
| **Exact Alarms** | `android.permission.SCHEDULE_EXACT_ALARM` | Ensures timely delivery of installment and event reminders. |
| **Boot Completed** | `android.permission.RECEIVE_BOOT_COMPLETED` | Reschedules user alarms and notifications after system reboot. |
| **Biometric Auth** | `android.permission.USE_BIOMETRIC` | Optional biometric authentication for app unlock via system prompt. |

> [!NOTE]
> PocketOS explicitly requests **ZERO** intrusive permissions: no SMS, contacts, location, background audio, notification listener, accessibility services, or package installation (`REQUEST_INSTALL_PACKAGES`) capabilities.

---

## Update Mechanism & Binary Integrity

- **Zero Dropper Capabilities**: PocketOS does not silently download executable code, load dynamic `.dex` files, or install APK packages in the background.
- **Transparent Updates**: When an update is detected, release notes are presented to the user. Tapping update opens the download URL directly in the user's default web browser or package manager.
- **Reproducible & Optimized**: Release binaries are compiled with ProGuard/R8 code shrinking and optimization, ensuring that no unused debug libraries remain in production releases.

---

## Reporting a Vulnerability

We value the security community and take all security vulnerabilities seriously. If you identify a potential security vulnerability or privacy flaw in PocketOS, please follow responsible disclosure:

1. **Do not** disclose vulnerabilities through public GitHub issues or discussions.
2. Submit a private report via **[GitHub Security Advisories](https://github.com/pooriyayt/PocketOS/security/advisories)**.
3. Alternatively, contact the maintainer directly through the official website contact portal: **[https://pouriyaparniyan.ir](https://pouriyaparniyan.ir)**.

### Report Submission Guidelines
To help us triage and resolve reports quickly, please include:
- A clear description of the vulnerability and its potential impact.
- Exact reproduction steps or a minimal proof of concept (PoC).
- Affected PocketOS version and Android OS version tested.

We will acknowledge receipt of your report within 48 hours and work diligently to issue a timely security patch.

---

================================================================================
                           خلاصه فارسی خط‌مشی امنیت
================================================================================

### معماری اولویت حریم خصوصی (Privacy-First)

اصل بنیادین در طراحی پاکت‌او‌اس: **«داده‌های شما فقط و فقط روی دستگاه خودتان باقی می‌ماند.»**  
برنامه کاملاً آفلاین عمل کرده و هیچ نیازی به ساخت حساب کاربری، سرورهای ریموت یا اتصال ابری ندارد.

#### ۱. جدول استانداردهای رمزنگاری
- **پایگاه داده محلی**: رمزگذاری کامل دیتابیس با **SQLCipher** (الگوریتم AES-256 و اعتبارسنجی HMAC-SHA512). کلید اصلی در ماژول سخت‌افزاری امنیتی دستگاه (Android Keystore) نگهداری می‌شود.
- **پشتیبان‌گیری رمزنگاری‌شده (`.pocketos`)**: رمزنگاری احرازهویت‌شده **AES-256-GCM** همراه با مشتق‌سازی رمزعبور کاربر با **PBKDF2** (۱۰۰ هزار دور تکرار و سالت تصادفی).
- **قفل برنامه**: حفاظت از پین‌کد با توابع هش امن و سالت‌های مشتق‌شده از `SecureRandom`.

#### ۲. مجوزها و دسترسی‌های سیستم
برنامه از اصل حداقل دسترسی ممکن پیروی می‌کند:
- **اینترنت**: صرفاً برای استعلام نسخه‌های جدید از ریپازیتوری رسمی گیت‌هاب (به درخواست کاربر).
- **نوتیفیکیشن و آلارم دقیق**: فقط برای یادآوری‌های سررسید اقساط، چک‌ها و اشتراک‌ها.
- **بیومتریک**: برای قفل امن برنامه با اثر انگشت یا چهره.
- **عدم درخواست دسترسی‌های خطرناک**: برنامه به هیچ‌وجه دسترسی پیامک، مخاطبین، موقعیت مکانی، دسترسی‌های پکیج اینستالر (`REQUEST_INSTALL_PACKAGES`) یا قابلیت‌های مشکوک به بدافزار درخواست نمی‌کند.

#### ۳. گزارش آسیب‌پذیری‌های امنیتی
در صورت کشف هرگونه باگ یا آسیب‌پذیری امنیتی:
- از طرح موضوع در ایشوهای عمومی خودداری فرمایید.
- گزارش محرمانه خود را از طریق بخش **[GitHub Security Advisories](https://github.com/pooriyayt/PocketOS/security/advisories)** ارسال کنید.
- یا از طریق درگاه ارتباطی وب‌سایت رسمی اقدام فرمایید: **[https://pouriyaparniyan.ir](https://pouriyaparniyan.ir)**.
