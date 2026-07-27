# FinanceMate — Product Brief

**An Android budgeting app that finds money you're wasting, without your
financial data ever leaving your phone.**

_Last updated: 2026-07-26. Companion documents: [design brief](design-brief.md),
[ADR 0001](adr/0001-anthropic-sdk-on-android.md)._

---

## 1. In one paragraph

Most people have more subscriptions than they can list from memory. FinanceMate
reads your bank statements — on your device, never uploaded — and tells you where
money is quietly leaking: two services doing the same job, a price that crept up
without you noticing, a membership you stopped using, fees you didn't know you
were paying. It also runs an ordinary envelope budget. It has no account, no
login, and no server.

---

## 2. The problem

Three things are true at once for most households:

1. **Recurring spend is invisible.** A £12 subscription doesn't feel like a
   decision. Twenty of them are £3,000 a year, and almost nobody can list them
   from memory.
2. **Price rises go unnoticed.** Vendors raise prices in single-digit
   increments. The charge still looks familiar on a statement, so it passes.
3. **Budgeting apps demand a lot before they give anything.** Most require
   linking bank accounts through an aggregator, then present an empty budget you
   have to fill in. The effort comes first, the payoff much later, and most
   people churn in between.

**The wedge:** lead with the payoff. Import one statement and immediately get
told something you didn't know — "you're paying for two music services, that's
£146 a year". Budgeting becomes the thing you do *after* the app has already
proved useful, not the price of entry.

---

## 3. Who it's for

**Primary:** someone with enough financial complexity to have lost track — many
subscriptions, more than one card, a partner with their own. They want the app
to do the noticing. They are not a spreadsheet enthusiast.

**Secondary:** the privacy-conscious. People who won't hand bank credentials to
an aggregator, and are therefore locked out of most of this category entirely.

**Not for:** people wanting investment tracking, tax, or business accounting.

---

## 4. How it's different

| | Typical budgeting app | FinanceMate |
|---|---|---|
| **Getting data in** | Link accounts via aggregator (bank credentials) | Import a statement file you already have |
| **Where data lives** | Vendor's servers | Your device, encrypted |
| **Works offline** | No | Entirely |
| **Account required** | Yes | None |
| **Business model pressure** | Your data is an asset | It never leaves; it can't be |

The category standard is credential-sharing with a third-party aggregator. That
excludes everyone unwilling to do it, and creates an ongoing breach liability for
everyone who does. FinanceMate takes the file-import route instead: more friction
at the start, no credentials, no server, no breach surface.

**This is a real trade-off, not a free win.** Aggregator apps update
automatically; ours requires the user to import a statement periodically. We're
betting that a meaningful group prefers that, and that leading with a savings
insight makes the first import worth doing.

---

## 5. How it works

```
Statement file  ─►  parse on device  ─►  encrypted ledger  ─►  local analysis  ─►  findings
   CSV/OFX/QIF          no network          SQLCipher            arithmetic         UI
                                                                     │
                                                                     ▼
                                                            optional, opt-in
                                                            redacted → AI API
```

**Three properties follow from this shape:**

1. **Ingestion is rich and local.** Everything the app can read, it reads on the
   device. There is no mode in which a statement is uploaded.
2. **The analysis needs no AI.** Recurring detection, duplicates, price rises,
   fees, and forecasting are arithmetic and pattern-matching. A language model
   would be slower, cost money, and occasionally be wrong about a question that
   has an exact answer.
3. **The app is fully useful before any network call.** That's what makes the
   privacy stance cheap to hold — nothing essential depends on egress, so there's
   never pressure to relax it.

### The engine, briefly

- **Merchant normalisation** reduces noisy bank descriptors to a stable key, so
  the same shop in two cities groups as one merchant. Everything downstream
  depends on this.
- **De-duplication** means re-importing an overlapping statement is a no-op —
  enforced by a database constraint, not by application code.
- **Recurring detection** clusters by merchant and classifies cadence from the
  gaps between charges, tolerating missed payments and price changes.
- **Subscription analysis** turns that into duplicates, price rises, and
  annualised costs.

---

## 6. The privacy model

This is the product's spine, so it's specified rather than asserted.

**What never leaves the device, in any mode:** statement files, transaction
descriptions, amounts, dates, balances, account numbers, or anything derived
directly from them.

**What can leave, only with explicit opt-in:** redacted merchant strings (no
amounts, no dates) for categorisation, and rounded category aggregates for
narration.

**How that's enforced:**

- One module (`:ai`) is the only code permitted to touch the network. A
  structural test asserts no other module can reach an HTTP client, so `INTERNET`
  plus that one module is the complete surface anyone auditing this has to read.
- Every outbound payload passes through a single redaction gate.
- The first send of each feature shows the literal JSON and requires confirmation.
- An **egress log** records every request ever made, viewable in-app.

**Storage:** the ledger is a SQLCipher-encrypted database whose key is generated
on-device and sealed in the hardware-backed Keystore. Verified on a real device —
the database file's header is random bytes, not SQLite's magic string.

**The honest cost:** because the key is device-bound, the database cannot be
opened elsewhere. Cloud backup is deliberately disabled, since a backup that
silently can't be restored is worse than none. Migration therefore depends on the
app's own encrypted export, which makes that export a requirement rather than a
nice-to-have.

**AI access is bring-your-own-key.** The user supplies their own Anthropic API
key, sealed in the Keystore; requests go device-to-Anthropic with no
intermediary. Nobody — including us — sits in the middle. There is no server to
run and no per-user cost.

---

## 7. What exists today

**Working, and verified on a physical Pixel 8 Pro.**

| Area | State |
|---|---|
| Statement import — CSV, OFX, QFX, QIF | Done. 7 built-in bank profiles, auto-detection for unknown layouts |
| Merchant normalisation & de-duplication | Done |
| Encrypted ledger | Done. Room + SQLCipher + Keystore |
| Recurring-payment detection | Done |
| Duplicate subscriptions, price rises | Done |
| User-taggable merchants | Done |
| Import + Savings screens | Done — but that is the entire UI |
| PDF / screenshot-OCR import | Not started |
| Budgeting | Not started |
| Fees, cashflow forecast, what-if simulator | Not started |
| AI features | Not started (transport proven, no features use it) |

**Quality:** 208 automated tests. The parsing, money arithmetic, de-duplication
and detection logic are heavily covered; the engine produced correct figures to
the cent on a real device against a seeded statement.

**The honest gap:** two screens is not an app. There is no accounts management,
no transaction list, no editing, no settings, and no onboarding. The engine is
well ahead of the interface around it.

**Size:** roughly 19 MB per-device download, dominated by the bundled on-device
OCR model rather than anything avoidable.

---

## 8. Roadmap

Ordered by the sequencing that keeps the app coherent, not by ambition.

**Now — make it usable.** Navigation, accounts, transaction list and editing,
settings, onboarding. The engine is ahead of the app; more detectors won't help
if you can't correct a miscategorised transaction.

**Next — the other half of the promise.** Envelope and 50/30/20 budgeting,
category rules that auto-categorise on import, month-over-month comparison.

**Then — finish the savings engine.** Fee detection, cashflow forecast with
safe-to-spend and projected low-balance date, bill calendar, and the what-if
cancellation simulator ("cancel these three → £412 a year").

**Then — more ways in.** PDF statement parsing and screenshot OCR, both fully
on-device.

**Then — AI, deliberately last.** Merchant categorisation via the Batches API,
insight narration, chat over redacted aggregates, bill-negotiation scripts.
Everything it narrates already works without it.

**Later — goals.** Trip planner, sinking funds, emergency-fund runway, debt
payoff comparison, net worth. Household features: expense splitting, per-person
breakdown.

---

## 9. Decisions that shape everything

| Decision | Why |
|---|---|
| **File import, not aggregator** | No credentials, no server, no breach surface. Costs convenience |
| **Money as integer minor units** | Floating point drifts; a budget that can't be reconciled against the bank is worthless |
| **Deterministic engine, AI optional** | The core questions have exact answers. An LLM would be slower, costlier, and occasionally wrong |
| **One sign convention, applied at import** | Negative is money out, every account type. Card issuers disagree; normalising at the boundary means nothing downstream has to think about it |
| **De-duplication in the schema** | A unique index survives refactors and concurrent imports; application logic doesn't |
| **Native Kotlin, not cross-platform** | Hardware Keystore, on-device OCR, and fast local computation over thousands of rows |
| **Bring-your-own AI key** | No server, no per-user cost, nobody in the middle |
| **Pure-JVM core modules** | The logic that decides what your money did is testable in milliseconds without an emulator |

---

## 10. Risks

**First-import friction is the main commercial risk.** Exporting a statement is
more work than tapping "connect bank". The mitigation is that the payoff arrives
immediately and is concrete. If users don't reach a real insight in their first
session, the model doesn't work.

**Merchant coverage is inherently incomplete.** The built-in service catalogue
has ~120 entries and cannot cover regional or niche providers. User tagging
covers the gap, but early users will do noticeable tagging. AI categorisation
would help — at the cost of making a core feature depend on opt-in egress.

**Detection is probabilistic.** A false subscription is worse than a missed one,
because users act on it. The engine is deliberately biased toward silence: it
requires most inter-charge gaps to fit a cadence, matches merchants exactly
rather than fuzzily, and doesn't flag things people plausibly choose (several
video services). This costs recall on purpose.

**Device-bound encryption means data loss is possible.** Lose the phone without
an export and the ledger is unrecoverable. This is the correct trade for
financial history, but the export feature has to be excellent and prominent.

**Android-only.** An iOS version means a rewrite; the core logic is
Kotlin and could move to Kotlin Multiplatform, but the crypto, storage and UI
layers would not.

---

## 11. What "good" looks like

- A new user reaches a **specific, true, surprising** statement about their own
  money within their first session.
- Every number in the app can be **tapped down to the transactions behind it**.
- Re-importing an overlapping statement **changes nothing**, silently and
  correctly.
- A user can say **"that's wrong"** about any finding and have it stay corrected.
- The privacy claim is something a sceptical user can **verify themselves**, in
  the app, in under a minute.

---

## 12. Open questions

1. **Is the savings hook strong enough to carry the import friction?** Untested
   with real users. It's the central bet.
2. **How much budgeting is enough?** Full envelope budgeting is a large build.
   Would a lighter "where does it go" view satisfy most people?
3. **Multi-account handling** — detect the account from the file, or ask? Today
   everything lands in one auto-created account.
4. **What is the Home screen?** Dashboard, attention feed, or a single
   safe-to-spend number. Decides the app's daily character.
5. **Is bring-your-own-key too high a barrier** for AI features to ever get used,
   and does that matter given everything works without them?
6. **Region.** Built US-first. UK/EU means new statement formats, date ordering,
   and a different service catalogue.
