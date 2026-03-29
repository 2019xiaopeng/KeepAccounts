package com.qcb.keepaccounts.ui.components
import com.qcb.keepaccounts.ui.components.appPressable

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val outerHeight = maxOf(36.dp, (textSizeSp + 2).dp + verticalPadding * 2)
    val innerHeight = (outerHeight - 4.dp).coerceAtLeast(30.dp)
    val itemMinWidth = maxOf(44.dp, horizontalPadding * 2)

    val itemOffsets = remember(options) { mutableStateListOf(*Array(options.size) { 0.dp }) }
    val itemWidths = remember(options) { mutableStateListOf(*Array(options.size) { 0.dp }) }

    val targetOffset = itemOffsets.getOrElse(safeIndex) { 0.dp }
    val targetWidth = itemWidths.getOrElse(safeIndex) { 0.dp }

    val indicatorOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = spring(stiffness = 550f),
        label = "segmented-indicator-offset",
    )
    val indicatorWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(stiffness = 550f),
        label = "segmented-indicator-width",
    )

    Box(
        modifier = modifier
            .wrapContentSize()
            .height(outerHeight)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.18f), shape)
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.40f), shape = shape),
    ) {
        if (indicatorWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .align(Alignment.CenterStart)
                    .width(indicatorWidth)
                    .height(innerHeight)
                    .background(color = accentColor.copy(alpha = 0.82f), shape = shape)
                    .border(width = 0.8.dp, color = Color.White.copy(alpha = 0.35f), shape = shape),
            )
        }

        Row(
            modifier = Modifier.wrapContentSize(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == safeIndex
                val icon = icons.getOrNull(index)
                val textColor = if (selected) WarmBrown else WarmBrownMuted.copy(alpha = 0.8f)

                Row(
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
                        .height(outerHeight)
                        .widthIn(min = itemMinWidth)
                        .appPressable { onSelectedChange(index) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = textColor,
                            modifier = Modifier.size((textSizeSp + 3).dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Text(
                        text = label,
                        color = textColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = textSizeSp.sp,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeight = (textSizeSp + 1).sp,
                        ),
                    )
                }
            }
        }
    }
}
