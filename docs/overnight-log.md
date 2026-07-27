# Overnight session — activation flow

Branch: `overnight/activation-flow`, pushed. `main` untouched.
250 tests, all passing. Debug and release both build; R8 is clean.

```bash
git checkout overnight/activation-flow && ./gradlew :app:installDebug
```

---

## What landed

Five commits, each green and each independently revertable.

| | |
|---|---|
| `ee15653` | Roborazzi screenshot testing |
| `817d52a` | Real navigation graph |
| `9cd6562` | The egress log |
| `4133efc` | Welcome and egress-proof screens |
| `187cc39` | Source picker, checkpoint, result screen |

The activation flow from the designs is now complete and works on hardware:
first run → welcome → (optional proof) → source → checkpoint → import → result →
savings.

Fourteen rendered screens are committed under `app/src/test/screenshots/`, so
tonight's UI can be reviewed without building anything.

---

## The one thing worth arguing about

**The egress log was not in the plan, and it took roughly a third of the night.**

The proof screen in the designs shows a request log with "0 requests since
install" and the sentence *"Every request the app ever makes is recorded here
with its exact payload."* The cheap version is a hardcoded zero. It would have
looked identical in every screenshot and been a lie the moment AI ships — on the
one screen whose entire purpose is to be verifiable.

So the log is real before the screen that displays it. `:ai` gained an
`EgressRecorder` port and a `RecordingTransport`; `:core:data` gained an
`egress_log` table at schema version 2. The DAO offers insert and
complete-an-entry and nothing else: no delete, no general update. A log the app
can edit is not evidence.

If you disagree with spending the time, the revert is one commit and the screen
falls back to needing a constant.

---

## Judgement calls made without you

Each of these was a fork in the road with no answer in the designs or the briefs.

**Copy changed on the proof screen.** The design says the disclosure list is
"generated from the code that is allowed to use the network". It is not
generated — it is a declaration kept beside that code. The screen now says
"declared inside the one module that is allowed to use the network, next to the
code that would do it". Overclaiming there would be self-defeating.

**"6 banks", not the design's 7.** Counted from `BankProfiles.AUTO_DETECTED`.
There are seven profiles, but Wells Fargo's export has no header row, so it can
only be chosen by hand. The number is computed, so it cannot go stale.

**"NOT BUILT YET" on screenshot and manual import.** Both appear in the designs
as live options. Neither exists. They are shown, disabled, with that label —
"Coming soon" would be a promise with no date behind it.

**No column-mapping screen.** Screen 03 names the unrecognised-CSV case but does
not design the mapping UI. `ColumnDetector` already handles detection; designing
that screen is real work, not a 3am improvisation. **This is the largest known
gap in the flow.**

**Import is one navigation route, not three.** The steps are strictly linear and
each replaces the last, so a back stack would be a second, weaker copy of
`ImportUiState`, and the two could disagree. Back is handled explicitly instead:
from the checkpoint it returns to the source picker.

**Onboarding shows once**, tracked in DataStore, deliberately outside the
encrypted ledger — see `OnboardingStore` for why.

---

## Bugs found, and where

Screenshot tests and the device caught different things, which is the argument
for having both.

**Roborazzi caught** the orange budget blowing past its stated 5% (both duplicate
and price-rise cards were filled), a pluralisation bug that only appears with
exactly one untagged merchant, and an orange loading spinner.

**The device caught** three things no screenshot could:

1. Finishing onboarding landed on Savings instead of Import. The DataStore flag
   was being collected as a live flow, so completing onboarding flipped it,
   changed the `NavHost` start destination, rebuilt the graph, and discarded the
   navigation in progress. Now read once with `first()`.
2. Back from that first import quit the app — popping onboarding left Import
   alone on the stack with no floor under it.
3. The result screen's undo link sat under the navigation bar on a Pixel 5.

**A test caught me.** My first `ImportCheckpointBuilder` counted rows repeated
within one file as duplicates. `DedupHasher` already numbers identical rows in
source order so that two $3.75 coffees on the same day stay two transactions —
the forecast was second-guessing a deliberate decision. Caught by the test that
runs the forecast and the import over the same file and asserts they agree.

**A false alarm worth recording**, because it will happen to you too: importing
failed on the device with `IllegalStateException` and I spent a while suspecting
the parser. The cause was my own screenshots. `adb shell screencap -p /sdcard/…`
writes files that MediaStore indexes, which pushed the test CSV down the picker's
"Recent files" list between the screenshot and the tap. I was selecting a PNG.
Use `adb exec-out screencap -p > local.png` instead — nothing touches the device.

That hunt did produce something useful: import failures now log the exception
class and stack frames, deliberately **without** the message, because a parser
message can quote the offending row and logcat is readable by anyone with a
cable.

---

## Known rough edges

- **The checkpoint's count tiles clip** at the scroll boundary on a Pixel 5 — the
  "date range" label is cut mid-word until you scroll. Reachable, but it looks
  like a bug. Fixing it properly means either shrinking the type or dropping a
  section, which is a design decision.
- **UK spelling throughout, US market.** The app says "aeroplane mode",
  "analyse", "normalise", "colour". Android on a US device says "Airplane mode".
  This is consistent with the existing codebase and with the designs, so I left
  it — but it is a decision, not an accident, and it should be made deliberately
  before anyone else sees the app.
- **The savings screen still has nothing to say after a one-month import.**
  Recurring detection needs at least two charges per merchant, so a single
  statement legitimately yields zero findings. The result screen reported
  "0 / 0 / 0" honestly on the test file. That is correct behaviour and a weak
  first-run experience; the product brief's central bet needs a user to import
  more than one month, and nothing currently tells them so.
- **`ImportScreen`'s failure state is plain.** It works and is styled, but it has
  had the least design attention of anything here.

---

## Not done

Budgeting. Home. Accounts management beyond checkpoint selection. PDF and OCR
import. Anything in `:ai` beyond the egress plumbing. Light theme. Column
mapping. Fee detection — the optional Phase 6 — was skipped; the night went into
the egress log instead, which I think was the better trade.

---

## Suggested next

1. **Column mapping.** The largest gap, and the source screen already promises it.
2. **Decide the spelling question**, and do it before there is more copy.
3. **Say something useful after a one-month import** — either ask for more
   history at the checkpoint, or make the result screen honest about why it is
   quiet.
