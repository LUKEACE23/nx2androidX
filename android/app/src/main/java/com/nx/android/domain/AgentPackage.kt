package com.nx.android.domain

import org.json.JSONObject

/**
 * The engine package as the release repository carries it.
 *
 * The package is a zip with the engine binary and a manifest. The manifest says which version the
 * package is, what the binary's digest is and which app version it needs — the app checks all three
 * before the new engine takes the place of the old one.
 *
 * Free of Android, so the tests run on the JVM.
 */
object AgentPackage {

    /** The repository the packages come from. */
    const val REPO_URL = "https://github.com/NixPort/pi-respack"

    /** The feed of the repository's releases: a plain page, no API and no rate limit. */
    const val RELEASES_FEED_URL = "$REPO_URL/releases.atom"

    /** The names of the packages a release carries (the assets page says them), empty when it carries none. */
    fun packageNamesIn(html: String): List<String> =
        Regex("""(pi-agent-r[A-Za-z0-9._-]+\.zip)""").findAll(html).map { it.groupValues[1] }.distinct().toList()

    /** The page that lists what a release carries. */
    fun assetsPage(releaseTag: String): String = "$REPO_URL/releases/expanded_assets/$releaseTag"

    /** The url of a package as a release carries it. */
    fun assetUrl(releaseTag: String, packageName: String): String =
        "$REPO_URL/releases/download/$releaseTag/$packageName"
}

/**
 * What the package's manifest says.
 *
 * @param sha256 the digest of the engine binary the package carries
 */
data class AgentPackageManifest(
    val name: String,
    val version: String,
    val sha256: String,
    val minAppVersion: String,
    val piVersion: String,
) {

    /**
     * Whether this app is new enough for the package. A rule that cannot be read does not hold the
     * package back — the app would rather try it than refuse a package over a word it cannot parse.
     */
    fun fits(appVersion: String): Boolean {
        val need = UpdateVersion.parse(minAppVersion) ?: return true
        val mine = UpdateVersion.parseOwn(appVersion) ?: return true
        return mine >= need
    }

    companion object {
        /** The manifest as it is written; null when it does not say the needed things. */
        fun parse(json: String): AgentPackageManifest? {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
            val name = o.optString("name").trim()
            val version = o.optString("version").trim()
            val sha = o.optString("sha256").trim()
            if (name.isEmpty() || version.isEmpty() || sha.isEmpty()) return null
            return AgentPackageManifest(
                name = name,
                version = version,
                sha256 = sha.lowercase(),
                minAppVersion = o.optString("minAppVersion").trim(),
                piVersion = o.optString("piVersion").trim(),
            )
        }
    }
}
