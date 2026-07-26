# FinanceMate

A privacy-first Android budgeting app. Build a budget, import your bank
statements, and find the money you're leaking — duplicate subscriptions, silent
price rises, zombie memberships, avoidable fees.

**Native Kotlin + Jetpack Compose. No backend. Your data stays on your phone.**

---

## The design decision everything else follows from

The upload format is not the privacy boundary. **The network boundary is.**

So ingestion and analysis are deliberately decoupled from egress:

- **Ingestion is rich and entirely on-device.** CSV / OFX / QFX / QIF parsing,
  PDF text extraction, and screenshot OCR all run locally. Raw statement data
  never leaves the phone, in any mode, with any setting.
- **Analysis is deterministic and entirely on-device.** Recurring-payment
  detection, duplicate subscriptions, price-hike detection, fee detection, and
  cashflow forecasting are arithmetic and pattern-matching. They do not need a
  language model and are *more* accurate without one.
- **AI is a narrow, opt-in, auditable egress point.** Only redacted merchant
  strings and rounded aggregates ever cross the wire, through a single gate,
  with a preview of the exact JSON before the first send of each feature.

A consequence worth stating plainly: **the app is fully useful before it makes a
single API call.** That is what makes the privacy posture cheap to hold — nothing
essential depends on egress, so there is never pressure to relax it.

### Why not screenshots-only, as a privacy measure?

A screenshot is opaque pixels. To use one you must send the whole image to a
vision model, and you cannot programmatically redact what you cannot parse. A
CSV parsed on-device need never leave the phone at all.

On Android specifically, screenshots *are* a good ingestion path — but because
ML Kit does text recognition **on-device**, not because images are inherently
safer. The privacy comes from where the computation happens, not the file format.

---

## Architecture

```
:app             Compose UI, navigation, DI wiring
:core:money      Money value type — Long minor units + ISO currency. No Double, ever.
:core:model      Domain entities. Pure Kotlin.
:core:parsing    CSV / OFX / QFX / QIF parsers, merchant normalisation, dedup hashing.
:core:crypto     Keystore wrapper, DB key management, biometric unlock.
:core:data       Room + SQLCipher, DAOs, repositories.
:feature:import  SAF, PDF extraction, on-device OCR, column-mapping and review UI.
:feature:budget  Envelope and 50/30/20 budgeting, categories, rules engine.
:feature:insight The deterministic savings engine. Pure Kotlin.
:ai              Redaction gate + Anthropic client. THE ONLY MODULE THAT TOUCHES THE NETWORK.
```

Two deliberate choices in that list:

**Everything carrying real logic is a pure-JVM module.** `:core:money`,
`:core:model`, `:core:parsing`, and `:feature:insight` have no Android
dependency, so the code that decides what your money did is testable in
milliseconds without an emulator.

**`:ai` is a structural choke point, not a convention.** It is the only module
that depends on an HTTP client. If you find yourself wanting networking
elsewhere, that is the signal something is about to bypass the redaction gate.
`INTERNET` plus `:ai` is the complete surface anyone auditing this app's privacy
claims needs to read.

---

## Money is never a floating-point number

`0.1 + 0.2 != 0.3` in binary floating point. A budgeting app that drifts by
fractions of a cent across thousands of transactions produces totals its user
cannot reconcile against their bank.

`Money` stores whole minor units in a `Long` with an ISO-4217 currency, and
refuses to mix currencies. Splitting is exact by construction: `allocate` uses
the largest-remainder method, so parts always sum back to the original — a
three-way split of $10.00 yields $3.34 / $3.33 / $3.33, never a lost cent. This
is enforced by property tests over thousands of generated cases, not by example.

---

## Build

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew test
```

```bash
./gradlew :app:bundleRelease
```

**Requirements:** JDK 17, Android SDK with API 36. Everything else is fetched by
the Gradle wrapper.

### If your checkout is on a network drive

SMB holds file handles long enough that Gradle intermittently fails to clear its
own outputs. Add this to `local.properties` (gitignored) to put build output on
local disk — it also makes builds several times faster:

```properties
financemate.buildDir=C:/FinanceMateBuild
```

`gradle.properties` also disables VFS watching and runs the Kotlin compiler
in-process for the same reason. Both are safe to remove on a local disk.

---

## Toolchain

| | |
|---|---|
| AGP | 9.3.1 — note it supplies Kotlin itself; applying `kotlin-android` is an error |
| Gradle | 9.6.1 |
| Kotlin | 2.4.10 |
| minSdk / targetSdk | 26 / 36 |

---

## Status

Phase 0 complete: project scaffolded, toolchain verified end to end, `Money`
implemented and property-tested, and the Anthropic-SDK-on-Android risk closed
(see [ADR 0001](docs/adr/0001-anthropic-sdk-on-android.md) — it found a
release-only failure that is invisible in debug builds).

Next: the data model and import pipeline, then budgeting, then the savings
engine. AI comes last, deliberately.
