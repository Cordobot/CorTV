package cordobot.pacificrim.cortv.common.parser

import cordobot.pacificrim.cortv.common.model.CastMember
import cordobot.pacificrim.cortv.common.model.Episode
import cordobot.pacificrim.cortv.common.model.MediaItem
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Result of loading a playlist: the items to show and, if the remote list failed, why. */
data class PlaylistResult(
    val items: List<MediaItem>,
    val error: String? = null
)

object M3UParser {

    // Default sample M3U URL containing free and public domain streams
    const val DEFAULT_M3U_URL = "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us.m3u"

    // Public list of free live TV channels from around the world (https://github.com/iptv-org/iptv)
    const val IPTV_ORG_URL = "https://iptv-org.github.io/iptv/index.m3u"

    private const val WIKI_THUMB = "https://thumb.wikimedia.org/wikipedia/commons/thumb"

    private val SINTEL_CAST = listOf(
        CastMember("Halina Reijn", "Sintel (voz)", "$WIKI_THUMB/d/da/Halina_Reijn-66123_%28cropped%29.jpg/250px-Halina_Reijn-66123_%28cropped%29.jpg"),
        CastMember("Thom Hoffman", "Chamán (voz)", "$WIKI_THUMB/3/3c/Thom_Hoffman_at_the_symposium_Photography_Research_in_the_Digital_Age.jpg/250px-Thom_Hoffman_at_the_symposium_Photography_Research_in_the_Digital_Age.jpg")
    )

    private val TEARS_OF_STEEL_CAST = listOf(
        CastMember("Derek de Lint", photoUrl = "$WIKI_THUMB/6/64/Derek-de-lint-1340646708.jpg/250px-Derek-de-lint-1340646708.jpg"),
        CastMember("Sergio Hasselbaink"),
        CastMember("Rogier Schippers"),
        CastMember("Vanja Rukavina"),
        CastMember("Denise Rebergen"),
        CastMember("Jody Bhe"),
        CastMember("Chris Haley")
    )

    private val STAR_TREK_TNG_CAST = listOf(
        CastMember("Patrick Stewart", "Jean-Luc Picard", "$WIKI_THUMB/8/87/Patrick_Stewart_by_Gage_Skidmore_2.jpg/250px-Patrick_Stewart_by_Gage_Skidmore_2.jpg"),
        CastMember("Jonathan Frakes", "William Riker", "$WIKI_THUMB/0/09/Jonathan_Frakes_at_Galaxy_Con_Columbus.jpg/250px-Jonathan_Frakes_at_Galaxy_Con_Columbus.jpg"),
        CastMember("Brent Spiner", "Data", "$WIKI_THUMB/3/30/Brent_Spiner_at_GalaxyCon_San_Jose%2C_August%2C_30%2C_2026_197.jpg/250px-Brent_Spiner_at_GalaxyCon_San_Jose%2C_August%2C_30%2C_2026_197.jpg"),
        CastMember("Gates McFadden", "Beverly Crusher", "$WIKI_THUMB/8/88/Gates_McFadden_Photo_Op_GalaxyCon_Raleigh_2025.jpg/250px-Gates_McFadden_Photo_Op_GalaxyCon_Raleigh_2025.jpg"),
        CastMember("Marina Sirtis", "Deanna Troi", "$WIKI_THUMB/0/02/Marina_Sirtis_Photo_Op_GalaxyCon_Raleigh_2023.jpg/250px-Marina_Sirtis_Photo_Op_GalaxyCon_Raleigh_2023.jpg"),
        CastMember("LeVar Burton", "Geordi La Forge", "$WIKI_THUMB/3/35/LeVar_Burton_Photo_Op_GalaxyCon_Raleigh_2023.jpg/250px-LeVar_Burton_Photo_Op_GalaxyCon_Raleigh_2023.jpg"),
        CastMember("Michael Dorn", "Worf", "$WIKI_THUMB/c/c1/Michael_Dorn_Photo_Op_GalaxyCon_San_Jose_2024_%28cropped%29.jpg/250px-Michael_Dorn_Photo_Op_GalaxyCon_San_Jose_2024_%28cropped%29.jpg"),
        CastMember("Denise Crosby", "Tasha Yar", "$WIKI_THUMB/4/4d/Denise_Crosby_%2853373315035%29.jpg/250px-Denise_Crosby_%2853373315035%29.jpg"),
        CastMember("Wil Wheaton", "Wesley Crusher", "$WIKI_THUMB/8/88/Wil_Wheaton_-_LATFOB_2026-02.jpg/250px-Wil_Wheaton_-_LATFOB_2026-02.jpg")
    )

    // Pre-configured public demo streams so the app is functional out of the box.
    // Metadata (cast, director, dates, ratings) is real; ratings are reference values and
    // Metacritic is left empty because these open shorts were never reviewed there.
    val DEMO_STREAMS = listOf(
        MediaItem(
            id = "dw_espanol",
            country = "DE",
            title = "DW Español",
            url = "https://dwamdstream104.akamaized.net/hls/live/2015530/dwstream104/index.m3u8",
            category = "Canales de TV",
            logoUrl = "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=500",
            description = "Noticias internacionales, reportajes y documentales de Deutsche Welle en español, en vivo las 24 horas.",
            genre = "Noticias"
        ),
        MediaItem(
            id = "dw_english",
            country = "DE",
            title = "DW English",
            url = "https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/index.m3u8",
            category = "Canales de TV",
            logoUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=500",
            description = "La señal internacional en inglés de Deutsche Welle: noticias globales, economía, cultura y ciencia en directo.",
            genre = "Noticias"
        ),
        MediaItem(
            id = "cbs_news",
            country = "US",
            title = "CBS News 24/7",
            url = "https://cbsn-us.cbsnstream.cbsnews.com/out/v1/55a8648e8f134e82a470f83d562deeca/master.m3u8",
            category = "Canales de TV",
            logoUrl = "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=500",
            description = "Canal de noticias en vivo de CBS News con la última hora de Estados Unidos y el mundo, en inglés.",
            genre = "Noticias"
        ),
        MediaItem(
            id = "redbull_tv",
            country = "AT",
            title = "Red Bull TV",
            url = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
            category = "Canales de TV",
            logoUrl = "https://images.unsplash.com/photo-1517649763962-0c623066013b?w=500",
            description = "Deportes extremos, aventura, música, juegos y entretenimiento en vivo desde cualquier parte del mundo.",
            genre = "Deportes extremos"
        ),
        MediaItem(
            id = "tears_of_steel",
            title = "Tears of Steel",
            url = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
            category = "Peliculas",
            logoUrl = "https://mango.blender.org/wp-content/uploads/2013/05/01_thom_celia_bridge.jpg",
            description = "En un Ámsterdam futuro y distópico, un grupo de guerreros y científicos se reúne en la Oude Kerk en un intento desesperado por salvar al mundo de unos robots destructivos.",
            genre = "Ciencia ficción, Acción",
            director = "Ian Hubert",
            releaseDate = "2012-09-26",
            imdbRating = 5.5,
            cast = TEARS_OF_STEEL_CAST
        ),
        MediaItem(
            id = "sintel_trailer",
            originalTitle = "Sintel",
            title = "Sintel (Tráiler)",
            url = "https://download.blender.org/durian/trailer/sintel_trailer-720p.mp4",
            category = "Peliculas",
            logoUrl = "https://durian.blender.org/wp-content/uploads/2010/06/05.8b_comp_000272.jpg",
            description = "Tráiler de Sintel: una joven solitaria emprende un largo viaje por tierras heladas y ciudades en ruinas para encontrar a Scales, el pequeño dragón que una vez rescató.",
            genre = "Animación, Fantasía",
            director = "Colin Levy",
            releaseDate = "2010-09-30",
            imdbRating = 7.4,
            cast = SINTEL_CAST
        ),
        MediaItem(
            id = "big_buck_bunny",
            isKids = true,
            title = "Big Buck Bunny",
            url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            category = "Animes",
            logoUrl = "https://peach.blender.org/wp-content/uploads/title_anouncement.jpg",
            description = "Un conejo gigante y bonachón decide darles una lección a tres roedores traviesos que arruinan su tranquilo día en el bosque. Cortometraje sin diálogos.",
            genre = "Animación, Comedia",
            director = "Sacha Goedegebure",
            releaseDate = "2008-04-10",
            imdbRating = 6.4
        ),
        MediaItem(
            id = "sintel_full",
            originalTitle = "Sintel",
            title = "Sintel (con subtítulos)",
            url = "https://storage.googleapis.com/shaka-demo-assets/sintel-mp4-wvtt/dash.mpd",
            category = "Animes",
            logoUrl = "$WIKI_THUMB/8/8f/Sintel_poster.jpg/500px-Sintel_poster.jpg",
            description = "Sintel recorre un mundo hostil en busca de Scales, el bebé dragón al que cuidó y que le fue arrebatado. Incluye subtítulos en español, inglés y otros idiomas.",
            genre = "Animación, Fantasía",
            director = "Colin Levy",
            releaseDate = "2010-09-30",
            imdbRating = 7.4,
            cast = SINTEL_CAST
        ),
        MediaItem(
            id = "caminandes",
            isKids = true,
            title = "Caminandes",
            url = "https://archive.org/download/Caminandes1LlamaDrama/01_llama_drama_1080p.mp4",
            category = "Series",
            logoUrl = "$WIKI_THUMB/6/61/Pablo_Vazquez_-_Caminandes_-_Episode_1_-_Llama_Drama_-_Cover_thumbnail.png/500px-Pablo_Vazquez_-_Caminandes_-_Episode_1_-_Llama_Drama_-_Cover_thumbnail.png",
            description = "Serie animada de la Fundación Blender sobre Koro, un joven guanaco de la Patagonia que intenta superar los obstáculos más absurdos, inspirada en los dibujos animados de Chuck Jones.",
            genre = "Animación, Comedia",
            director = "Pablo Vazquez",
            releaseDate = "2013",
            episodes = listOf(
                Episode(
                    id = "caminandes_s1e1",
                    season = 1,
                    number = 1,
                    title = "Llama Drama",
                    url = "https://archive.org/download/Caminandes1LlamaDrama/01_llama_drama_1080p.mp4",
                    description = "Koro tiene problemas para cruzar una carretera aparentemente desierta, algo que a un despistado armadillo no le cuesta nada.",
                    releaseDate = "2013",
                    thumbnailUrl = "$WIKI_THUMB/6/61/Pablo_Vazquez_-_Caminandes_-_Episode_1_-_Llama_Drama_-_Cover_thumbnail.png/500px-Pablo_Vazquez_-_Caminandes_-_Episode_1_-_Llama_Drama_-_Cover_thumbnail.png"
                ),
                Episode(
                    id = "caminandes_s1e2",
                    season = 1,
                    number = 2,
                    title = "Gran Dillama",
                    url = "https://archive.org/download/Caminandes2GranDillama/02_gran_dillama_1080p.mp4",
                    description = "Koro busca comida al otro lado de una cerca y vuelve a inspirarse en el armadillo, esta vez con un resultado electrizante.",
                    releaseDate = "2013",
                    thumbnailUrl = "$WIKI_THUMB/a/a8/Blender_Foundation_-_Caminandes_-_Episode_2_-_Gran_Dillama_-_Cover_thumbnail.png/500px-Blender_Foundation_-_Caminandes_-_Episode_2_-_Gran_Dillama_-_Cover_thumbnail.png"
                ),
                Episode(
                    id = "caminandes_s1e3",
                    season = 1,
                    number = 3,
                    title = "Llamigos",
                    url = "https://archive.org/download/CaminandesLlamigos/Caminandes_%20Llamigos-1080p.mp4",
                    description = "En pleno invierno, Koro conoce a Oti, un pingüino de Magallanes bebé bastante molesto, y ambos se enfrentan en una épica batalla por unas sabrosas bayas rojas.",
                    releaseDate = "2016",
                    thumbnailUrl = "$WIKI_THUMB/a/aa/Blender_Foundation_-_Caminandes_-_Episode_3_-_Llamigos_-_Cover_thumbnail.png/500px-Blender_Foundation_-_Caminandes_-_Episode_3_-_Llamigos_-_Cover_thumbnail.png"
                )
            )
        ),
        MediaItem(
            id = "star_trek_tng",
            originalTitle = "Star Trek: The Next Generation",
            title = "Star Trek: La nueva generación",
            url = "https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8",
            category = "Series",
            logoUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500",
            description = "El capitán Jean-Luc Picard y la tripulación de la USS Enterprise exploran la galaxia en el siglo XXIV. Audio doblado en español, inglés, francés, alemán e italiano.",
            genre = "Ciencia ficción",
            releaseDate = "1987",
            cast = STAR_TREK_TNG_CAST,
            episodes = listOf(
                Episode(
                    id = "star_trek_tng_s1e14",
                    season = 1,
                    number = 14,
                    title = "Angel One",
                    url = "https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8",
                    description = "La Enterprise viaja a Angel One, un planeta gobernado por mujeres, para buscar a los supervivientes de un carguero desaparecido.",
                    director = "Michael Rhodes",
                    releaseDate = "1988-01-25"
                )
            )
        )
    )

    fun parse(m3uContent: String): List<MediaItem> = parseEntries(m3uContent, liveOnly = false).ifEmpty { DEMO_STREAMS }

    /**
     * Parses the entries of an M3U playlist. With [liveOnly] every entry is a live channel and its
     * group-title is only used as genre (iptv-org groups such as "Movies" are channels, not films).
     */
    private fun parseEntries(m3uContent: String, liveOnly: Boolean): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val lines = m3uContent.lineSequence().iterator()

        var currentTitle = ""
        var currentLogoUrl: String? = null
        var currentCategory = "Canales de TV"
        var currentId = ""
        var currentGenre: String? = null
        var currentUserAgent: String? = null
        var currentReferrer: String? = null

        while (lines.hasNext()) {
            val line = lines.next().trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:")) {
                // Parse EXTINF metadata
                currentTitle = line.substringAfterLast(",").trim()

                // Parse attributes: tvg-logo, group-title, tvg-id
                currentLogoUrl = parseAttribute(line, "tvg-logo")

                val groupTitle = parseAttribute(line, "group-title") ?: parseAttribute(line, "tvg-name")
                currentCategory = if (liveOnly) "Canales de TV" else determineCategory(groupTitle, currentTitle)
                // iptv-org separates several groups with ";" and uses "Undefined" for none
                currentGenre = parseAttribute(line, "group-title")
                    ?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() && it != "Undefined" }
                    ?.joinToString(", ")?.ifEmpty { null }

                currentId = parseAttribute(line, "tvg-id").orEmpty()
                currentUserAgent = parseAttribute(line, "http-user-agent")
                currentReferrer = parseAttribute(line, "http-referrer")
            } else if (line.startsWith("#EXTVLCOPT:")) {
                val option = line.removePrefix("#EXTVLCOPT:")
                when {
                    option.startsWith("http-user-agent=") -> currentUserAgent = option.substringAfter("=").trim().ifEmpty { null }
                    option.startsWith("http-referrer=") -> currentReferrer = option.substringAfter("=").trim().ifEmpty { null }
                }
            } else if (!line.startsWith("#")) {
                // It's a stream URL
                if (line.startsWith("http://") || line.startsWith("https://")) {
                    val mediaItem = MediaItem(
                        // tvg-id is often shared by several variants of a channel, so add the URL hash
                        id = if (currentId.isEmpty()) line.hashCode().toString() else "$currentId-${line.hashCode()}",
                        title = if (currentTitle.isEmpty()) "Canal Sin Nombre" else currentTitle,
                        url = line,
                        category = currentCategory,
                        logoUrl = currentLogoUrl,
                        genre = currentGenre,
                        description = "Transmisión en vivo de IPTV. Categoría: $currentCategory. Disfruta de tus canales favoritos.",
                        userAgent = currentUserAgent,
                        referrer = currentReferrer
                    )
                    items.add(mediaItem)
                }
                // Reset temporary fields
                currentTitle = ""
                currentLogoUrl = null
                currentCategory = "Canales de TV"
                currentId = ""
                currentGenre = null
                currentUserAgent = null
                currentReferrer = null
            }
        }

        return items
    }

    private fun parseAttribute(line: String, attributeName: String): String? {
        val key = "$attributeName=\""
        if (!line.contains(key)) return null
        val startIndex = line.indexOf(key) + key.length
        val endIndex = line.indexOf("\"", startIndex)
        if (endIndex == -1) return null
        return line.substring(startIndex, endIndex).trim().ifEmpty { null }
    }

    private fun determineCategory(groupTitle: String?, title: String): String {
        val searchString = "${groupTitle ?: ""} $title".lowercase()
        return when {
            searchString.contains("anime") -> "Animes"
            searchString.contains("pelicula") || searchString.contains("movie") || searchString.contains("cine") -> "Peliculas"
            searchString.contains("serie") || searchString.contains("show") || searchString.contains("novela") -> "Series"
            else -> "Canales de TV"
        }
    }

    fun fetchFromUrl(urlString: String): List<MediaItem> = loadPlaylist(urlString).items

    /**
     * Loads the playlist at [urlString]. A blank URL or the default URL returns the demo streams.
     * Network or parse failures fall back to the demo streams and report the reason in [PlaylistResult.error].
     * Must be called off the main thread.
     */
    fun loadPlaylist(urlString: String): PlaylistResult {
        val trimmed = urlString.trim()
        if (trimmed.isEmpty() || trimmed == DEFAULT_M3U_URL) return PlaylistResult(DEMO_STREAMS)
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return PlaylistResult(DEMO_STREAMS, "La URL debe empezar por http:// o https://")
        }
        return try {
            val content = download(trimmed)
            if (!isM3U(content)) {
                PlaylistResult(DEMO_STREAMS, "El archivo no es una lista M3U válida. Mostrando contenido demo.")
            } else {
                PlaylistResult(parse(content))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            PlaylistResult(DEMO_STREAMS, "No se pudo cargar la lista: ${e.message ?: e.javaClass.simpleName}. Mostrando contenido demo.")
        }
    }

    /**
     * Loads the live channels of the public iptv-org list ([IPTV_ORG_URL]), all under "Canales de TV".
     * Unlike [loadPlaylist] it never falls back to the demo streams: on failure the list is empty.
     * Must be called off the main thread.
     */
    fun loadLiveChannels(urlString: String = IPTV_ORG_URL): PlaylistResult = try {
        val content = download(urlString)
        if (!isM3U(content)) {
            PlaylistResult(emptyList(), "La lista de canales no es una lista M3U válida.")
        } else {
            PlaylistResult(parseEntries(content, liveOnly = true))
        }
    } catch (e: Exception) {
        e.printStackTrace()
        PlaylistResult(emptyList(), "No se pudieron cargar los canales: ${e.message ?: e.javaClass.simpleName}")
    }

    private fun isM3U(content: String) = content.trimStart().startsWith("#EXTM3U") || content.contains("#EXTINF")

    private fun download(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "CorTV/1.0 (Android TV)")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            return BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
