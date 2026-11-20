package com.nx.android.input

/**
 * 查表规则：物理键 → 游戏键。
 *
 * 顺序是「玩家自己绑的 → 出厂字典」：玩家没动过的地方，落回出厂字典。
 *
 * 出厂字典按**位置**说话，而位置是扫描码的事（Linux 手柄规范：304 是下面那颗，307 是上面那颗）：
 * 同一个键在任何手柄上都是同一个扫描码，而 Android 给它起的名字（BUTTON_A、BUTTON_X…）**每家不一样**
 * ——任天堂的手柄有自己的键位表，把上面那颗叫 BUTTON_Y。名字不可信，位置才可信。
 *
 * 游戏嘴里的 A/B/X/Y 指的就是 **NS 手柄上的四个位置**：X 在上、Y 在左、A 在右、B 在下。
 * 手柄上印的字只是贴纸；想要「印字说了算」的玩家在改键页自己绑（或者点那个一键预设）。
 *
 * 没有扫描码的键（十字键）按 Android 的 keyCode 兜底——那上面没有歧义。
 *
 * 这份规则与宿主那边的一份是同一套（nxnative/dm/android/pad_map.h）：宿主按事件的 device 查同一张表。
 */
object PadMapping {

    /** Android 的 keyCode（KeyEvent）：出厂字典用的就是它们。 */
    object Key {
        const val DPAD_UP = 19
        const val DPAD_DOWN = 20
        const val DPAD_LEFT = 21
        const val DPAD_RIGHT = 22
        const val BUTTON_A = 96
        const val BUTTON_B = 97
        const val BUTTON_X = 99
        const val BUTTON_Y = 100
        const val BUTTON_L1 = 102
        const val BUTTON_R1 = 103
        const val BUTTON_L2 = 104
        const val BUTTON_R2 = 105
        const val BUTTON_THUMBL = 106
        const val BUTTON_THUMBR = 107
        const val BUTTON_START = 108
        const val BUTTON_SELECT = 109
    }

    /** Linux 手柄规范的扫描码：BTN_SOUTH 是下面那颗，BTN_EAST 右边，BTN_NORTH 上面，BTN_WEST 左边。 */
    object Scan {
        const val SOUTH = 304
        const val EAST = 305
        const val NORTH = 307
        const val WEST = 308
        const val L1 = 310
        const val R1 = 311
        const val L2 = 312
        const val R2 = 313
        const val SELECT = 314
        const val START = 315
        const val L3 = 317
        const val R3 = 318
    }

    /**
     * 出厂字典（按位置）：**扫描码 → 游戏键**。
     *
     * NS 的位置：X 在上、Y 在左、A 在右、B 在下。游戏说的是这四个位置，
     * 手柄上印的字只是贴纸（想要印字说了算的，改键页有一键预设）。
     */
    private val POSITION: Map<Int, String> = mapOf(
        Scan.SOUTH to "b", Scan.EAST to "a", Scan.NORTH to "x", Scan.WEST to "y",
        Scan.L1 to "l", Scan.R1 to "r", Scan.L2 to "zl", Scan.R2 to "zr",
        Scan.L3 to "ls", Scan.R3 to "rs",
        Scan.SELECT to "minus", Scan.START to "plus",
    )

    /**
     * 按印字的出厂字典：手柄上印的 A/B/X/Y 是哪个游戏键（Xbox 的印字：下 A、右 B、上 Y、左 X）。
     *
     * 布局选「按印字」时用它——上游也是这一个位（那边是 `labels`，环境变量 `DM_PAD_LABEL` 是默认值）。
     */
    private val PRINT: Map<Int, String> = POSITION + mapOf(
        Scan.SOUTH to "a", Scan.EAST to "b", Scan.NORTH to "y", Scan.WEST to "x",
    )

    /**
     * 没有扫描码时的兜底：**Android 的 keyCode → 游戏键**。
     *
     * 只有十字键真的走这条路（它的扫描码是 0）。面键的 keyCode 各家手柄起名字不一样，
     * 拿它当准绳就是之前那个「按上面却变成左边」的毛病，所以它只是兜底。
     */
    private val BY_KEY: Map<Int, String> = mapOf(
        Key.BUTTON_A to "b", Key.BUTTON_B to "a", Key.BUTTON_X to "x", Key.BUTTON_Y to "y",
        Key.BUTTON_L1 to "l", Key.BUTTON_R1 to "r", Key.BUTTON_L2 to "zl", Key.BUTTON_R2 to "zr",
        Key.BUTTON_THUMBL to "ls", Key.BUTTON_THUMBR to "rs",
        Key.BUTTON_START to "plus", Key.BUTTON_SELECT to "minus",
        Key.DPAD_UP to "up", Key.DPAD_DOWN to "down", Key.DPAD_LEFT to "left", Key.DPAD_RIGHT to "right",
    )

    /** 按印字的 keyCode 兜底（与宿主那份一致）。 */
    private val BY_KEY_PRINT: Map<Int, String> = BY_KEY + mapOf(
        Key.BUTTON_A to "a", Key.BUTTON_B to "b", Key.BUTTON_X to "y", Key.BUTTON_Y to "x",
    )

    /**
     * 出厂字典：这个物理键是哪个游戏键（不是手柄键就是 null）。
     *
     * @param scanCode 这个键的扫描码（0：没有）
     * @param keyCode Android 给它起的名字（只在没有扫描码时兜底）
     * @param labels 手柄的布局：按位置（false）还是按印字（true）。
     */
    fun defaultTarget(scanCode: Int, keyCode: Int, labels: Boolean = false): String? {
        if (scanCode > 0) {
            val byPosition = (if (labels) PRINT else POSITION)[scanCode]
            if (byPosition != null) return byPosition
        }
        return (if (labels) BY_KEY_PRINT else BY_KEY)[keyCode]
    }

    /** 这个物理键是哪个游戏键（出厂字典 + 这只手的布局）。 */
    fun resolveButton(labels: Boolean, scanCode: Int, keyCode: Int): String? =
        defaultTarget(scanCode, keyCode, labels)

    /**
     * 面键的**方位**：手柄上那四颗键各在哪儿。
     *
     * Linux 的扫描码说的是位置（BTN_NORTH 是上面那颗），Android 的 keyCode 说的是它在那儿叫什么
     * （BTN_NORTH 在 Android 那边叫 BUTTON_X）。方位是扫描码的事，所以面板上的菱形按它画。
     */
    object Face {
        /** 上面那颗（打印 Y 的那颗在 Xbox 布局的手柄上）。 */
        const val TOP = Scan.NORTH
        /** 左边那颗。 */
        const val LEFT = Scan.WEST
        /** 右边那颗。 */
        const val RIGHT = Scan.EAST
        /** 下面那颗。 */
        const val BOTTOM = Scan.SOUTH

        /**
         * 那颗键在 Android 那边通常叫什么 keyCode（通用键位表：扫描码与 keyCode 是一对）。
         *
         * 只是兜底：说了算的是位置（扫描码），而各家手柄的键位表不一定跟通用表一样
         * （任天堂的手柄就把上面那颗叫 BUTTON_Y）。
         */
        fun keyCodeOf(scanCode: Int): Int = when (scanCode) {
            Scan.SOUTH -> Key.BUTTON_A
            Scan.EAST -> Key.BUTTON_B
            Scan.NORTH -> Key.BUTTON_X
            Scan.WEST -> Key.BUTTON_Y
            Scan.L1 -> Key.BUTTON_L1
            Scan.R1 -> Key.BUTTON_R1
            Scan.L2 -> Key.BUTTON_L2
            Scan.R2 -> Key.BUTTON_R2
            Scan.SELECT -> Key.BUTTON_SELECT
            Scan.START -> Key.BUTTON_START
            Scan.L3 -> Key.BUTTON_THUMBL
            Scan.R3 -> Key.BUTTON_THUMBR
            else -> 0
        }
    }
}
