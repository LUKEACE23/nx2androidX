package com.nx.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nx.android.R
import com.nx.android.core.ui.PreviewSurface
import com.nx.android.core.ui.theme.NxPalette

/**
 * 空态：架子上几个还没放上游戏的空位。
 *
 * 没有状态，没有动作——只有一张插画、一句提示和几个格式徽章。
 */

/**
 * 空态的插画：架子上几个还没放上游戏的空位。
 *
 * 中间那张是留给游戏的空位（虚线描边 + 一个手柄的幽灵图标），两边两张暗一点、歪一点，
 * 三张都站在一条架板上——没有架板，它们只是三块深色的斑，读不出「架子」这层意思。
 *
 * 尺寸只有这一套，外层的宽是从内容反推的（两张边卡 + 一张空位 + 两个间距），
 * 所以卡片永远不会被外层挤扁：以前外层比内容窄，右边那张卡被压成一条细片。
 */
@Composable
internal fun EmptyShelf() {
    val side = 62.dp
    val slot = 94.dp
    val gap = 12.dp
    // 架子多宽，那一排卡片就多宽——宽度由内容说了算，不由外层说了算
    val rowWidth = side * 2 + slot + gap * 2
    val boardGap = 10.dp
    val boardHeight = 6.dp
    val halo = 168.dp

    Box(Modifier.size(rowWidth, slot + boardGap + boardHeight)) {
        // 光晕：品牌橙的一圈极淡的光，落在空位上，纯黑的底不至于发闷
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = slot / 2 - halo / 2)
                .size(halo)
                .background(Brush.radialGradient(listOf(NxPalette.Brand.copy(alpha = 0.18f), Color.Transparent)))
        )
        // 卡片站在架板上：底边齐平（歪的那两张靠旋转显得随意）
        Row(
            Modifier.align(Alignment.TopStart),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            EmptyCard(side, -11f, 0.55f)
            EmptySlot(slot)
            EmptyCard(side, 11f, 0.55f)
        }
        // 架板：中间亮一点，像光落在板上
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(boardHeight)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            NxPalette.Cream.copy(alpha = 0.05f),
                            NxPalette.Cream.copy(alpha = 0.18f),
                            NxPalette.Cream.copy(alpha = 0.05f),
                        )
                    )
                )
        )
    }
}

/** 预览：空态的那张插画 */
@Preview(name = "Empty shelf", widthDp = 300, heightDp = 200, showBackground = true)
@Composable
private fun EmptyShelfPreview() {
    PreviewSurface {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyShelf()
        }
    }
}

/** 空位：留给游戏的位置——虚线描边（还没放东西）+ 一个手柄的幽灵图标 */
@Composable
private fun EmptySlot(size: Dp) {
    val radius = size * 0.15f
    val shape = RoundedCornerShape(radius)
    Box(
        Modifier
            .size(size)
            .background(Brush.linearGradient(listOf(Color(0xFF232327), Color(0xFF141418))), shape)
            // 虚线：描边自己画（background 的 shape 只管填充，虚线要另画一圈）
            .drawBehind {
                drawRoundRect(
                    color = NxPalette.Unbuilt.copy(alpha = 0.55f),
                    topLeft = Offset.Zero,
                    size = this.size,
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 8f)),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.VideogameAsset, null, Modifier.size(size * 0.36f),
            tint = NxPalette.Cream.copy(alpha = 0.32f))
    }
}

/** 空位卡：和真的游戏瓦片一个形状，只是没有封面，只有暗底与细描边 */
@Composable
private fun EmptyCard(size: Dp, tilt: Float, alpha: Float) {
    // 圆角跟着卡走：14dp 只配得上 94dp 的卡，62dp 的卡用 9dp 才不像鹅卵石
    val shape = RoundedCornerShape(size * 0.15f)
    Box(
        Modifier
            .size(size)
            .rotate(tilt)
            .background(Brush.linearGradient(listOf(Color(0xFF26262A), Color(0xFF16161A))), shape, alpha)
            .border(1.dp, NxPalette.Unbuilt.copy(alpha = 0.40f), shape),
    )
}

/** 空态里的格式徽章：文件后缀是技术记号，两种语言里都是这四个 */
internal val EMPTY_FORMATS = listOf("NSP", "XCI", "NSZ", "XCZ")

/**
 * 空态的文案：一句话、支持的格式。
 *
 * 并排时在插画右边（左边对齐——一行行地读比居中好读），竖排时在插画下面（居中）。
 */
@Composable
internal fun EmptyCopy(align: Alignment.Horizontal, textAlign: TextAlign) {
    Column(horizontalAlignment = align) {
        Text(
            stringResource(R.string.lib_empty_title),
            style = MaterialTheme.typography.displaySmall,
            textAlign = textAlign,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.lib_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign,
            modifier = Modifier.widthIn(max = 340.dp),
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (f in EMPTY_FORMATS) FormatChip(f)
        }
    }
}

@Composable
internal fun FormatChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** 预览：空态里的那几个格式徽章 */
@Preview(name = "Format chips", widthDp = 260, heightDp = 70, showBackground = true)
@Composable
private fun FormatChipsPreview() {
    PreviewSurface {
        Row(Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (f in EMPTY_FORMATS) FormatChip(f)
        }
    }
}
