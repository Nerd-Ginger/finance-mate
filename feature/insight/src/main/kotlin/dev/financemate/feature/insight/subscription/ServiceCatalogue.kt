package dev.financemate.feature.insight.subscription

import dev.financemate.core.model.MerchantKey

/**
 * What kind of service a merchant provides.
 *
 * Used to spot two subscriptions doing the same job. The classes are drawn
 * around **substitutability** — services a household would plausibly keep only
 * one of — rather than around industry sectors. Two video streaming services is
 * a normal choice; two cloud-storage plans or two password managers almost never
 * is.
 */
public enum class ServiceClass(
    public val displayName: String,
    /**
     * Whether holding several is ordinarily redundant.
     *
     * Video streaming is deliberately *not* redundant: plenty of people
     * subscribe to several on purpose, and nagging them about it is how a
     * savings feature becomes noise the user switches off. Those are surfaced
     * as information, not as a problem to fix.
     */
    public val usuallyRedundant: Boolean,
) {
    VIDEO_STREAMING("Video streaming", usuallyRedundant = false),
    MUSIC_STREAMING("Music streaming", usuallyRedundant = true),
    CLOUD_STORAGE("Cloud storage", usuallyRedundant = true),
    PASSWORD_MANAGER("Password manager", usuallyRedundant = true),
    VPN("VPN", usuallyRedundant = true),
    FITNESS("Gym or fitness", usuallyRedundant = true),
    NEWS("News or magazines", usuallyRedundant = false),
    GAMING("Gaming", usuallyRedundant = false),
    MEAL_KIT("Meal kits", usuallyRedundant = true),
    AUDIOBOOKS("Audiobooks", usuallyRedundant = true),
    PHONE("Mobile plan", usuallyRedundant = true),
    INTERNET("Home internet", usuallyRedundant = true),
    INSURANCE("Insurance", usuallyRedundant = false),
    SOFTWARE("Software", usuallyRedundant = false),
    ;
}

/**
 * Maps merchants onto [ServiceClass].
 *
 * Matching is on the exact normalised merchant key, not a fuzzy contains-check.
 * A substring match would classify "AMERICAN EXPRESS" as containing "EXPRESS"
 * and pair it with a VPN, and a wrong duplicate-subscription claim is worse than
 * a missing one: the user acts on it, cancels something, and stops believing
 * the app.
 *
 * The list is inevitably incomplete. Unknown merchants simply have no class and
 * are never reported as duplicates.
 */
public object ServiceCatalogue {

    private val CLASSIFICATIONS: Map<String, ServiceClass> = buildMap {
        listOf(
            "NETFLIX", "HULU", "DISNEY PLUS", "DISNEYPLUS", "HBO MAX", "MAX",
            "PARAMOUNT PLUS", "PEACOCK", "APPLE TV", "STARZ", "SHOWTIME",
            "CRUNCHYROLL", "MUBI", "BRITBOX", "SLING TV", "FUBOTV", "YOUTUBE TV",
        ).forEach { put(it, ServiceClass.VIDEO_STREAMING) }

        listOf(
            "SPOTIFY", "APPLE MUSIC", "YOUTUBE PREMIUM", "TIDAL", "DEEZER",
            "PANDORA", "AMAZON MUSIC", "SIRIUSXM",
        ).forEach { put(it, ServiceClass.MUSIC_STREAMING) }

        listOf(
            "DROPBOX", "GOOGLE STORAGE", "GOOGLE ONE", "ICLOUD", "APPLE ICLOUD",
            "ONEDRIVE", "MICROSOFT ONEDRIVE", "BOX", "BACKBLAZE", "SYNC COM",
            "PCLOUD", "MEGA",
        ).forEach { put(it, ServiceClass.CLOUD_STORAGE) }

        listOf(
            "1PASSWORD", "LASTPASS", "DASHLANE", "BITWARDEN", "KEEPER SECURITY",
        ).forEach { put(it, ServiceClass.PASSWORD_MANAGER) }

        listOf(
            "NORDVPN", "EXPRESSVPN", "SURFSHARK", "PROTONVPN", "MULLVAD",
            "PRIVATE INTERNET ACCESS", "CYBERGHOST",
        ).forEach { put(it, ServiceClass.VPN) }

        listOf(
            "PLANET FITNESS", "EQUINOX", "LA FITNESS", "GOLDS GYM", "ANYTIME FITNESS",
            "ORANGETHEORY", "CLASSPASS", "PELOTON", "CRUNCH FITNESS", "24 HOUR FITNESS",
            "LIFE TIME", "YMCA",
        ).forEach { put(it, ServiceClass.FITNESS) }

        listOf(
            "NEW YORK TIMES", "NYTIMES", "WASHINGTON POST", "WALL STREET JOURNAL",
            "THE ATHLETIC", "THE ECONOMIST", "MEDIUM", "SUBSTACK", "THE GUARDIAN",
        ).forEach { put(it, ServiceClass.NEWS) }

        listOf(
            "XBOX GAME PASS", "PLAYSTATION PLUS", "NINTENDO SWITCH ONLINE",
            "STEAM", "EA PLAY", "UBISOFT PLUS",
        ).forEach { put(it, ServiceClass.GAMING) }

        listOf(
            "HELLOFRESH", "BLUE APRON", "HOME CHEF", "FACTOR", "GREEN CHEF",
            "MARLEY SPOON", "SUNBASKET",
        ).forEach { put(it, ServiceClass.MEAL_KIT) }

        listOf("AUDIBLE", "SCRIBD", "EVERAND", "LIBRO FM")
            .forEach { put(it, ServiceClass.AUDIOBOOKS) }

        listOf(
            "VERIZON", "AT&T", "ATT", "T MOBILE", "TMOBILE", "MINT MOBILE",
            "VISIBLE", "GOOGLE FI", "CRICKET WIRELESS", "BOOST MOBILE",
        ).forEach { put(it, ServiceClass.PHONE) }

        listOf("COMCAST", "XFINITY", "SPECTRUM", "COX COMMUNICATIONS", "CENTURYLINK", "FRONTIER")
            .forEach { put(it, ServiceClass.INTERNET) }

        listOf(
            "GEICO", "PROGRESSIVE", "STATE FARM", "ALLSTATE", "LEMONADE",
            "USAA", "LIBERTY MUTUAL",
        ).forEach { put(it, ServiceClass.INSURANCE) }

        listOf(
            "ADOBE", "MICROSOFT 365", "OFFICE 365", "GITHUB", "NOTION",
            "FIGMA", "SLACK", "ZOOM", "CANVA", "GRAMMARLY",
        ).forEach { put(it, ServiceClass.SOFTWARE) }
    }

    public fun classify(merchant: MerchantKey): ServiceClass? = CLASSIFICATIONS[merchant.value]

    public fun isKnown(merchant: MerchantKey): Boolean = CLASSIFICATIONS.containsKey(merchant.value)

    /** Every merchant known to belong to [serviceClass]. Exposed for tests and settings UI. */
    public fun merchantsIn(serviceClass: ServiceClass): Set<String> =
        CLASSIFICATIONS.filterValues { it == serviceClass }.keys
}
