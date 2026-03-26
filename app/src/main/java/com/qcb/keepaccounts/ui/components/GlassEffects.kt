package com.qcb.keepaccounts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
            elevation = 12.dp,
            shape = shape,
            ambientColor = glowColor,
            spotColor = glowColor,
        )
        .clip(shape)
        .background(color = backgroundColor, shape = shape)
        .border(width = 1.dp, color = borderColor, shape = shape)
}
