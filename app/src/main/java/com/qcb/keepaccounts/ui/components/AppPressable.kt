package com.qcb.keepaccounts.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

fun Modifier.appPressable(
	enabled: Boolean = true,
	interactionSource: MutableInteractionSource? = null,
	indication: Indication? = null,
	role: Role? = null,
	onClickLabel: String? = null,
	pressedScale: Float = 0.975f,
	onClick: () -> Unit,
): Modifier = composed {
	val source = interactionSource ?: remember { MutableInteractionSource() }
	val isPressed by source.collectIsPressedAsState()
	val targetScale = if (enabled && isPressed) pressedScale else 1f
	val scale by animateFloatAsState(
		targetValue = targetScale,
		animationSpec = spring(
			dampingRatio = Spring.DampingRatioMediumBouncy,
			stiffness = Spring.StiffnessLow,
		),
		label = "appPressableScale",
	)

	this
		.graphicsLayer {
			scaleX = scale
			scaleY = scale
		}
		.clickable(
			enabled = enabled,
			interactionSource = source,
			indication = indication,
			role = role,
			onClickLabel = onClickLabel,
			onClick = onClick,
		)
}
