# FinanceMate — Design Brief

**For:** anyone designing screens for this app.
**Status:** the engine works; the app around it barely exists. This is the right
moment to design properly, before more screens get built by accident.

---

## 1. What this is

An Android budgeting app that does two things:

1. **Finds money you're wasting** — duplicate subscriptions, silent price rises,
   zombie memberships, avoidable fees.
2. **Helps you run a budget** — envelopes, categories, what's left this month.

The hook is the first one. Nobody wakes up wanting to do budgeting admin; people
do want to be told they're paying for two music services.

**Who it's for:** someone who has more subscriptions than they can list from
memory, and enough financial complexity to have lost track. Not a spreadsheet
hobbyist — they want the app to do the noticing.

---

## 2. The one idea the design has to carry

**Your financial data never leaves your phone.**

This is not a marketing line bolted on afterwards; it is the architecture. Bank
statements are parsed on-device, all analysis is local arithmetic, and the app
works fully offline. AI features are opt-in, and even then only redacted merchant
names and rounded totals ever cross the network.

Most apps in this category require you to hand over bank credentials to an
aggregator. That is the thing we are not doing, and the design should make that
**visible and provable**, not asserted in a privacy policy nobody reads.

Concretely, that means designing for:

- An **egress log** — a plain list of every request that ever left the device,
  with the exact payload, viewable any time.
- A **payload preview** — before the first send of each AI feature, show the
  literal JSON and make the user confirm.
- Import screens that state plainly that the file is being read locally.

Treat these as features to design well, not compliance screens to hide in
settings. They are the reason to choose this app.

---

## 3. Where we actually are

**Built and working on a real device:**

| Screen | State |
|---|---|
| Savings | Real. Totals, duplicates, price rises, subscription list |
| Import | Real. File pick → preview → confirm → result |
| Merchant tagging | Bottom sheet, functional |

**That's it.** Two screens and a sheet. There is no navigation graph — a
two-value enum swaps composables. No accounts management, no transaction list,
no editing, no settings, no onboarding.

The engine underneath is well ahead of the interface: parsing, categorisation,
recurring detection, duplicate and price-rise analysis all work and are tested.
Design is now the constraint, not capability.

**Visual starting point:** Material 3 with dynamic colour, so the palette is
taken from the user's wallpaper. Current screens are plain M3 defaults — usable,
not designed. Dark mode should be treated as the primary case.

---

## 4. Information architecture

### Proposed bottom navigation

Four destinations. These are *places*, chosen because you return to them:

| Tab | Purpose |
|---|---|
| **Home** | What's happening right now — safe to spend, what's due, what needs attention |
| **Savings** | The money-finding hub: subscriptions, duplicates, price rises, fees |
| **Budget** | Envelopes and categories for the current period |
| **Accounts** | Balances, transaction list, import history |

Settings lives behind an icon in the top bar, not in the bottom bar.

**Import is deliberately not a tab.** It is currently taking half the navigation
bar, which is wrong: importing is an occasional task, not somewhere you live. It
should be an action — a FAB or a prominent entry point on Home and Accounts —
reachable in one tap but not permanently occupying navigation.

### Full screen inventory

Everything that needs to exist. `[built]` marks what exists today.

**First run**
- Welcome — what the app does, and the on-device promise
- Add first account
- First import
- Empty state that teaches rather than apologises

**Home**
- Overview — safe-to-spend, upcoming bills, this month at a glance
- Attention feed — new price rise, new duplicate, unusual charge, untagged merchants

**Savings**
- Savings overview `[built]`
- Subscription list
- Subscription detail — full charge history, price over time, next due, how to cancel
- Duplicate detail — the overlap explained, with the maths shown
- Price-rise detail — before/after, when, annual impact
- Fees summary — ATM, overdraft, foreign transaction, annualised
- **What-if simulator** — tick subscriptions to cancel, see the annual saving
- Merchant tagging sheet `[built]`

**Budget**
- Budget overview — envelopes for the period, spent vs allocated
- Category detail — transactions behind a category, trend
- Create/edit budget — including the 50/30/20 preset
- Category management — tree, essential flags
- Rules — merchant → category, applied on import and re-runnable
- Month comparison / period close

**Accounts & transactions**
- Account list — balances, types
- Account detail
- Add/edit account
- Transaction list — search, filter, date range
- Transaction detail / edit — category, notes, tags, mark as transfer
- Bulk categorise
- Split transaction
- Import history — every batch, with **undo**

**Import**
- Source picker — file, screenshot, manual entry
- Column mapping — for CSVs we don't recognise; the hardest screen in the app
- Review & confirm `[built]`
- Result `[built]`
- OCR review — confirm/correct rows read from a screenshot

**Goals** (later phase)
- Goal list
- Trip planner — destination, dates, per-day budget, monthly saving needed
- Sinking funds — car service, insurance, Christmas
- Goal detail with progress

**Settings**
- Privacy & data — **egress log**, encrypted export, delete everything
- AI — bring-your-own key, model choice, per-feature toggles, payload preview
- Security — biometric lock
- Categories & rules
- Appearance
- About

---

## 5. Flows worth designing first

In priority order:

1. **First run → first import → first insight.** The activation moment. If
   someone doesn't reach "you're paying for two music services" in their first
   session, they won't come back. Everything before that insight is friction to
   be minimised.
2. **Import a statement**, including the unhappy path where we don't recognise
   the CSV and the user has to map columns.
3. **Act on a duplicate** — see it, understand it, decide, mark it handled.
4. **Correct a mistake** — recategorise a transaction, fix a wrong merchant tag,
   undo an entire import. Trust depends on being able to fix things.
5. **Set up a budget** from existing spending, rather than from a blank form.
6. **Turn on AI** — including the payload preview consent.

---

## 6. Design principles

These are specific to this app. They are not generic good practice.

### Every number must be traceable
Any figure — an annual total, a saving, a category spend — should be tappable
down to the transactions behind it. This app tells people things about their
money; the moment a number can't be explained, the whole thing becomes
untrustworthy.

### Show uncertainty, never fake confidence
Recurring detection is probabilistic. The engine produces a confidence score and
the UI must reflect it. A subscription we're 60% sure about is still worth
surfacing, but it must not look identical to one we're certain of. Never round a
maybe up to a fact.

### Don't nag
Savings features die by becoming noise. The engine already declines to flag
multiple video-streaming services, because plenty of people subscribe to several
on purpose. Design should hold the same line: surface things that are probably
mistakes, stay quiet about things that are probably choices, and never repeat a
suggestion the user has dismissed.

### Preview before anything irreversible
Importing writes to the ledger. A wrong bank profile produces perfectly
plausible rows with the sign inverted — spending recorded as income — and that's
invisible once it's in. The import preview exists for this reason and should be
designed as a genuine checkpoint, not a formality to tap through.

### Empty states are the product
A new user has no data, so the first thing they see of every screen is its empty
state. These should teach what the screen will do and offer the one action that
fills it — not apologise for being blank.

### Privacy is a screen, not a sentence
See §2. The egress log and payload preview deserve real design attention.

---

## 7. Content and tone

**Plain, specific, and never salesy.** This app makes claims about someone's
money; overstating anything is how it loses them.

| Do | Don't |
|---|---|
| "You're paying for 2 music services" | "You could be saving hundreds!" |
| "Save $146/yr — assumes keeping the cheapest" | "Save $146/yr" with no basis |
| "Netflix went from $15.99 to $17.99 on 15 May" | "Price alert!" |
| "0 added — all 240 were already in your ledger" | "Import failed" |

**Always show the basis of an estimate.** "Assumes keeping the cheapest" is not
small print; it's what makes the number believable.

**Never imply certainty about intent.** We know a charge repeats. We don't know
whether the user wants it. Phrase findings as observations, and let them decide.

---

## 8. Data display rules

These are non-negotiable — they come from how the engine works.

- **Negative is money out**, positive is money in, on every account type. This is
  applied at import and holds everywhere.
- **Amounts are exact.** Money is stored as whole cents; the app never rounds
  mid-calculation. Per-charge figures show exact cents (`$15.99`).
- **Annual and projected figures may round** to whole currency units (`$2,318`)
  — the cents carry no decision-relevant information and hurt legibility.
- **Always show the currency symbol.** Multi-currency is supported and a bare
  number is ambiguous.
- **Cadence must be explicit.** "Every 4 weeks" and "monthly" are different
  things — 13 charges a year versus 12 — and the difference is real money.
- **Dates are the statement's dates**, never timezone-shifted.

---

## 9. Technical constraints

- **Jetpack Compose + Material 3.** Designs should map to M3 components where
  reasonable; bespoke components are fine where they earn it.
- **Dynamic colour is on** — the palette comes from the user's wallpaper, so the
  design cannot depend on specific brand colours. It must work with an arbitrary
  generated palette, in light and dark.
- **Dark mode is the common case.** Design dark first.
- **Target device is large** (Pixel 8 Pro, 1344×2992). Primary actions must be
  reachable one-handed; the top of the screen is not.
- **Everything works offline.** No design may assume connectivity.
- **No account, no login, no sync.** There is no server. Data lives on the device
  and moves only by explicit encrypted export.

---

## 10. Open questions for design

1. **What is Home?** A dashboard, an attention feed, or a "safe to spend"
   number? This decides the app's daily-use character and is the biggest open
   question.
2. **How much do we show at low confidence?** Where's the line between useful
   and noisy?
3. **How does the user dismiss a finding** so it stays dismissed without
   feeling like data was deleted?
4. **Column mapping** is the hardest screen — a table-shaped problem on a phone,
   shown to someone who doesn't know what a column mapping is. Is there a way to
   avoid ever showing it?
5. **How is the savings number framed** — cumulative "found so far", or current
   annualised overlap? The first is more motivating and easier to overstate.
6. **Multi-account** — does the user pick an account per import, or do we detect
   it? Currently everything lands in one auto-created account.
7. **How prominent is the privacy story?** Front and centre as the differentiator,
   or quiet and discoverable for those who care?
