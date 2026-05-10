# Hardcoded user-facing strings — follow-up inventory

This document tracks user-facing English literals that remain in
Java code after the "move error-messages to `en_us.json`" sweep. It
is a follow-up backlog, not a list of bugs: every entry below has a
deliberate reason it was deferred (cross-platform parity coupling,
defense-in-depth fallback, or a non-translatable token).

The runtime localization source of truth is
[`app/src/main/res/raw/en_us.json`](../../../main/res/raw/en_us.json).
Strings in its `langValues` block are **byte-compared with the iOS
sibling repo** by
[`EnUsParityTest`](../../java/com/quantumcoinwallet/app/locale/EnUsParityTest.java);
the `errors` block is Android-only and was the target of the
preceding sweep. Adding any new `langValues` key REQUIRES a
matching edit in the iOS repo (or an entry in
`OS_SPECIFIC_DIVERGENCES`).

The audit was last run against the tree containing the lockout /
network-add / reveal-wallet localizations. Line numbers may drift;
treat each row as a starting point, not a precise pointer.

---

## Group A — pure literals (deferred)

Each requires either an iOS-side coordinated `langValues` add OR a
permanent allow-list entry. Filed here so a future pass can land
them in one batch rather than as ad-hoc one-off changes.

### Tamper / security gate
- [`TamperGatePolicy.java`](../../../main/java/com/quantumcoinwallet/app/security/TamperGatePolicy.java)
  — `title = "Security check"`, `continueLabel = "Continue at my own risk"`, and the
  three body paragraphs in `describe(Severity)` (ROOT_SUSPECTED,
  DEBUGGER_ATTACHED_IN_RELEASE, RUNTIME_TAMPER_DETECTED, default).
  The catalog already has `tamper-*-banner / -message / -title`
  keys but the dialog body is hand-rolled; the catalog wording is
  shorter than the in-Java prose. Choice for the future pass:
  either (a) shorten the in-Java prose to match the catalog and
  drop the literals or (b) add a parallel set of
  `tamper-*-long-message` keys on both platforms.

### Permission prompt defaults
- [`GlobalMethods.java`](../../../main/java/com/quantumcoinwallet/app/utils/GlobalMethods.java)
  — `Permission required` (default dialog title) and `Open Settings`
  (default positive button). Not in the catalog on either platform.
  Both are Android-flavored ("Settings" is the Android in-app
  navigation target); a future pass should add
  `permission-required-title` / `open-settings-button` as
  `langValues` keys on both platforms, OR allow-list them as
  Android-only.

### Backup executor fallbacks
- [`BackupExecutor.java`](../../../main/java/com/quantumcoinwallet/app/backup/BackupExecutor.java)
  — `safe()`-shaped fallbacks for `Backup failed: …`,
  `Wallet backed up`, `Backup submitted`,
  `Backup submitted to cloud`, `Wallet submitted to cloud
  destination. Upload may take a moment.`, `Wallet exported`,
  `Export failed: …`, `Press OK to dismiss.`, and the
  `cloud-backup-info` fallback at line 142. The catalog already
  exposes `backup-saved`, `backup-failed`, `backup-submitted-cloud-*`
  and `cloud-backup-info`; these fallbacks fire only on a missing
  key — they are defense-in-depth. Low-priority follow-up: audit
  that all six template keys are present on iOS, then collapse
  these fallbacks to a single tight English string.

### Error-title literals on exception paths
- [`HomeWalletFragment.java`](../../../main/java/com/quantumcoinwallet/app/view/fragment/HomeWalletFragment.java)
  — `"Error"` literal title at lines 880, 1039, 1671 (passed
  through `ExceptionError`), 2021, etc. The catalog already has
  `errorTitle`. Each is a one-line `safe(vm.getErrorTitleByLangValues(),
  "Error")` substitution; deferred because there are ~10 sites and
  they all funnel through the same dialog helper. A future pass
  could either (a) replace the literals in place or (b) move the
  fallback logic into `GlobalMethods.ShowErrorDialog` so callers
  pass `null` and the helper substitutes.

### Restore-summary column labels
- [`HomeWalletFragment.java`](../../../main/java/com/quantumcoinwallet/app/view/fragment/HomeWalletFragment.java)
  lines ~3444–3461 — `Status`, `Address`, `Restored`,
  `Already exists`, `Skipped`. These match the
  `restore-summary-status-column` / `-address-column` /
  `-status-restored` / `-status-skipped` / `-status-already-exists`
  keys already in the catalog; they fire only on a catalog miss.
  Same defense-in-depth pattern; no functional bug.

### Send-success and restore-progress fallbacks
- [`HomeWalletFragment.java`](../../../main/java/com/quantumcoinwallet/app/view/fragment/HomeWalletFragment.java)
  lines ~3387, ~3640, ~3655, ~3659 — fallbacks for
  `[COUNT] wallet(s) were restored. Enter password for the
  remaining.`, `Unable to decrypt. Enter a different password or
  skip this file.`, `The wallet with following address already
  exists:\n[ADDRESS]`, `OK`. All keys exist in the catalog; same
  defense-in-depth pattern.

### Accessibility / brand literals (intentionally English-only)
- [`BackupPasswordDialog.java:45`](../../../main/java/com/quantumcoinwallet/app/view/dialog/BackupPasswordDialog.java)
  — `Show or hide password` content description. Documented as
  intentionally English-only; mirrors the rationale on
  `strings.xml password_toggle_content_description` (Android
  `TextInputLayout` does not support runtime relabeling of the
  static `passwordToggleContentDescription` attribute).
- [`TransactionReviewDialog.java:246`](../../../main/java/com/quantumcoinwallet/app/view/dialog/TransactionReviewDialog.java)
  — `i agree` literal English fallback for the agree-gate
  validation. The dialog accepts EITHER the localized
  `i-agree-literal` OR the English literal so a partially
  translated bundle never permanently blocks the user; deliberate.
- [`SendFragment.java:1360`](../../../main/java/com/quantumcoinwallet/app/view/fragment/SendFragment.java)
  — `QuantumCoin` brand literal (asset name). Non-translatable.
- [`AccountTransactionsFragment.java:161-162`](../../../main/java/com/quantumcoinwallet/app/view/fragment/AccountTransactionsFragment.java)
  — `<` and `>` pagination glyphs. Non-translatable.

---

## Group B — `safe(vm.get*ByLangValues(), "English fallback")`

These are defense-in-depth fallbacks: the catalog key DOES exist
on both platforms, but the call site keeps a hardcoded English
literal as the catalog-miss fallback. Acceptable as-is; flagged
only so a future pass can decide whether to collapse them.

Approximate sites (line numbers may drift):
- [`BackupPasswordDialog.java`](../../../main/java/com/quantumcoinwallet/app/view/dialog/BackupPasswordDialog.java)
  — ~25 `safe()` calls covering `Backup password`, `Password`,
  `Confirm password`, `OK`, `Cancel`, `Error`,
  `Enter a password`, `Wallets to restore:`, etc.
- [`TransactionReviewDialog.java`](../../../main/java/com/quantumcoinwallet/app/view/dialog/TransactionReviewDialog.java)
  — `Please review your transaction request to be sent:`,
  `What is being sent?`, `Contract address:`, `From Address`,
  `To Address`, `Send quantity`, `chain`, `Network`, `Type `,
  `I agree`, ` to confirm:`, `Cancel`, `OK`, `Error`,
  `Please type "…" to confirm.` (compound interpolation).
- [`TamperGatePolicy.java:84`](../../../main/java/com/quantumcoinwallet/app/security/TamperGatePolicy.java)
  — `safe(vm.getCloseByLangValues(), "Close app")`.
- [`SettingsFragment.java`](../../../main/java/com/quantumcoinwallet/app/view/fragment/SettingsFragment.java)
  — `Phone Backup`, `Enabled`, `Disabled`.

---

## Group C — dynamic / non-localizable

- `e.getMessage()` exception passthroughs (every
  `GlobalMethods.ExceptionError` call). Localization is not
  meaningful; the surfaced string is whatever the underlying
  library produced.
- `R.string.*` toasts in
  [`SendFragment.java`](../../../main/java/com/quantumcoinwallet/app/view/fragment/SendFragment.java)
  (lines 376, 396, 1126, 1185, 1548, 1595) and
  [`HomeActivity.java`](../../../main/java/com/quantumcoinwallet/app/view/activities/HomeActivity.java)
  (lines 410, 413, 416, 419, 422, 443, 444, 449, 450, 1126,
  1386, 1426, 1427, 1430, 1439, 1440) — these go through
  Android resources, not `en_us.json`. The two pipelines are
  intentional: notification-channel labels, retry-layout error
  strings, network-error toasts (HTTP `4xx`) use `strings.xml`,
  while UI screens use `en_us.json` (parity-checked with iOS).
  See README "Source of truth for strings" notes.

---

## Out-of-scope: bridge identifiers

Strings like `INVALID_ADDRESS` / `BAD_AMOUNT` returned by
`QuantumCoinJSBridge` to the WebView are protocol identifiers, not
user-facing UI. They are translated to user copy inside the
JavaScript bundle. Do NOT localize these on the Java side.
