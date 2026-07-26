package dev.financemate.feature.insight.subscription

import dev.financemate.core.model.MerchantKey

/**
 * Decides what kind of service a merchant provides.
 *
 * Exists so the built-in catalogue can be overridden. The catalogue can only
 * ever cover the services it happens to list — regional providers, niche tools,
 * and anything launched after it was written are all invisible to it — and the
 * user is the authority on their own subscriptions.
 */
public fun interface MerchantClassifier {
    public fun classify(merchant: MerchantKey): ServiceClass?
}

/** The built-in catalogue, as a classifier. */
public val BuiltInClassifier: MerchantClassifier = MerchantClassifier { ServiceCatalogue.classify(it) }

/**
 * What the user has said about a merchant.
 *
 * The [NotAService] case is the reason this is a type rather than a nullable
 * [ServiceClass]. "I have not classified this" and "this is definitely not a
 * subscription service" need to be different states: without the distinction,
 * dismissing a bad suggestion is impossible, because the classifier would fall
 * straight back to the catalogue and suggest it again.
 */
public sealed interface MerchantOverride {

    /** The user assigned this merchant to a service class. */
    public data class Classified(val serviceClass: ServiceClass) : MerchantOverride

    /**
     * The user said this is not a subscription service.
     *
     * Suppresses the built-in catalogue, so a merchant the catalogue guessed
     * wrong stays dismissed instead of reappearing after every import.
     */
    public data object NotAService : MerchantOverride
}

/**
 * Applies user overrides on top of a fallback classifier.
 *
 * The user always wins. If they have said a merchant is a gym, it is a gym, even
 * if the built-in catalogue disagrees — they can see their own bank statement
 * and the catalogue is guessing from a name.
 */
public class OverridingMerchantClassifier(
    private val overrides: Map<MerchantKey, MerchantOverride>,
    private val fallback: MerchantClassifier = BuiltInClassifier,
) : MerchantClassifier {

    override fun classify(merchant: MerchantKey): ServiceClass? =
        when (val override = overrides[merchant]) {
            is MerchantOverride.Classified -> override.serviceClass
            MerchantOverride.NotAService -> null
            null -> fallback.classify(merchant)
        }

    /** True when the user has expressed an opinion about [merchant]. */
    public fun isOverridden(merchant: MerchantKey): Boolean = overrides.containsKey(merchant)
}
