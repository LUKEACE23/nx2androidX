package com.nx.android.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The rootfs carries a version: when the app brings a newer one, the tree is unpacked again. The
 * old tree keeps working until the new one is whole.
 */
class RootfsTreeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun aTreeWithTheSameVersionStays() {
        assertFalse(RootfsTree.stale("r7", "r7"))
    }

    @Test
    fun aTreeWithAnotherVersionIsOutOfDate() {
        assertTrue(RootfsTree.stale("r8", "r7"))
    }

    @Test
    fun aPayloadWithoutAVersionChangesNothing() {
        assertFalse(RootfsTree.stale("", "r7"))
    }

    @Test
    fun theNewTreeTakesTheOldOnesPlace() {
        val stage = File(tmp.root, "rootfs.new").apply { mkdirs() }
        File(stage, "python").writeText("new")
        val rootfs = File(tmp.root, "rootfs").apply { mkdirs() }
        File(rootfs, "python").writeText("old")

        RootfsTree.swap(stage, rootfs, File(tmp.root, "rootfs.old"))

        assertEquals("new", File(rootfs, "python").readText())
        assertFalse("the old tree is gone once the swap worked", File(tmp.root, "rootfs.old").exists())
    }

    @Test
    fun aFailedSwapLeavesTheOldTreeInPlace() {
        val stage = File(tmp.root, "rootfs.new")   // never unpacked: the swap cannot work
        val rootfs = File(tmp.root, "rootfs").apply { mkdirs() }
        File(rootfs, "python").writeText("old")

        val failed = runCatching { RootfsTree.swap(stage, rootfs, File(tmp.root, "rootfs.old")) }.isFailure

        assertTrue(failed)
        assertTrue("the old tree keeps working", File(rootfs, "python").isFile)
        assertEquals("old", File(rootfs, "python").readText())
    }
}
