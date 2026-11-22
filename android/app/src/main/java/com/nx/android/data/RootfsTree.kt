package com.nx.android.data

import java.io.File
import java.io.IOException

/**
 * The rootfs tree's own rules: when it is out of date, and how a new one takes the old one's place.
 *
 * Free of Android, so the JVM tests drive it.
 */
object RootfsTree {

    /**
     * The tree is out of date when the assets carry a version and it is not the one this phone
     * unpacked. No version in the assets means nothing to compare: the tree stays.
     */
    fun stale(want: String, stored: String): Boolean = want.isNotEmpty() && want != stored

    /**
     * The new tree takes the old one's place. The old one is only thrown away once the swap
     * worked: a failure puts it back, and the environment is never missing.
     */
    fun swap(stage: File, rootfs: File, old: File) {
        old.deleteRecursively()
        val hadOld = rootfs.exists()
        if (hadOld && !rootfs.renameTo(old)) rootfs.deleteRecursively()
        if (!stage.renameTo(rootfs)) {
            if (old.exists()) old.renameTo(rootfs)
            stage.deleteRecursively()
            throw IOException("The Linux environment could not be replaced")
        }
        old.deleteRecursively()
    }
}
