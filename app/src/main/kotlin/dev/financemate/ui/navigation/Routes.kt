package dev.financemate.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Every place the app can be.
 *
 * Type-safe routes rather than strings: a typo in a route name is a compile
 * error instead of a crash the first time somebody taps the wrong thing.
 *
 * The split into graphs matters. Onboarding is a separate top-level graph so it
 * can be dropped from the back stack entirely once finished — a user who has
 * just imported their first statement must not be able to press back into the
 * welcome screen. Import is a graph rather than a single route because it is a
 * multi-step flow with its own internal back behaviour.
 */
object Routes {

    // --- Onboarding. Shown once; landed in Phase 3. -------------------------

    @Serializable
    object Onboarding

    @Serializable
    object Welcome

    @Serializable
    object EgressProof

    // --- The places you return to. ------------------------------------------

    @Serializable
    object Main

    @Serializable
    object Savings

    // --- Import: a task, not a place. ---------------------------------------

    @Serializable
    object Import

    /** Choose where the transactions are coming from. */
    @Serializable
    object ImportSource

    /** Review what was parsed before anything is written. */
    @Serializable
    object ImportCheckpoint

    /** What changed, with undo. */
    @Serializable
    object ImportResult
}
