package com.qcb.keepaccounts.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted

@Composable
fun ThemedSegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?> = emptyList(),
    textSizeSp: Int = 13,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 8.dp,
) {
    if (options.isEmpty()) return

    val density = LocalDensity.current
    val safeIndex = selectedIndex.coerceIn(0, options.lastIndex)
    val shape = RoundedCornerShape(999.dp)
    val itemOffsets = remember(options) { mutableStateListOf(*Array(options.size) { 0.dp }) }
    val itemWidths = remember(options) { mutableStateListOf(*Array(options.size) { 0.dp }) }
    var containerWidth by remember(options) { mutableStateOf(0.dp) }
    var containerHeight by remember(options) { mutableStateOf(0.dp) }

    val indicatorOffset = itemOffsets.getOrElse(safeIndex) { 0.dp }
    val indicatorWidth = itemWidths.getOrElse(safeIndex) { 0.dp }

    Box(
        modifier = modifier
            .wrapContentSize()
            .clip(shape),
    ) {
        if (containerWidth > 0.dp && containerHeight > 0.dp) {
            Box(
                modifier = Modifier
                    .width(containerWidth)
                    .height(containerHeight)
                    .graphicsLayer {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = RenderEffect.createBlurEffect(
                                18f,
                                18f,
                                Shader.TileMode.CLAMP,
                            ).asComposeRenderEffect()
                        }
                    }
                    .background(Color.White.copy(alpha = 0.18f), shape)
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.40f), shape = shape),
            )

            Box(
                modifier = Modifier
                    .width(containerWidth)
                    .height(containerHeight)
                    .background(Color.White.copy(alpha = 0.10f), shape),
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(indicatorWidth)
                    .height(containerHeight)
                    .shadow(
                        elevation = 9.dp,
                        shape = shape,
                        ambientColor = accentColor.copy(alpha = 0.34f),
                        spotColor = accentColor.copy(alpha = 0.34f),
                    )
                    .graphicsLayer {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = RenderEffect.createBlurEffect(
                                18f,
                                18f,
                                Shader.TileMode.CLAMP,
                            ).asComposeRenderEffect()
                        }
                    }
                    .background(color = accentColor.copy(alpha = 0.82f), shape = shape)
                    .border(width = 0.8.dp, color = Color.White.copy(alpha = 0.35f), shape = shape),
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(indicatorWidth)
                    .height(containerHeight)
                    .background(Color.White.copy(alpha = 0.10f), shape),
            )
        }

        Row(
            modifier = Modifier
                .wrapContentSize()
                .padding(2.dp)
                .onGloballyPositioned { coords ->
                    val widthDp = with(density) { coords.size.width.toDp() }
                    val heightDp = with(density) { coords.size.height.toDp() }
                    if (containerWidth != widthDp) containerWidth = widthDp
                    if (containerHeight != heightDp) containerHeight = heightDp
                }
                .zIndex(1f),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == safeIndex
                val icon = icons.getOrNull(index)
                val textColor = if (selected) WarmBrown else WarmBrownMuted.copy(alpha = 0.8f)

                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            val xDp = with(density) { coords.positionInParent().x.toDp() }
                            val widthDp = with(density) { coords.size.width.toDp() }
                            if (itemOffsets[index] != xDp) {
                                itemOffsets[index] = xDp
                            }
                            if (itemWidths[index] != widthDp) {
                                itemWidths[index] = widthDp
                            }
                        }
                        .clickable { onSelectedChange(index) }
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = textColor,
                                modifier = Modifier.size((textSizeSp + 3).dp),
                            )
                        }
                        Text(
                            text = label,
                            color = textColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = textSizeSp.sp,
                        )
                    }
                }
            }
        }
    }
}
