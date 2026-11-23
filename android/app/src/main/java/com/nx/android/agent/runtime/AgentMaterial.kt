package com.nx.android.agent.runtime

/**
 * The material the App ships: the prompt material the engine reads on the player's phone.
 *
 * A piece of it may carry a placeholder: the app knows the language the player reads, and the
 * sub-agent's own words need it. That is how the language reaches the sub-agent without
 * depending on the main agent to remember to pass it on - the material says it, so it cannot
 * be forgotten.
 */
object AgentMaterial {

    /** The placeholder that carries the player's language into the material. */
    const val PLAYER_LANGUAGE = "{{player_language}}"

    /** The material with its placeholders filled. */
    fun fill(text: String, language: String): String =
        text.replace(PLAYER_LANGUAGE, playerLanguage(language))

    /**
     * The language the player reads, in the words a model understands.
     *
     * The player picked it in the app's settings, or the phone's own language is it. The
     * sub-agent writes its digest in this language, and the case file's player-facing lines
     * with it.
     */
    fun playerLanguage(code: String): String = when (code) {
        "zh" -> "Chinese (zh)"
        "ja" -> "Japanese (ja)"
        "ko" -> "Korean (ko)"
        "fr" -> "French (fr)"
        "de" -> "German (de)"
        "es" -> "Spanish (es)"
        "ru" -> "Russian (ru)"
        else -> "English (${code.ifBlank { "en" }})"
    }
}
