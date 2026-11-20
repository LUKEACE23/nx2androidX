package com.nx.android.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 出厂字典：手柄的键怎么认。
 *
 * 按**位置**（扫描码）说话：游戏嘴里的 A/B/X/Y 指的就是 NS 手柄上的四个位置
 * （X 上、Y 左、A 右、B 下），手柄上印的字只是贴纸——那是「按印字」那一套
 * （Xbox 的印字：下 A、右 B、上 Y、左 X），玩家在手柄中心里选。
 * Android 给键起的名字（BUTTON_A、BUTTON_X…）每家手柄不一样，所以位置说了算，名字只是兜底。
 */
class PadMappingTest {

    @Test
    fun `the default dictionary is the Switch's layout by position`() {
        assertEquals("b", PadMapping.defaultTarget(PadMapping.Scan.SOUTH, 0))
        assertEquals("a", PadMapping.defaultTarget(PadMapping.Scan.EAST, 0))
        assertEquals("x", PadMapping.defaultTarget(PadMapping.Scan.NORTH, 0))
        assertEquals("y", PadMapping.defaultTarget(PadMapping.Scan.WEST, 0))
        assertEquals("l", PadMapping.defaultTarget(PadMapping.Scan.L1, 0))
        assertEquals("r", PadMapping.defaultTarget(PadMapping.Scan.R1, 0))
        assertEquals("zl", PadMapping.defaultTarget(PadMapping.Scan.L2, 0))
        assertEquals("zr", PadMapping.defaultTarget(PadMapping.Scan.R2, 0))
        assertEquals("ls", PadMapping.defaultTarget(PadMapping.Scan.L3, 0))
        assertEquals("rs", PadMapping.defaultTarget(PadMapping.Scan.R3, 0))
        assertEquals("plus", PadMapping.defaultTarget(PadMapping.Scan.START, 0))
        assertEquals("minus", PadMapping.defaultTarget(PadMapping.Scan.SELECT, 0))
        // the d-pad has no scan code: the key code's name it is
        assertEquals("up", PadMapping.defaultTarget(0, PadMapping.Key.DPAD_UP))
        assertEquals("down", PadMapping.defaultTarget(0, PadMapping.Key.DPAD_DOWN))
        assertEquals("left", PadMapping.defaultTarget(0, PadMapping.Key.DPAD_LEFT))
        assertEquals("right", PadMapping.defaultTarget(0, PadMapping.Key.DPAD_RIGHT))
        assertNull(PadMapping.defaultTarget(306, 0))
        assertNull(PadMapping.defaultTarget(0, 9999))
    }

    @Test
    fun `the position wins over the key code's name`() {
        // 任天堂的手柄把上面那颗叫 BUTTON_Y（keyCode 100），把左边那颗叫 BUTTON_X：
        // 名字各家的手柄不一样，位置才是说了算的
        assertEquals("x", PadMapping.defaultTarget(PadMapping.Scan.NORTH, PadMapping.Key.BUTTON_Y))
        assertEquals("y", PadMapping.defaultTarget(PadMapping.Scan.WEST, PadMapping.Key.BUTTON_X))
        // 通用键位表的手柄：名字与位置本来就对得上
        assertEquals("x", PadMapping.defaultTarget(PadMapping.Scan.NORTH, PadMapping.Key.BUTTON_X))
        assertEquals("b", PadMapping.defaultTarget(PadMapping.Scan.SOUTH, PadMapping.Key.BUTTON_A))
    }

    @Test
    fun `by label the face buttons are the Xbox print`() {
        // 按印字：手柄上印的 A/B/X/Y 是哪个游戏键（下 A、右 B、上 Y、左 X）
        assertEquals("a", PadMapping.defaultTarget(PadMapping.Scan.SOUTH, 0, labels = true))
        assertEquals("b", PadMapping.defaultTarget(PadMapping.Scan.EAST, 0, labels = true))
        assertEquals("y", PadMapping.defaultTarget(PadMapping.Scan.NORTH, 0, labels = true))
        assertEquals("x", PadMapping.defaultTarget(PadMapping.Scan.WEST, 0, labels = true))
        // the rest is the same either way
        assertEquals("l", PadMapping.defaultTarget(PadMapping.Scan.L1, 0, labels = true))
        assertEquals("plus", PadMapping.defaultTarget(PadMapping.Scan.START, 0, labels = true))
    }

    @Test
    fun `the key code is the fallback when there is no scan code`() {
        // 十字键没有扫描码：按 Android 的名字（那上面没有歧义）
        assertEquals("up", PadMapping.defaultTarget(0, PadMapping.Key.DPAD_UP))
        // 面键没有扫描码时也走按键码兜底：按位置与按印字各有一套
        assertEquals("b", PadMapping.defaultTarget(0, PadMapping.Key.BUTTON_A))
        assertEquals("a", PadMapping.defaultTarget(0, PadMapping.Key.BUTTON_A, labels = true))
    }

    @Test
    fun `resolve button is the dictionary with the layout`() {
        assertEquals("x", PadMapping.resolveButton(labels = false, scanCode = PadMapping.Scan.NORTH, keyCode = 0))
        assertEquals("y", PadMapping.resolveButton(labels = true, scanCode = PadMapping.Scan.NORTH, keyCode = 0))
        // a key that is not in the dictionary at all: nothing
        assertNull(PadMapping.resolveButton(labels = false, scanCode = 306, keyCode = 0))
    }
}
