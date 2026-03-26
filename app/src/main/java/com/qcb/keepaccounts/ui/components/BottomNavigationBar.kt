package com.qcb.keepaccounts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.qcb.keepaccounts.ui.navigation.BottomNavItem
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted

@Composable
fun BottomNavigationBar(
    navController: NavHostController,
    items: List<BottomNavItem>,
    modifier: Modifier = Modifier,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .glassCard(shape = RoundedCornerShape(28.dp), glowColor = MintGreen.copy(alpha = 0.34f))
            .background(color = Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            val tint = if (selected) WarmBrown else WarmBrownMuted
            val textStyle = if (selected) FontWeight.ExtraBold else FontWeight.Medium

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = item.icon,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = item.label,
                    color = tint,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = textStyle,
                )
            }
        }
    }
}
