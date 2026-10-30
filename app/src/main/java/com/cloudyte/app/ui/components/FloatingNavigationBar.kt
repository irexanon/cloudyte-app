package com.cloudyte.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.cloudyte.app.R

val LocalBottomBarInset = staticCompositionLocalOf { 80.dp }

data class FloatingNavItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun FloatingNavigationBar(
    navController: NavController,
    visible: Boolean = true,
    showNavText: Boolean = true,
    modifier: Modifier = Modifier,
    onNavigate: ((String) -> Unit)? = null
) {
    val items = listOf(
        FloatingNavItem("home", stringResource(R.string.nav_home), Icons.Default.Home),
        FloatingNavItem("files", stringResource(R.string.nav_files), Icons.Default.Folder),
        FloatingNavItem("transfers", stringResource(R.string.nav_transfers), Icons.Default.SwapVert),
        FloatingNavItem("settings", stringResource(R.string.nav_settings), Icons.Default.Settings)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("floating_bottom_bar"),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val totalWidth = maxWidth
                val slotWidth = totalWidth / items.size
                val pillSize = if (showNavText) 52.dp else 46.dp
                val targetX = (slotWidth * selectedIndex) + ((slotWidth - pillSize) / 2)
                val pillX by animateDpAsState(
                    targetValue = targetX,
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "pillX"
                )

                // Animated sliding background pill behind active item
                Box(
                    modifier = Modifier
                        .offset(x = pillX)
                        .size(pillSize)
                        .align(Alignment.CenterStart)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                )

                // Row of primary navigation items
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                        val isSelected = index == selectedIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("nav_item_${item.route}")
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = false, radius = 26.dp),
                                    role = Role.Tab
                                ) {
                                    if (!isSelected) {
                                        onNavigate?.invoke(item.route)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    if (showNavText) {
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = item.label,
                                            color = Color.White,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
