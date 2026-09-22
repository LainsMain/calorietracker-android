# Architecture

Single-activity native Kotlin/Compose application, Hilt construction, immutable domain
models, a Room encrypted aggregate vault, DataStore display preferences, and WorkManager
periodic work. Kotlin coroutines/StateFlow connect repositories to the UI. No backend.

- `domain`: deterministic portion math, recipe yields, plan estimates, chronology,
  conservative evidence summaries and bounded chat-context selection.
- `data`: AES-GCM/Keystore, Room persistence and atomic/idempotent reviewed actions.
- `services`: independently bounded food providers, DeepSeek streaming/tool loop,
  Health Connect reconciliation, password backups and signed-APK verification.
- `ui`: Material 3 screens, numeric editing, camera preview, local encrypted image decoding.

The Room row is an encrypted versioned aggregate. This makes multi-record actions and
restore atomic and avoids leaving sensitive indexed columns in plaintext. All vault
encoding/encryption/database IO runs off the UI thread. This is intended for personal
usage, not a shared multi-user database. A future per-record encrypted schema should
use an explicit migration and preserve IDs/snapshots; never use destructive migration.
Room schema 1 is checked in. JSON schema fields use backward-compatible defaults;
unsupported backup schemas are rejected before replacement.

Food entries embed their nutrient snapshot. Recipe versions embed their ingredient food
snapshots. Food-provider changes, recipe edits and target changes cannot rewrite diary
history. Unknown/trace nutrient values propagate as unknown, including recipe totals.

The AI can query and prepare actions but has no apply tool. Only explicit UI actions can
call `Store.applyProposal`; a mutex and status transition make repeated taps idempotent.
Each applied action has an audit event and undo. Numeric computations remain in app code.
Untrusted provider/model content never becomes executable code. All original chat history
is retained locally; compaction adds date-ranged summaries and retrieval remains available.

Health Connect uses permission-scoped aggregates (respecting platform source priority),
record pagination and a full reconciliation of the readable window. Changes tokens are
refreshed and expired tokens recover through the same snapshot path. Reconciliation
replaces data, so repeated sync and source deletions do not accumulate totals.

Update manifests are public. The downloaded APK must match its SHA-256, expected byte count,
application ID, newer version and the installed app's signing certificate. Android owns
installation confirmation. A compromised manifest cannot make a differently signed APK pass.
