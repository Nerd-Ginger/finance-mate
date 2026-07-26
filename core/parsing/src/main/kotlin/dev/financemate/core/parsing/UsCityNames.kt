package dev.financemate.core.parsing

/**
 * City names used to strip trailing locations from bank descriptors.
 *
 * ## Why a list rather than a heuristic
 *
 * "SQ *BLUE BOTTLE COFFEE #47 OAKLAND CA" and the same shop in San Francisco must
 * reduce to the same merchant. Removing the state code is easy; removing the city
 * is not, because there is no structural difference between the trailing city in
 * `SAFEWAY OAKLAND CA` and the trailing word in `SAFEWAY FUEL CA`.
 *
 * The obvious heuristic — "drop the token before the state code" — turns
 * `SAFEWAY CA` into nothing. Matching against known city names instead means we
 * only ever remove a token we can positively identify.
 *
 * ## Coverage
 *
 * This is the ~350 most populous US cities plus the common multi-word ones. It is
 * not exhaustive and does not need to be: an unrecognised city leaves slightly
 * noisier keys for one merchant, which is a far better failure than merging two
 * different merchants together.
 */
internal object UsCityNames {

    /** Longest city name in tokens, so callers know how far back to look. */
    const val MAX_TOKENS: Int = 3

    val NAMES: Set<String> = setOf(
        // Multi-word first (matched greedily by the caller).
        "NEW YORK", "LOS ANGELES", "SAN ANTONIO", "SAN DIEGO", "SAN JOSE",
        "SAN FRANCISCO", "FORT WORTH", "EL PASO", "OKLAHOMA CITY", "LAS VEGAS",
        "KANSAS CITY", "LONG BEACH", "VIRGINIA BEACH", "COLORADO SPRINGS",
        "SANTA ANA", "CORPUS CHRISTI", "ST LOUIS", "SAINT LOUIS", "ST PAUL",
        "SAINT PAUL", "ST PETERSBURG", "SAINT PETERSBURG", "NEW ORLEANS",
        "SANTA CLARITA", "GARDEN GROVE", "OVERLAND PARK", "SIOUX FALLS",
        "HUNTINGTON BEACH", "GRAND RAPIDS", "SALT LAKE CITY", "HUNTSVILLE",
        "GRAND PRAIRIE", "NEWPORT NEWS", "LITTLE ROCK", "MORENO VALLENO",
        "MORENO VALLEY", "SANTA ROSA", "SIOUX CITY", "JERSEY CITY",
        "CHULA VISTA", "NORTH LAS VEGAS", "WEST VALLEY CITY", "SANTA CLARA",
        "CEDAR RAPIDS", "ELK GROVE", "PALM BAY", "FORT LAUDERDALE",
        "SOUTH BEND", "BATON ROUGE", "DES MOINES", "FORT COLLINS",
        "WEST PALM BEACH", "SANTA MONICA", "DALY CITY", "MOUNTAIN VIEW",
        "REDWOOD CITY", "UNION CITY", "SAN MATEO", "SAN RAFAEL", "SAN BRUNO",
        "SAN LEANDRO", "SAN RAMON", "SAN MARCOS", "SAN BERNARDINO",
        "SANTA BARBARA", "SANTA CRUZ", "SANTA FE", "SIMI VALLEY",
        "THOUSAND OAKS", "COSTA MESA", "COLLEGE STATION", "COLLEGE PARK",
        "ROUND ROCK", "SUGAR LAND", "LEAGUE CITY", "MISSOURI CITY",
        "BOWLING GREEN", "ANN ARBOR", "BOCA RATON", "CORAL SPRINGS",
        "POMPANO BEACH", "MIAMI BEACH", "DAYTONA BEACH", "PALM SPRINGS",
        "BROKEN ARROW", "ROCK HILL", "MYRTLE BEACH", "CHAPEL HILL",
        "WINSTON SALEM", "HIGH POINT", "NEW HAVEN", "NEW BRITAIN",
        "WHITE PLAINS", "YUBA CITY", "OCEAN CITY", "ATLANTIC CITY",
        "IOWA CITY", "TRAVERSE CITY", "RAPID CITY", "CARSON CITY",
        "JOHNSON CITY", "TEMPLE TERRACE", "PORT ST LUCIE", "CAPE CORAL",

        // Single-word.
        "CHICAGO", "HOUSTON", "PHOENIX", "PHILADELPHIA", "DALLAS", "AUSTIN",
        "JACKSONVILLE", "COLUMBUS", "CHARLOTTE", "INDIANAPOLIS", "SEATTLE",
        "DENVER", "BOSTON", "NASHVILLE", "DETROIT", "PORTLAND", "MEMPHIS",
        "LOUISVILLE", "MILWAUKEE", "BALTIMORE", "ALBUQUERQUE", "TUCSON",
        "FRESNO", "SACRAMENTO", "MESA", "ATLANTA", "OMAHA", "RALEIGH",
        "MIAMI", "OAKLAND", "MINNEAPOLIS", "TULSA", "WICHITA", "ARLINGTON",
        "TAMPA", "AURORA", "ANAHEIM", "HONOLULU", "RIVERSIDE", "BAKERSFIELD",
        "STOCKTON", "CINCINNATI", "PITTSBURGH", "ANCHORAGE", "TOLEDO",
        "GREENSBORO", "PLANO", "NEWARK", "LINCOLN", "ORLANDO", "IRVINE",
        "CHANDLER", "LAREDO", "DURHAM", "MADISON", "LUBBOCK", "WINSTON",
        "GILBERT", "GLENDALE", "RENO", "HIALEAH", "CHESAPEAKE", "SCOTTSDALE",
        "IRVING", "FREMONT", "BOISE", "RICHMOND", "SPOKANE", "MODESTO",
        "FONTANA", "TACOMA", "OXNARD", "FAYETTEVILLE", "MONTGOMERY",
        "SHREVEPORT", "AKRON", "AMARILLO", "GLENDALE", "MOBILE", "KNOXVILLE",
        "WORCESTER", "TEMPE", "BROWNSVILLE", "PEORIA", "SALEM", "ONTARIO",
        "EUGENE", "MCKINNEY", "SPRINGFIELD", "PASADENA", "ROCKFORD", "PATERSON",
        "SAVANNAH", "TORRANCE", "BRIDGEPORT", "MCALLEN", "MESQUITE", "SYRACUSE",
        "MIDLAND", "PASSAIC", "KILLEEN", "HAMPTON", "PROVIDENCE", "DAYTON",
        "CARROLLTON", "CHARLESTON", "ROSEVILLE", "DENTON", "SURPRISE",
        "STAMFORD", "ABILENE", "ALLENTOWN", "NORMAN", "BEAUMONT", "WACO",
        "COLUMBIA", "MURFREESBORO", "LAFAYETTE", "OLATHE", "BELLEVUE",
        "CLARKSVILLE", "PROVO", "EVANSVILLE", "BERKELEY", "EVERETT", "ARVADA",
        "PUEBLO", "LANSING", "FLINT", "GRESHAM", "CONCORD", "BILLINGS",
        "ANTIOCH", "FAIRFIELD", "VALLEJO", "VENTURA", "CLEARWATER", "INGLEWOOD",
        "BURBANK", "COMPTON", "CARLSBAD", "TEMECULA", "MURRIETA", "ESCONDIDO",
        "SUNNYVALE", "HAYWARD", "POMONA", "PALMDALE", "LANCASTER", "CORONA",
        "SALINAS", "ELGIN", "NAPERVILLE", "JOLIET", "CICERO", "EVANSTON",
        "BLOOMINGTON", "KENOSHA", "APPLETON", "GREENVILLE", "SPARTANBURG",
        "AUGUSTA", "MACON", "ALBANY", "BUFFALO", "ROCHESTER", "YONKERS",
        "SCHENECTADY", "UTICA", "TRENTON", "CAMDEN", "WILMINGTON", "DOVER",
        "HARTFORD", "WATERBURY", "NORWALK", "DANBURY", "MANCHESTER", "NASHUA",
        "BURLINGTON", "PORTSMOUTH", "SCRANTON", "ERIE", "BETHLEHEM", "READING",
        "LANCASTER", "HARRISBURG", "ANNAPOLIS", "ROCKVILLE", "BETHESDA",
        "ALEXANDRIA", "ARLINGTON", "NORFOLK", "ROANOKE", "LYNCHBURG",
        "ASHEVILLE", "CARY", "WILMINGTON", "GAINESVILLE", "OCALA", "SARASOTA",
        "NAPLES", "BRADENTON", "LAKELAND", "KISSIMMEE", "HOLLYWOOD",
        "PEMBROKE", "MIRAMAR", "SUNRISE", "PLANTATION", "DAVIE", "WESTON",
        "JUPITER", "STUART", "MELBOURNE", "TITUSVILLE", "LEESBURG", "SANFORD",
        "MARIETTA", "ROSWELL", "ALPHARETTA", "SMYRNA", "DECATUR", "DULUTH",
        "CHATTANOOGA", "JACKSON", "BILOXI", "GULFPORT", "HATTIESBURG",
        "MONROE", "ALEXANDRIA", "KENNER", "METAIRIE", "SLIDELL", "HOUMA",
        "TYLER", "LONGVIEW", "ODESSA", "GALVESTON", "CONROE", "PEARLAND",
        "BAYTOWN", "TEXARKANA", "SHERMAN", "TEMPLE", "BRYAN", "VICTORIA",
        "EDINBURG", "HARLINGEN", "PHARR", "MISSION", "WESLACO", "ELPASO",
        "FLAGSTAFF", "YUMA", "PRESCOTT", "SEDONA", "HENDERSON", "SPARKS",
        "OGDEN", "OREM", "SANDY", "LAYTON", "BOZEMAN", "MISSOULA", "HELENA",
        "CHEYENNE", "CASPER", "FARGO", "BISMARCK", "DULUTH", "ROCHESTER",
        "BLOOMINGTON", "EDINA", "MINNETONKA", "PLYMOUTH", "MAPLEWOOD",
        "TOPEKA", "LAWRENCE", "MANHATTAN", "SALINA", "HUTCHINSON",
        "SPRINGDALE", "BENTONVILLE", "CONWAY", "JONESBORO", "PADUCAH",
        "LEXINGTON", "OWENSBORO", "FRANKFORT", "KETTERING", "PARMA",
        "CANTON", "YOUNGSTOWN", "LORAIN", "ELYRIA", "MANSFIELD", "MARION",
        "MUNCIE", "KOKOMO", "ANDERSON", "ELKHART", "HAMMOND", "GARY",
        "KALAMAZOO", "SAGINAW", "PONTIAC", "TROY", "LIVONIA", "DEARBORN",
        "WARREN", "STERLING", "NOVI", "TAYLOR", "WYOMING", "HOLLAND",
    )
}
