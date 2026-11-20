package com.nx.android.input.ui

import com.nx.android.input.GamepadScan
import com.nx.android.input.PadCalibration
import com.nx.android.input.PadMapping
import com.nx.android.input.PadProfileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 一只接在手机上的手柄（GameSir-X5 Lite）。 */
private fun device(id: Int = 7, key: String = "device:abc") = GamepadScan.Info(
    id = id,
    name = "GameSir-X5 Lite",
    vibration = true,
    descriptor = "GameSir-X5 Lite",
    vendorId = 48879,
    productId = 57005,
    axes = setOf(0, 1),
    profileKey = key,
    profilePersistent = true,
    capabilitySignature = "sig",
    modelTemplateKey = null,
    requiresProfileConfirmation = false,
)

/**
 * 手柄中心的状态：布局选择、校准与档案的存取。
 *
 * 手柄的键怎么认只有布局这一个选择：按位置（NS 的四个位置）还是按印字（手柄上印的 A/B/X/Y），
 * 与上游是同一个模型（那边是 `labels` 一个位），这里逐手柄存。
 */
class PadCenterControllerTest {

    private class Fixture {
        val dir: File = Files.createTempDirectory("nx2a-pad-center").toFile()
        val store = PadProfileStore(File(dir, PadProfileStore.FILE_NAME))
        var devices = listOf(device())
        val controller: PadCenterController
        var changes = 0

        init {
            controller = PadCenterController(
                store = store,
                onChanged = { changes++ },
                devices = { devices },
            )
        }

        fun info() = devices.first()
    }

    @Test
    fun `by position the face buttons are the switch's four places`() {
        val f = Fixture()
        // 上面那颗是 X，左边是 Y，右边是 A，下面是 B（按位置）
        assertEquals("x", f.controller.resolveTarget(7, PadMapping.Scan.NORTH, 0))
        assertEquals("y", f.controller.resolveTarget(7, PadMapping.Scan.WEST, 0))
        assertEquals("a", f.controller.resolveTarget(7, PadMapping.Scan.EAST, 0))
        assertEquals("b", f.controller.resolveTarget(7, PadMapping.Scan.SOUTH, 0))
    }

    @Test
    fun `by label the face buttons are the ones the pad prints`() {
        val f = Fixture()
        f.controller.setLayout(f.info(), byLabel = true)
        // 按印字：上面那颗印 Y 就是 Y，左边印 X 就是 X，下面印 A 就是 A，右边印 B 就是 B
        assertEquals("y", f.controller.resolveTarget(7, PadMapping.Scan.NORTH, 0))
        assertEquals("x", f.controller.resolveTarget(7, PadMapping.Scan.WEST, 0))
        assertEquals("a", f.controller.resolveTarget(7, PadMapping.Scan.SOUTH, 0))
        assertEquals("b", f.controller.resolveTarget(7, PadMapping.Scan.EAST, 0))
    }

    @Test
    fun `a controller that reports by its print is recognised`() {
        val f = Fixture()
        f.controller.select(7)
        // 这只手把「印着哪个字」编进了代码：印 Y 的那颗报 308（左边那颗的位置）
        f.controller.startProbe()
        assertTrue(f.controller.probing)
        assertTrue(f.controller.probeKey(7, PadMapping.Scan.WEST, 0))
        assertFalse(f.controller.probing)
        assertTrue(f.controller.profile(f.info()).printCoded)

        // 按位置：印 Y 的那颗（308）在上面 → NS 的 X；印 X 的那颗（307）在左边 → NS 的 Y
        assertEquals("x", f.controller.resolveTarget(7, PadMapping.Scan.WEST, 0))
        assertEquals("y", f.controller.resolveTarget(7, PadMapping.Scan.NORTH, 0))
        // 按印字：印 Y 的就是 Y，印 X 的就是 X
        f.controller.setLayout(f.info(), byLabel = true)
        assertEquals("y", f.controller.resolveTarget(7, PadMapping.Scan.WEST, 0))
        assertEquals("x", f.controller.resolveTarget(7, PadMapping.Scan.NORTH, 0))
    }

    @Test
    fun `a controller that reports by its position stays that way`() {
        val f = Fixture()
        f.controller.select(7)
        f.controller.startProbe()
        // 印着 Y 的那颗在上面，报 307
        assertTrue(f.controller.probeKey(7, PadMapping.Scan.NORTH, 0))
        assertFalse(f.controller.profile(f.info()).printCoded)
        // 按位置：上面那颗是 NS 的 X
        assertEquals("x", f.controller.resolveTarget(7, PadMapping.Scan.NORTH, 0))
    }

    @Test
    fun `the probe only listens to the selected controller`() {
        val f = Fixture()
        f.controller.select(7)
        f.controller.startProbe()
        assertFalse(f.controller.probeKey(99, PadMapping.Scan.WEST, 0))
        assertTrue(f.controller.probing)
        // 别的键（不是面键）不认
        assertFalse(f.controller.probeKey(7, PadMapping.Scan.L1, 0))
        assertTrue(f.controller.probing)
        // 换了一只手柄就不认了
        f.controller.select(99)
        assertFalse(f.controller.probing)
    }

    @Test
    fun `the layout belongs to the controller and is stored`() {
        val f = Fixture()
        f.controller.setLayout(f.info(), byLabel = true)
        assertEquals(1, f.changes)

        val reloaded = PadProfileStore(File(f.dir, PadProfileStore.FILE_NAME)).load().profiles
        val profile = reloaded[f.info().profileKey]
        assertNotNull(profile)
        assertTrue(profile!!.labels)
    }

    @Test
    fun `the calibration belongs to the controller`() {
        val f = Fixture()
        f.controller.setCalibration(f.info(), PadCalibration(deadzone = 0.25f, trigger = 0.6f))

        val profile = f.controller.profile(f.info())
        assertEquals(0.25f, profile.calibration.deadzone, 0.0001f)
        assertEquals(0.6f, profile.calibration.trigger, 0.0001f)
    }

    @Test
    fun `the resolved target is the game key the player sees`() {
        val f = Fixture()
        // 出厂字典按位置：下面那颗是游戏的 B，右边那颗是游戏的 A
        assertEquals("b", f.controller.resolveTarget(7, 0, PadMapping.Key.BUTTON_A))
        assertEquals("a", f.controller.resolveTarget(7, 0, PadMapping.Key.BUTTON_B))
        assertEquals("x", f.controller.resolveTarget(7, PadMapping.Scan.NORTH, 0))
        assertEquals("y", f.controller.resolveTarget(7, PadMapping.Scan.WEST, 0))
    }

    @Test
    fun `the position wins over the key code's name`() {
        val f = Fixture()
        // 任天堂的手柄把上面那颗叫 BUTTON_Y（keyCode 100），把左边那颗叫 BUTTON_X：
        // Android 的名字各家的手柄不一样，位置才是说了算的
        assertEquals("x", f.controller.resolveTarget(7, PadMapping.Scan.NORTH, PadMapping.Key.BUTTON_Y))
        assertEquals("y", f.controller.resolveTarget(7, PadMapping.Scan.WEST, PadMapping.Key.BUTTON_X))
        // 通用键位表的手柄：名字与位置本来就对得上
        assertEquals("x", f.controller.resolveTarget(7, PadMapping.Scan.NORTH, PadMapping.Key.BUTTON_X))
        assertEquals("b", f.controller.resolveTarget(7, PadMapping.Scan.SOUTH, PadMapping.Key.BUTTON_A))
    }

    @Test
    fun `the panel picks the first controller when nothing is selected`() {
        val f = Fixture()
        // 面板上没有选中项时它显示第一只设备：状态也得跟着它走（否则「认键」落空）
        f.controller.devicesChanged(f.devices)
        assertEquals(7, f.controller.selectedId)
        // 认键要对着当前这只
        f.controller.startProbe()
        assertTrue(f.controller.probeKey(7, PadMapping.Scan.WEST, 0))
    }

    @Test
    fun `a controller that goes away leaves the panel`() {
        val f = Fixture()
        f.controller.select(7)
        f.devices = emptyList()
        f.controller.devicesChanged(f.devices)
        assertEquals(null, f.controller.selectedId)
    }
}
