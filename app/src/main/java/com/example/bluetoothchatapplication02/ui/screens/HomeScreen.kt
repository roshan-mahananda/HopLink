package com.example.bluetoothchatapplication02.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetoothchatapplication02.R
import com.example.bluetoothchatapplication02.ui.components.BluePrimary

@Composable
fun HopLinkHeader(
    userName: String,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "HopLink",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Broadcasting as: $userName",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
        }
        IconButton(onClick = onEditClick) {
            Icon(
                painterResource(R.drawable.edit_24px),
                contentDescription = "Edit Name",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun MainToggleCard(isBluetoothOn: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BluePrimary)
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isBluetoothOn) "HopLink is ON" else "HopLink is OFF",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    text = "You are visible to nearby devices",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Switch(
                checked = isBluetoothOn,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BluePrimary,
                    checkedTrackColor = Color.White,
                    uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    subtitle: String,
    icon: Painter,
    iconBgColor: Color,
    badgeText: String,
    badgeBgColor: Color,
    badgeTextColor: Color = MaterialTheme.colorScheme.onPrimary,
    isCircleBadge: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(if (isCircleBadge) CircleShape else RoundedCornerShape(12.dp))
                    .background(badgeBgColor)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    color = badgeTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun HopLinkDashboardScreen(
    userName: String,
    discoveredCount: Int,
    isBluetoothOn: Boolean,
    activeRelaysCount: Int,
    queuedMessagesCount: Int,
    onToggleBluetooth: (Boolean) -> Unit,
    onDiscoverClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Column(modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)) {
        HopLinkHeader(
            userName = userName,
            onEditClick = onEditClick
        )

        MainToggleCard(isBluetoothOn = isBluetoothOn, onToggle = onToggleBluetooth)

        DashboardCard(
            title = "Nearby Devices",
            subtitle = if (isBluetoothOn) "Scanning via\nBluetooth" else "Bluetooth is off",
            icon = painterResource(R.drawable.nearby_24px),
            iconBgColor = MaterialTheme.colorScheme.primaryContainer,
            badgeText = discoveredCount.toString(),
            badgeBgColor = if (discoveredCount > 0) BluePrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            badgeTextColor = if (discoveredCount > 0) Color.White else MaterialTheme.colorScheme.onSurface,
            isCircleBadge = true,
            onClick = onDiscoverClick
        )

        DashboardCard(
            title = "Relay Active",
            subtitle = if (activeRelaysCount > 0) "You are forwarding\n$activeRelaysCount messages" else "No active relays",
            icon = painterResource(R.drawable.schedule_send_24px),
            iconBgColor = MaterialTheme.colorScheme.secondaryContainer,
            badgeText = if (activeRelaysCount > 0) "ON" else "OFF",
            badgeBgColor = if (activeRelaysCount > 0) Color(0xFF34A853) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            badgeTextColor = if (activeRelaysCount > 0) Color.White else MaterialTheme.colorScheme.onSurface
        )

        DashboardCard(
            title = "Messages Queued",
            subtitle = if (queuedMessagesCount > 0) "Waiting for a relay\npath" else "Queue is empty",
            icon = painterResource(R.drawable.message_queue),
            iconBgColor = MaterialTheme.colorScheme.tertiaryContainer,
            badgeText = queuedMessagesCount.toString(),
            badgeBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            badgeTextColor = MaterialTheme.colorScheme.onSurface
        )
    }
}