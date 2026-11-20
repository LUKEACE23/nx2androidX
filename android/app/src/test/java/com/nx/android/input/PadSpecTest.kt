package com.nx.android.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 推给宿主的映射表（行式文本）：宿主那边读的就是它（nxnative/dm/android/pad_map.h）。
 */
class PadSpecTest {

    @Test
    fun `the table is one block per device`() {
        val text = PadSpec.build(listOf(
            PadSpec.Device(
                deviceId = 3,
                labels = true,
                player = 1,
                calibration = PadCalibration(deadzone = 0.15f, trigger = 0.55f),
            ),
            PadSpec.Device(deviceId = 7, labels = false),
        ))
        val lines = text.trim().lines()
        assertEquals("v1", lines[0])
        assertEquals("dev 3 labels=1 coded=0 player=1 dz=0.15 tr=0.55", lines[1])
        assertTrue(lines.contains("dev 7 labels=0 coded=0 player=0 dz=0.12 tr=0.5"))
    }

    @Test
    fun `the table carries only the layout, the player and the calibration`() {
        val text = PadSpec.build(listOf(
            PadSpec.Device(deviceId = 1, labels = false),
        ))
        assertTrue(!text.contains("bind"))
        assertTrue(!text.contains("unbind"))
        assertEquals("dev 1 labels=0 coded=0 player=0 dz=0.12 tr=0.5", text.trim().lines()[1])
    }

    @Test
    fun `the reporting kind rides in the dev line`() {
        // 按印字上报的手柄：宿主换一张面键表（代码名就是印字）
        val text = PadSpec.build(listOf(
            PadSpec.Device(deviceId = 5, labels = true, printCoded = true),
            PadSpec.Device(deviceId = 6, labels = false),
        ))
        val lines = text.trim().lines()
        assertEquals("dev 5 labels=1 coded=1 player=0 dz=0.12 tr=0.5", lines[1])
        assertEquals("dev 6 labels=0 coded=0 player=0 dz=0.12 tr=0.5", lines[2])
    }

    @Test
    fun `the axis roles ride in the dev line`() {
        val text = PadSpec.build(listOf(
            PadSpec.Device(deviceId = 2, labels = false, zTrig = true, rxStick = true),
            PadSpec.Device(deviceId = 3, labels = false, zTrig = true),
            PadSpec.Device(deviceId = 4, labels = false),
        ))
        val lines = text.trim().lines()
        assertEquals("dev 2 labels=0 coded=0 player=0 dz=0.12 tr=0.5 ztrig=1 rxstick=1", lines[1])
        assertEquals("dev 3 labels=0 coded=0 player=0 dz=0.12 tr=0.5 ztrig=1", lines[2])
        assertEquals("dev 4 labels=0 coded=0 player=0 dz=0.12 tr=0.5", lines[3])   // 没有角色就没有新键：与老表逐字节一致
    }
}
