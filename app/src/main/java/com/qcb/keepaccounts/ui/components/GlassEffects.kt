package com.qcb.keepaccounts.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.qcb.keepaccounts.ui.theme.GlassBorder
import com.qcb.keepaccounts.ui.theme.GlassSurface
import com.qcb.keepaccounts.ui.theme.MintGreen

fun Modifier.glassCard(
    shape: RoundedCornerShape = RoundedCornerShape(32.dp),
    backgroundColor: Color = GlassSurface,
    borderColor: Color = GlassBorder,
    glowColor: Color = MintGreen.copy(alpha = 0.4f),
): Modifier {
    return this
        .shadow(
            elevation = 18.dp,
            shape = shape,
            ambientColor = glowColor,
            spotColor = glowColor,
        )
        .graphicsLayer {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                renderEffect = RenderEffect
                    .createBlurEffect(20f, 20f, Shader.TileMode.DECAL)
                    .asComposeRenderEffect()
            }
        }
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.86f),
                    backgroundColor,
                    Color.White.copy(alpha = 0.58f),
                ),
            ),
            shape = shape,
        )
        .border(width = 1.dp, color = borderColor.copy(alpha = 0.9f), shape = shape)
        .border(
            width = 0.6.dp,
            color = Color.White.copy(alpha = 0.55f),
            shape = shape,
        )
}
