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

    /**
     * The whole import flow: source, checkpoint, result.
     *
     * One route rather than three. The steps are strictly linear and each
     * replaces the last, so a back stack would only be a second, weaker copy of
     * `ImportUiState` — and the two could then disagree, which is how a user
     * ends up looking at a checkpoint for a file that has already been imported.
     * `ImportScreen` handles back explicitly instead.
     */
    @Serializable
    object ImportSource
}
