package dev.financemate.ai.egress

/**
 * What this module is, and is not, permitted to send.
 *
 * The proof screen shows this list. It is declared **here**, beside the only
 * code in the app that can open a socket, rather than written as copy in the UI
 * layer — because a privacy claim that lives in a string resource drifts from
 * the truth the first time someone changes the transport and forgets the
 * marketing. Adding a network call to this module without adding a line here
 * should feel like the omission it is.
 *
 * This is not generated from the code, and pretending otherwise would be the
 * same kind of overclaim the screen exists to avoid. What it is: a declaration
 * kept in the one module that could possibly violate it, reviewed alongside the
 * transport it describes.
 */
public object EgressDisclosure {

    /** The declared handling of each kind of data the app holds. */
    public val items: List<EgressDisclosureItem> = listOf(
        EgressDisclosureItem(
            what = "Your statement file",
            handling = EgressHandling.NEVER,
        ),
        EgressDisclosureItem(
            what = "Amounts, dates, balances",
            handling = EgressHandling.NEVER,
        ),
        EgressDisclosureItem(
            what = "Account numbers",
            handling = EgressHandling.NEVER,
        ),
        EgressDisclosureItem(
            what = "Redacted merchant names, if you turn on AI",
            handling = EgressHandling.OPT_IN,
        ),
    )

    /**
     * Every host this module can reach.
     *
     * One entry, and it is the user's own API key talking to Anthropic directly.
     * There is no FinanceMate server to route through, which is why there is no
     * account to create.
     */
    public val endpoints: List<String> = listOf(RecordingTransport.ANTHROPIC_MESSAGES)
}

public data class EgressDisclosureItem(
    val what: String,
    val handling: EgressHandling,
)

public enum class EgressHandling {
    /** Cannot leave the device. No code path exists that would send it. */
    NEVER,

    /** Leaves only after an explicit, per-feature opt-in with a payload preview. */
    OPT_IN,
}
