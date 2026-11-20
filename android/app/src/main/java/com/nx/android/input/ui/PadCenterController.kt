package com.nx.android.input.ui

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.InputDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nx.android.data.AppLog
import com.nx.android.input.GamepadScan
import com.nx.android.input.PadCalibration
import com.nx.android.input.PadMapping
import com.nx.android.input.PadProfileKey
import com.nx.android.input.PadProfileStore
import com.nx.android.input.PadTestInput
import java.io.File

/**
 * 手柄中心的状态：档案、当前看哪只手柄。
 *
 * 中心在两处开（首页的顶栏入口、游戏里的抽屉），状态放在这里，两处共用一份：
 * 档案写的是同一个文件（每只手柄一份，存在 App 自己的目录里），游戏读到的也是它。
 *
 * 手柄的键怎么认，**只有布局这一个选择**：按位置（NS 的四个位置：上 X、左 Y、右 A、下 B）
 * 或按印字（手柄上印的 A/B/X/Y）。这与上游的做法一致（那边是一个 `labels` 位，
 * 环境变量 `DM_PAD_LABEL` 还是默认值）；逐键改键没有了——怪手柄靠布局选对就行，
 * 实在不行换一只印法合的手柄。
 *
 * 改完档案 [onChanged] 会被叫一次：游戏里那次要把新表推给宿主（改完即时生效，不用重启游戏）；
 * 首页那次没有宿主可推，存下就好（游戏下次起来自己读）。
 */
class PadCenterController(
    private val store: PadProfileStore,
    private val onChanged: (Map<String, PadProfileStore.Profile>) -> Unit = {},
    private val devices: () -> List<GamepadScan.Info> = { GamepadScan.devices.value },
) {

    private val loaded = store.load()

    /** 每只手柄一份档案（键 → 档案）。 */
    var profiles by mutableStateOf(loaded.profiles)
        private set

    /** 档案坏了、被救回来的：面板上要说一句（玩家的改动可能没在）。 */
    var recovered by mutableStateOf(loaded.recoveredFromCorruption)
        private set

    /** 面板上正看着哪只手柄。 */
    var selectedId by mutableStateOf<Int?>(null)
        private set

    /** 这只手柄的档案（没有就是空的那一份）。 */
    fun profile(info: GamepadScan.Info): PadProfileStore.Profile =
        profiles[info.profileKey] ?: PadProfileStore.Profile(info.profileKey)

    /** 面板上看哪一只。 */
    fun select(id: Int?) {
        selectedId = id
        if (probing) probing = false   // 换了一只，认的那只就不是它了
    }

    /**
     * 面键怎么认：按位置（false）或按印字（true）。
     *
     * 存的是档案里的那一个位（`labels`），宿主那边按它选字典（`pad_default_bit` 的两套：
     * 位置与印字）。即存即生效——游戏里推新表，首页存下。
     */
    fun setLayout(info: GamepadScan.Info, byLabel: Boolean) {
        val profile = profile(info)
        if (profile.labels == byLabel) return
        save(profile.copy(labels = byLabel))
        PadTestInput.clearPressed()   // 按着的键算出来的目标键变了：面板上的亮格重来
    }

    /**
     * 这只手的面键**怎么上报**：按位置（false）还是按印字（true）。
     *
     * 固件说了算：按印字上报的手柄把「印着哪个字」编进了代码里（印 X 的报 307、
     * 印 Y 的报 308），同一份「按位置/按印字」表对它就不对了。
     */
    fun setPrintCoded(info: GamepadScan.Info, printCoded: Boolean) {
        val profile = profile(info)
        if (profile.printCoded == printCoded) return
        save(profile.copy(printCoded = printCoded))
        PadTestInput.clearPressed()
    }

    /** 正在等玩家按「印着 Y 的那颗键」吗（面板上要显示这句话）。 */
    var probing by mutableStateOf(false)
        private set

    /** 开始认这只手的上报方式。 */
    fun startProbe() {
        probing = true
        AppLog.line("pad: the probe waits for the button printed Y (selected=$selectedId)")
    }

    /** 不认了。 */
    fun cancelProbe() {
        probing = false
    }

    /**
     * 认键：玩家按下的那一颗（扫描码，没有就按 keyCode）。
     *
     * 按印字上报的手柄：印着 Y 的那颗报 308；按位置上报的：报 307。
     * 别的码认不出来（就当按位置上报——那是绝大多数手柄的样子）。
     *
     * @return true：这个键被认走了（不该再进游戏）。
     */
    fun probeKey(deviceId: Int, scanCode: Int, keyCode: Int): Boolean {
        if (!probing) return false
        AppLog.line("pad: the probe got a key: device=$deviceId scan=$scanCode key=$keyCode selected=$selectedId")
        if (deviceId != selectedId) {
            AppLog.line("pad: the probe ignores it (another controller)")
            return false
        }
        val info = device(deviceId) ?: return false
        val coded = when {
            scanCode == PadMapping.Scan.WEST -> true
            scanCode == PadMapping.Scan.NORTH -> false
            // 没有扫描码时按 keyCode：通用键位表把 308 起名叫 BUTTON_Y
            scanCode == 0 && keyCode == PadMapping.Key.BUTTON_Y -> true
            scanCode == 0 && keyCode == PadMapping.Key.BUTTON_X -> false
            else -> {
                AppLog.line("pad: the probe does not know that key (scan=$scanCode key=$keyCode)")
                return false
            }
        }
        probing = false
        AppLog.line("pad: the probe says ${if (coded) "by label" else "by position"} (${info.profileKey})")
        setPrintCoded(info, coded)
        return true
    }

    /** 校准（死区与扳机阈值）。 */
    fun setCalibration(info: GamepadScan.Info, calibration: PadCalibration) {
        val profile = profile(info)
        save(profile.copy(calibration = calibration))
    }

    /**
     * 手柄的增减：拔掉的那只从面板上消失。
     *
     * 面板上没有选中项时它显示第一只设备——那这里也把它选上：状态要跟着显示走，
     * 否则「认上报方式」那种要对着**当前这只**手柄做的事会落空。
     */
    fun devicesChanged(devices: List<GamepadScan.Info>) {
        val ids = devices.mapTo(mutableSetOf()) { it.id }
        if (selectedId != null && selectedId !in ids) selectedId = null
        if (selectedId == null) selectedId = devices.firstOrNull()?.id
    }

    /**
     * 这个物理键（deviceId 的那只）现在是哪个游戏键：出厂字典（按这只手的布局与上报方式）。
     * 示波画的就是这一层——玩家按了手柄上印着 A 的那颗，按印字时亮的是 A，按位置时亮的是 B。
     */
    fun resolveTarget(deviceId: Int, scanCode: Int, keyCode: Int): String? {
        val info = device(deviceId)
        // 列表里没有这只设备（它刚重连，设备 id 变了）：按它自己算档案键
        val key = info?.profileKey
            ?: InputDevice.getDevice(deviceId)?.let { PadProfileKey.forDevice(it).key }
            ?: return null
        val profile = profiles[key]
        return PadMapping.resolveButton(
            labels = profile?.labels ?: false,
            printCoded = profile?.printCoded ?: false,
            scanCode = scanCode,
            keyCode = keyCode,
        )
    }

    /** 这只手柄的死区（示波画的是游戏收到的那一份：死区里的偏移算 0）。 */
    fun deadzoneOf(info: GamepadScan.Info): Float =
        profiles[info.profileKey]?.calibration?.deadzone ?: PadCalibration.DEFAULT_DEADZONE

    /** 档案存下：写文件，再叫外面（游戏里要把新表推给宿主）。 */
    private fun save(profile: PadProfileStore.Profile) {
        profiles = profiles + (profile.key to profile)
        store.save(profiles)
        onChanged(profiles)
    }

    /** 从磁盘重读一遍档案（游戏回到前台时：玩家可能刚在首页改过）。 */
    fun reload() {
        val fresh = store.load()
        profiles = fresh.profiles
        recovered = fresh.recoveredFromCorruption
    }

    private fun device(deviceId: Int): GamepadScan.Info? =
        devices().firstOrNull { it.id == deviceId }

    companion object {
        /** 这个 App 里的手柄档案（游戏与首页用的是同一个文件）。 */
        fun forContext(context: Context): PadCenterController = PadCenterController(
            PadProfileStore(File(context.filesDir, "pads/${PadProfileStore.FILE_NAME}")),
        )
    }
}

/** 测振动：设备马达优先，机身兜底（蓝牙手柄常常没有马达）。 */
fun vibratePad(context: Context, info: GamepadScan.Info) {
    val manager = context.getSystemService(InputManager::class.java)
    val device = manager?.getInputDevice(info.id)
    val vibrator = if (Build.VERSION.SDK_INT >= 31) device?.vibratorManager?.defaultVibrator else device?.vibrator
    val effect = VibrationEffect.createOneShot(
        120, VibrationEffect.DEFAULT_AMPLITUDE,
    )
    if (vibrator?.hasVibrator() == true) {
        vibrator.vibrate(effect)
    } else {
        context.getSystemService(Vibrator::class.java)?.vibrate(effect)   // 没有马达：机身
    }
}
