package dev.financemate.ai.egress

import java.time.Instant

/**
 * The record of everything FinanceMate has ever sent off the device.
 *
 * ## Why this is a log and not a counter
 *
 * The app's central claim is that financial data stays on the phone. A claim
 * like that is worth nothing asserted and everything demonstrated, so the app
 * keeps a permanent, user-readable record of every outbound request **including
 * its exact payload**. A sceptical user can read what was sent, compare it to
 * what we said would be sent, and catch us if the two differ.
 *
 * That only works if the log cannot lie by omission, which drives two decisions:
 *
 * 1. An attempt is written **before** the request is sent, not after. A request
 *    that fails, times out, or is interrupted by the process being killed still
 *    leaves a trace. A log that only records successes would quietly hide the
 *    most interesting failures.
 * 2. Recording is not optional at the call site. [RecordingTransport] wraps the
 *    real transport, so there is no code path from a feature to the network that
 *    skips it.
 *
 * The log lives in the encrypted ledger like everything else: payloads are
 * redacted before they are sent, but redacted merchant names are still the
 * user's data and do not deserve weaker storage than the rest.
 */
public interface EgressRecorder {

    /**
     * Records that [attempt] is about to be sent, returning the row id so its
     * outcome can be filled in afterwards.
     */
    public suspend fun recordAttempt(attempt: EgressAttempt): Long

    /** Records how the attempt identified by [id] ended. */
    public suspend fun recordOutcome(id: Long, outcome: EgressOutcome)
}

/**
 * One outbound request, captured before it leaves.
 *
 * @property payload exactly what was sent, verbatim. Storing a summary here
 *   would defeat the point — the user is meant to be able to audit the real
 *   bytes, not our description of them.
 */
public data class EgressAttempt(
    val occurredAt: Instant,
    val featureId: String,
    val endpoint: String,
    val modelId: String,
    val payload: String,
) {
    val payloadBytes: Int get() = payload.toByteArray(Charsets.UTF_8).size
}

/** How a request ended. */
public sealed interface EgressOutcome {

    /** The request completed. [responseBytes] is the size of the text returned. */
    public data class Completed(
        val responseBytes: Int,
        val inputTokens: Long,
        val outputTokens: Long,
    ) : EgressOutcome

    /**
     * The request failed.
     *
     * [reason] carries only an exception class name. It must never include the
     * request body: a failure path that leaked the payload into a log line or a
     * crash report would send financial data to exactly the places the redaction
     * gate exists to keep it out of.
     */
    public data class Failed(val reason: String) : EgressOutcome
}

/**
 * A recorder that keeps nothing.
 *
 * For tests and for the pre-AI build, where no transport exists to wrap. It is
 * deliberately not the default anywhere in production wiring — the default has
 * to be the one that writes to disk, or the guarantee is only as good as
 * somebody remembering to opt in.
 */
public object NoOpEgressRecorder : EgressRecorder {
    override suspend fun recordAttempt(attempt: EgressAttempt): Long = 0L
    override suspend fun recordOutcome(id: Long, outcome: EgressOutcome) = Unit
}
