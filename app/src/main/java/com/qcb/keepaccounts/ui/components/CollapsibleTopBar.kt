package com.qcb.keepaccounts.ui.components
import com.qcb.keepaccounts.ui.components.appPressable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qcb.keepaccounts.ui.theme.WarmBrown

@Composable
fun rememberTopBarCollapseProgress(
    listState: LazyListState,
    collapseDistancePx: Float = 220f,
): Float {
    val target = if (listState.firstVisibleItemIndex > 0) {
        1f
    } else {
        (listState.firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
    }
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 360f),
        label = "topBarCollapseProgress",
    )
    return progress
}

@Composable
fun CollapsibleTopBar(
    title: String,
    subtitle: String,
    progress: Float,
    trailingIcon: ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val titleSize = lerpFloat(33f, 22f, safeProgress).sp
    val subtitleSize = lerpFloat(16f, 12f, safeProgress).sp
    val verticalPadding = lerpFloat(12f, 8f, safeProgress).dp
    val iconBoxSize = lerpFloat(48f, 36f, safeProgress).dp
    val iconSize = lerpFloat(24f, 18f, safeProgress).dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = verticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(lerpFloat(2f, 0f, safeProgress).dp)) {
            Text(
                text = title,
                color = WarmBrown,
                fontWeight = FontWeight.ExtraBold,
                fontSize = titleSize,
                lineHeight = titleSize,
            )
            Text(
                text = subtitle,
                color = WarmBrown.copy(alpha = lerpFloat(0.62f, 0.75f, safeProgress)),
                fontWeight = FontWeight.Bold,
                fontSize = subtitleSize,
                lineHeight = subtitleSize,
            )
        }

        if (trailingIcon != null) {
            Box(
                modifier = Modifier
                    .size(iconBoxSize)
                    .background(Color.White.copy(alpha = 0.92f), CircleShape)
                    .appPressable(enabled = onTrailingClick != null) { onTrailingClick?.invoke() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = title,
                    tint = WarmBrown.copy(alpha = 0.9f),
                    modifier = Modifier.size(iconSize),
                )
            }
        } else {
            Spacer(modifier = Modifier.size(iconBoxSize))
        }
    }
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}
