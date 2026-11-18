package com.nx.android.data

import android.content.Context
import java.io.File
import org.json.JSONObject

/**
 * The game profiles package of this build against the player's own layer.
 *
 * The app does not carry the profiles (they are half a gigabyte of shader tables): the player imports a package into
 * their own folder (nx2a/profiles) and keeps it. The app carries the package's manifest (assets/profiles.json): the
 * title IDs that package holds.
 *
 * A package that was cut before a game was ported does not hold that game, so the porter finds no profile for it and
 * the port has to be an experimental one. That is the player's package being older, not the game being unsupported.
 */
class ProfilesPack(private val ids: Set<String>, private val layer: File) {

    /** The package of this build has the game */
    fun has(titleId: String): Boolean = titleId in ids

    /** The player's own layer holds the game's profile */
    fun imported(titleId: String): Boolean = File(layer, titleId).isDirectory

    /** The package of this build has the game and the player's layer does not: the player's package is older */
    fun stale(titleId: String): Boolean = titleId.isNotEmpty() && has(titleId) && !imported(titleId)

    companion object {
        /** The package of this build, read from the app's own assets */
        fun of(app: Context, layer: File): ProfilesPack = ProfilesPack(readIds(app), layer)

        /** The title IDs the package holds (assets/profiles.json) */
        private fun readIds(app: Context): Set<String> = runCatching {
            val text = app.assets.open("profiles.json").use { it.readBytes().toString(Charsets.UTF_8) }
            val array = JSONObject(text).optJSONArray("ids") ?: return emptySet()
            (0 until array.length()).map { array.optString(it) }.filter { it.isNotEmpty() }.toSet()
        }.getOrDefault(emptySet())
    }
}
