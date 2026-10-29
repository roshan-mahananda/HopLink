package com.example.bluetoothchatapplication02.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosScreen(onTriggerSos: (String) -> Unit) {
    var isTriggered by remember { mutableStateOf(false) }
    var lastSentMessage by remember { mutableStateOf("") }

    LaunchedEffect(isTriggered) {
        if (isTriggered) {
            delay(5000)
            isTriggered = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            onClick = {
                isTriggered = true
                lastSentMessage = "General SOS"
                onTriggerSos("[SOS] HELP!")
            },
            modifier = Modifier.size(160.dp),
            shape = CircleShape,
            color = Color(0xFFFF4D4D),
            shadowElevation = 8.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = "SOS",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 36.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Specific Alerts",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickAlertButton(
                label = "Medical",
                payload = "[SOS] MEDICAL!"
            ) { msg, label ->
                isTriggered = true
                lastSentMessage = label
                onTriggerSos(msg)
            }

            QuickAlertButton(
                label = "Fire",
                payload = "[SOS] FIRE!"
            ) { msg, label ->
                isTriggered = true
                lastSentMessage = label
                onTriggerSos(msg)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickAlertButton(
                label = "Lost",
                payload = "[SOS] LOST!"
            ) { msg, label ->
                isTriggered = true
                lastSentMessage = label
                onTriggerSos(msg)
            }

            QuickAlertButton(
                label = "Danger",
                payload = "[SOS] DANGER!"
            ) { msg, label ->
                isTriggered = true
                lastSentMessage = label
                onTriggerSos(msg)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isTriggered) {
            Text(
                text = "$lastSentMessage Broadcasted!",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFFF4D4D),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun QuickAlertButton(
    label: String,
    payload: String,
    onClick: (String, String) -> Unit
) {
    Button(
        onClick = { onClick(payload, label) },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(140.dp)
            .height(50.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}