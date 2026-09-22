# Privacy and data handling

CalorieTracker has no application server, accounts, ads, analytics, or crash-upload service.

**On the phone:** profile, plans, diary snapshots, recipes, measurements, imported health
data, weekly reviews, messages, hidden provider reasoning, compaction summaries and pending actions live in an AES-256-GCM encrypted
Room vault. Photos and the API key are separately encrypted. Encryption keys stay in Android
Keystore. The vault is stored in app-private storage; automatic Android cloud/device backup
is disabled. Optional app locking uses device biometrics or credentials. The OS and a
compromised/unlocked device remain outside this protection boundary.

**Network connections:** food queries and barcodes go to Open Food Facts; version checks
and APK downloads go to GitHub. These providers see ordinary network information such as
IP addresses. Barcode recognition runs on the device using the bundled ML Kit model.

**DeepSeek:** when you use the coach, your messages, relevant profile/diary/health records,
context summaries and selected chat images are sent directly to DeepSeek's API using your
own key. Previously shared recent images can be included again for conversational continuity.
Thinking-mode reasoning is retained encrypted only when required for provider protocol continuity;
it is excluded from the visible conversation and diagnostic logs.
The app does not control DeepSeek's processing or retention. See https://www.deepseek.com
and your DeepSeek account terms. Photos that have not been attached/shared with the coach
are not automatically sent. The coach has no general web browsing capability.

**Health Connect:** read-only access to individually approved types. Aggregated steps and
energy use Health Connect's source-priority rules. Exercise calories are never added to
total-energy records or automatically added to food targets. The app refreshes 30 days,
or 90 days with optional history access, and can sync in the background with permission.
Changing permissions can remove corresponding imported records on reconciliation.
Weekly calculations run locally. DeepSeek receives a weekly review only after the user explicitly
asks the coach to explain it.

**Backups:** explicit password-encrypted files (AES-GCM with PBKDF2-HMAC-SHA256,
600,000 iterations, independent random salt/nonce). Export may include photos. API keys
are never exported. Restore replaces the local diary and drops imported health records
and device sync state. Passwords cannot be recovered. Treat exported files as private.

**Deletion:** messages, photos, or all local records can be deleted. Deleting messages or
photos invalidates derived context summaries. Local deletion cannot retract requests
already sent to third-party services. Uninstalling removes the only local copy unless
you created an explicit backup.
