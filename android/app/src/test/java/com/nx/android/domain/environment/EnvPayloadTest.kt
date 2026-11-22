package com.nx.android.domain.environment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule of the environment's build: when the app brought another environment, the phone's one is built again.
 */
class EnvPayloadTest {

    @Test
    fun theSameStampIsNoBuild() {
        assertFalse(EnvPayload.stale("abc", "abc", true))
    }

    @Test
    fun anotherStampIsABuild() {
        assertTrue(EnvPayload.stale("def", "abc", true))
    }

    @Test
    fun anAppWithoutAnEnvironmentLeavesTheTreeAlone() {
        assertFalse(EnvPayload.stale("", "abc", true))
        assertFalse(EnvPayload.stale("", "", true))
    }

    @Test
    fun aPhoneWithoutAnEnvironmentIsNotARebuild() {
        // the first install is the guided setup's business, not the app's start
        assertFalse(EnvPayload.stale("def", "", false))
    }

    @Test
    fun aPhoneWithNoStampIsBuiltAgain() {
        // the phone's environment was built by an app that did not write a stamp: it is an old one
        assertTrue(EnvPayload.stale("def", "", true))
    }
}
