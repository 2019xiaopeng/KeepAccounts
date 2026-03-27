package com.qcb.keepaccounts.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures

private val bottomTabRoutes = listOf(
    KeepAccountsDestination.HOME,
    KeepAccountsDestination.CHAT,
    KeepAccountsDestination.LEDGER,
    KeepAccountsDestination.PROFILE,
)

@Composable
fun rememberSwipeTabNavigator(currentRoute: String?, navigateToTab: (String) -> Unit): Modifier {
    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 72.dp.toPx() } }

    return Modifier.composed {
        pointerInput(currentRoute) {
            var accumX = 0f
            detectHorizontalDragGestures(
                onDragStart = { accumX = 0f },
                onDragEnd = {
                    val currentIndex = bottomTabRoutes.indexOf(currentRoute)
                    if (currentIndex < 0) return@detectHorizontalDragGestures
                    val target = when {
                        accumX < -thresholdPx && currentIndex < bottomTabRoutes.lastIndex -> bottomTabRoutes[currentIndex + 1]
                        accumX > thresholdPx && currentIndex > 0 -> bottomTabRoutes[currentIndex - 1]
                        else -> null
                    }
                    if (target != null) navigateToTab(target)
                },
                onHorizontalDrag = { change, dragAmount ->
                    accumX += dragAmount
                    change.consume()
                },
            )
        }
    }
}

fun Modifier.edgeSwipeBack(onBack: () -> Unit): Modifier = composed {
    val density = LocalDensity.current
    val edgeWidthPx = remember(density) { with(density) { 24.dp.toPx() } }
    val triggerPx = remember(density) { with(density) { 72.dp.toPx() } }

    pointerInput(Unit) {
        var startX = 0f
        var accumX = 0f
        var enabled = false

        detectHorizontalDragGestures(
            onDragStart = {
                startX = it.x
                accumX = 0f
                enabled = startX <= edgeWidthPx
            },
            onDragEnd = {
                if (enabled && accumX > triggerPx) onBack()
            },
            onHorizontalDrag = { change, dragAmount ->
                if (!enabled) return@detectHorizontalDragGestures
                accumX += dragAmount
                change.consume()
            },
        )
    }
}
