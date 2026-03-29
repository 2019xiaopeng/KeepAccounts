package com.qcb.keepaccounts.ui.components
import com.qcb.keepaccounts.ui.components.appPressable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qcb.keepaccounts.ui.navigation.BottomNavItem
import com.qcb.keepaccounts.ui.theme.WarmBrown

@Composable
fun BottomNavigationBar(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    activeColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .glassCard(
                shape = RoundedCornerShape(26.dp),
                backgroundColor = Color.White.copy(alpha = 0.50f),
                glowColor = activeColor.copy(alpha = 0.22f),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val selected = selectedIndex == index
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.04f else 1f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                label = "navScale",
            )
            val offsetY by animateFloatAsState(
                targetValue = if (selected) -2f else 0f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 450f),
                label = "navOffset",
            )

            Column(
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationY = offsetY,
                    )
                    .background(
                        color = if (selected) activeColor.copy(alpha = 0.16f) else Color.Transparent,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .appPressable {
                        if (selected) return@appPressable
                        onTabSelected(index)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            color = if (selected) activeColor.copy(alpha = 0.2f) else Color.Transparent,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(17.dp),
                        tint = if (selected) activeColor else Color(0xFF7D7C7A),
                    )
                }
                Text(
                    text = item.label,
                    color = if (selected) activeColor else WarmBrown.copy(alpha = 0.75f),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}
