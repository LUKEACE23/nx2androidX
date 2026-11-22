package com.nx.android.domain.environment

/**
 * The rule of the environment's build.
 *
 * The app carries the environment in one archive, with a stamp. The phone's environment remembers the stamp it
 * was built with. When the two differ, the environment is built again.
 *
 * It is a rule of its own (no Android in it), so that it can be tested on the JVM: EnvPayloadTest.
 */
object EnvPayload {

    /**
     * Whether the environment has to be built again.
     *
     * @param want the stamp the app's archive carries (empty when the app carries no environment)
     * @param stored the stamp the phone's environment was built with
     * @param environmentThere whether the phone has an environment at all
     */
    fun stale(want: String, stored: String, environmentThere: Boolean): Boolean {
        // the app carries no environment (a build without the payload): the tree is left alone
        if (want.isEmpty()) return false
        if (want == stored) return false
        // a phone with no environment yet is not a rebuild: the first install is the guided setup's business
        return environmentThere
    }
}
