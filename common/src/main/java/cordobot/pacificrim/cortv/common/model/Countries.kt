package cordobot.pacificrim.cortv.common.model

import java.util.Locale

// iptv-org uses "UK" for the United Kingdom; ISO 3166 (and java.util.Locale) uses "GB"
private fun isoCode(code: String) = if (code.equals("UK", ignoreCase = true)) "GB" else code.uppercase(Locale.ROOT)

/** Name of the country in the given language, e.g. "ES" -> "España". */
fun countryName(code: String, locale: Locale = Locale.getDefault()): String =
    runCatching { Locale.Builder().setRegion(isoCode(code)).build().getDisplayCountry(locale) } // invalid codes throw
        .getOrNull()?.ifEmpty { null } ?: code

/** Flag emoji for a country code, e.g. "ES" -> 🇪🇸. */
fun countryFlag(code: String): String {
    val iso = isoCode(code)
    if (iso.length != 2 || !iso.all { it in 'A'..'Z' }) return ""
    return iso.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

private val LATIN_NAME = Regex("(?i)latin|latam")
private val BRAZIL_NAME = Regex("(?i)brazil|brasil")

/** Whether the channel comes from [country]. */
fun MediaItem.isFrom(country: String): Boolean = this.country != null && isoCode(this.country) == isoCode(country)

/**
 * Whether a live channel is offered to viewers in [country] (null = every country): channels from
 * that country, plus US channels aimed at Latin America ("Latino", "Latin", "LATAM"). Their
 * Brazilian feeds are in Portuguese, so those only show for Brazil.
 */
fun MediaItem.isAvailableIn(country: String?): Boolean = when {
    country == null -> true
    this.country == null -> false
    isFrom(country) -> true
    else -> isFrom("US") && LATIN_NAME.containsMatchIn(title) &&
        (isoCode(country) == "BR" || !BRAZIL_NAME.containsMatchIn(title))
}
