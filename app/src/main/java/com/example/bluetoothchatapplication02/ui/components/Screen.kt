package com.example.bluetoothchatapplication02.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetoothchatapplication02.R

val BluePrimary = Color(0xFF1877F2)
val CardBackground = Color(0xFFF5F7FA)

sealed class Screen(val route: String, val title: String, val iconResId: Int) {
    object Home : Screen(route = "home", title = "Home", iconResId = R.drawable.home_24px)
    object Discover : Screen(route = "discover", title = "Discover", iconResId = R.drawable.nearby_24px)
    object Chats : Screen(route = "chats", title = "Chats", iconResId = R.drawable.sms_24px)
    object SOS : Screen(route = "sos", title = "SOS", iconResId = R.drawable.sos_24px)
}

@Composable
fun HopLinkBottomNav(currentRoute: String, onItemSelected: (Screen) -> Unit) {
    val items = listOf(Screen.Home, Screen.Discover, Screen.Chats, Screen.SOS)

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 8.dp
    ) {
        items.forEach { screen ->
            NavigationBarItem(
                icon = {
                    Icon(
                        painter = painterResource(id = screen.iconResId),
                        contentDescription = screen.title
                    )
                },
                label = { Text(screen.title, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                selected = currentRoute == screen.route,
                onClick = { onItemSelected(screen) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}