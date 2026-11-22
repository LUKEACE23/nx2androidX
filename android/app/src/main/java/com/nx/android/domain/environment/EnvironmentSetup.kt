package com.nx.android.domain.environment

import java.io.File

/** Operations that prepare and validate the bundled Android porter environment. */
interface EnvironmentSetup {
    val ready: Boolean
    val lastCheck: String

    /**
     * Whether the environment has to be built again: the app brought another one (an update) and the phone's
     * was built with the old one.
     */
    fun needsRebuild(): Boolean

    /**
     * Builds the environment again, with the archive the app carries.
     *
     * @param onProgress how much of it is done (0-100)
     */
    fun rebuild(onProgress: (Int) -> Unit)

    fun patchInterpreters()

    /**
     * A program that is started in the guest needs its interpreter path pointed at this phone's
     * loader — the way the payload's own binaries carry it.
     *
     * @return whether the program was patched
     */
    fun patchInterpreter(file: File): Boolean
}
