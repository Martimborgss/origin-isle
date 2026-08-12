package com.originisle.android.sports

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches real club crests from TheSportsDB for the football score card.
 *
 * TheSportsDB free tier (key "3", no signup): `searchteams.php?t=<team>` returns `strBadge`, a PNG
 * we download, scale and cache. Provides the two side badges (island pill) plus a composited
 * home-vs-away image for the expanded card centre (vivo's scoreboard template only has one centre
 * icon slot, so we combine the crests into one image).
 */
object SportsFeed {

    private const val SEARCH = "https://www.thesportsdb.com/api/v1/json/3/searchteams.php?t="
    private const val CREST_PX = 96
    private val crestCache = ConcurrentHashMap<String, Bitmap>()

    private val ALIASES = mapOf(
        "dortmund" to "Borussia Dortmund",
        "gladbach" to "Borussia Monchengladbach",
        "bayern" to "Bayern Munich",
        "leverkusen" to "Bayer Leverkusen",
        "frankfurt" to "Eintracht Frankfurt",
        "psg" to "Paris Saint Germain",
        "paris" to "Paris Saint Germain",
        "inter" to "Inter Milan",
        "milan" to "AC Milan",
        "spurs" to "Tottenham",
        "wolves" to "Wolverhampton Wanderers",
        "atletico" to "Atletico Madrid",
        "atlético" to "Atletico Madrid",
        "atleti" to "Atletico Madrid",
        "man city" to "Manchester City",
        "man utd" to "Manchester United",
        "man united" to "Manchester United",
        "barca" to "Barcelona",
        "barça" to "Barcelona",
        "sporting" to "Sporting CP",
        "ol" to "Olympique Lyonnais",
        "om" to "Olympique de Marseille",
        "union" to "Union Saint Gilloise",
    )

    // When a search returns several teams, prefer one of these leagues over amateur/minor divisions.
    private val MAJOR_LEAGUES = setOf(
        "English Premier League", "Spanish La Liga", "German Bundesliga", "Italian Serie A",
        "French Ligue 1", "UEFA Champions League", "UEFA Europa League", "English League Championship",
        "Portuguese Primeira Liga", "Dutch Eredivisie", "Scottish Premiership", "Turkish Super Lig",
        "German 2. Bundesliga", "Spanish La Liga 2", "Belgian Pro League", "Belgian First Division A",
        "Saudi Pro League",
    )

    /** Icons for a match: home badge, away badge, and a composited centre image (nulls if missing). */
    data class MatchIcons(val home: Icon?, val away: Icon?, val center: Icon?)

    /**
     * @param competition the match's competition/league name, if known (e.g. from the notification's
     *   sub-text) — used to pick the right club when a search returns several teams sharing a name
     *   (more accurate than the generic [MAJOR_LEAGUES] preference alone, and it also helps for
     *   leagues that aren't in that hardcoded set).
     */
    private suspend fun crestBitmap(team: String, competition: String = ""): Bitmap? = withContext(Dispatchers.IO) {
        val key = team.trim().lowercase()
        if (key.isEmpty()) return@withContext null
        crestCache[key]?.let { return@withContext it }
        try {
            val query = ALIASES[key] ?: team
            var teams = fetchTeams(query)
            if (teams == null || teams.length() == 0) {
                val normalized = normalizeForSearch(query)
                if (normalized.isNotEmpty() && !normalized.equals(query, ignoreCase = true)) {
                    teams = fetchTeams(normalized)
                }
            }
            if (teams == null || teams.length() == 0) return@withContext null
            val comp = competition.trim().lowercase()
            var chosen: JSONObject? = null
            var chosenIsMajorLeague = false
            for (i in 0 until teams.length()) {
                val t = teams.optJSONObject(i) ?: continue
                if (!t.optString("strSport").equals("Soccer", ignoreCase = true)) continue
                val league = t.optString("strLeague")
                if (chosen == null) chosen = t
                if (comp.isNotEmpty() && league.isNotBlank() &&
                    (league.lowercase().contains(comp) || comp.contains(league.lowercase()))
                ) {
                    // An exact competition match is unambiguous — take it immediately.
                    chosen = t
                    break
                }
                // Otherwise prefer the FIRST major-league result (deterministic), not whichever
                // happens to be scanned last.
                if (!chosenIsMajorLeague && league in MAJOR_LEAGUES) {
                    chosen = t
                    chosenIsMajorLeague = true
                }
            }
            val badge = (chosen ?: teams.optJSONObject(0))?.optString("strBadge")
            if (badge.isNullOrBlank()) return@withContext null
            val raw = URL(badge).openStream().use { BitmapFactory.decodeStream(it) } ?: return@withContext null
            Bitmap.createScaledBitmap(raw, CREST_PX, CREST_PX, true).also { crestCache[key] = it }
        } catch (e: Exception) {
            Log.w("SportsFeed", "crest fetch failed for '$team': ${e.message}")
            null
        }
    }

    private fun fetchTeams(query: String): JSONArray? {
        val json = URL(SEARCH + URLEncoder.encode(query, "UTF-8")).readText()
        return JSONObject(json).optJSONArray("teams")
    }

    /** Fold accented/Nordic letters to plain ASCII and punctuation to spaces, for a fallback search. */
    private fun normalizeForSearch(name: String): String {
        val folded = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "") // strip combining diacritical marks (é -> e, etc.)
            .replace('ø', 'o').replace('Ø', 'O')
            .replace('æ', 'e').replace('Æ', 'E')
            .replace('å', 'a').replace('Å', 'A')
            .replace('ß', 's')
        return folded.replace(Regex("""[/\\_-]"""), " ").replace(Regex("""\s+"""), " ").trim()
    }

    /** Single team crest as an [Icon], or null. */
    suspend fun crest(team: String): Icon? = crestBitmap(team)?.let { Icon.createWithBitmap(it) }

    /** Fetch both crests and build the badge + composite icons for a match. */
    suspend fun iconsFor(home: String, away: String, competition: String = ""): MatchIcons {
        val h = crestBitmap(home, competition)
        val a = crestBitmap(away, competition)
        return MatchIcons(
            home = h?.let { Icon.createWithBitmap(it) },
            away = a?.let { Icon.createWithBitmap(it) },
            center = composite(h, a),
        )
    }

    /** Home and away crest side by side as one image, for the expanded card centre. */
    private fun composite(home: Bitmap?, away: Bitmap?): Icon? {
        if (home == null && away == null) return null
        val gap = CREST_PX / 3
        val out = Bitmap.createBitmap(CREST_PX * 2 + gap, CREST_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        home?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        away?.let { canvas.drawBitmap(it, (CREST_PX + gap).toFloat(), 0f, null) }
        return Icon.createWithBitmap(out)
    }
}
