package com.nx.android.domain.environment

import java.io.File

/** Operations that prepare and validate the bundled Android porter environment. */
interface EnvironmentSetup {
    val ready: Boolean
    val lastCheck: String
    fun unpack(onStep: (String) -> Unit)
    fun reset()
    fun refreshTools()
    fun patchInterpreters()

    /**
     * The rootfs carries a version: when the app brings a newer one, the tree is unpacked again so
     * the new payload reaches the phones that are already installed.
     *
     * @param busy whether something that must not be interrupted is running (a port): the tree is
     *   left alone until it is done
     * @return whether the tree was unpacked again
     */
    fun refreshRootfsIfStale(busy: () -> Boolean, onStep: (String) -> Unit): Boolean

    /**
     * A program that is started in the guest needs its interpreter path pointed at this phone's
     * loader — the way the payload's own binaries carry it.
     *
     * @return whether the program was patched
     */
    fun patchInterpreter(file: File): Boolean
}
