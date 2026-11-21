package com.nx.android.agent

/**
 * The page context (ADR-007 / 30-控制契约/35-页面上下文.md): a bounded snapshot of what the player is
 * looking at, attached to one turn. The pages build it, the chat sends it, the model reads it — the UI
 * never shows it.
 *
 * The shape is deliberately strings-only and schema-bounded (a page's whitelist of fields, ≤400 tokens
 * rendered): the pages own the facts, this contract only fixes the envelope.
 */
data class AgentPageContext(
    /** library | builds | settings | game ... (the stable page ids the state machine uses) */
    val page: String,
    /** The object the page is about (the selected game, the running build); null on pages about nothing */
    val subject: AgentSubject? = null,
    /** The page's whitelisted facts, in display order (LinkedHashMap keeps it) */
    val facts: Map<String, String> = emptyMap(),
)

data class AgentSubject(
    /** game | build | env | session */
    val kind: String,
    /** The stable id (title ID, build id); never a file path with the player's home in it */
    val id: String,
    /** What the model may call it */
    val title: String,
)

/**
 * The agent's way to act on the UI (the other half of AG-UI): navigate the player to where the answer
 * lives. The pages implement this and register it on the [com.nx.android.agent.runtime.AgentUiBridge];
 * until they do, the navigate tool answers that it is unavailable.
 */
interface AgentUiActions {
    /**
     * Bring the player to [page] ("library" | "builds" | "settings"); on the builds page [game] (a title
     * ID or a name) selects that game's build directly. Returns the text the model should read back.
     */
    fun navigate(page: String, game: String? = null): String
}
