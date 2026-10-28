package com.example.bluetoothchatapplication02.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetoothchatapplication02.R
import com.example.bluetoothchatapplication02.model.ChatMessage
import com.example.bluetoothchatapplication02.ui.components.BluePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    peerName: String = "Connected Device",
    messages: List<ChatMessage> = emptyList(),
    onSendMessage: (String) -> Unit,
    onBackClick: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = peerName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(text = "Connected", color = Color(0xFF34A853), fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = true
            ) {
                items(messages.reversed()) { message ->
                    MessageBubble(message = message)
                }
            }

            ChatInputBar(onSendMessage = onSendMessage)
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val bubbleColor = if (message.isFromMe) BluePrimary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.isFromMe) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    val shape = if (message.isFromMe) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }

    val alignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start
        ) {
            if (!message.isFromMe) {
                Text(
                    text = message.senderName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(shape)
                    .background(bubbleColor)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(text = message.text, color = textColor, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun ChatInputBar(onSendMessage: (String) -> Unit) {
    var textState by remember { mutableStateOf("") }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textState,
                onValueChange = { textState = it },
                placeholder = {
                    Text(
                        text = "Type a message...",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    focusedBorderColor = BluePrimary,
                    cursorColor = BluePrimary
                ),
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (textState.isNotBlank()) {
                        onSendMessage(textState.trim())
                        textState = ""
                    }
                },
                modifier = Modifier
                    .background(BluePrimary, RoundedCornerShape(24.dp))
                    .size(48.dp)
            ) {
                Icon(
                    painterResource(R.drawable.send_24px),
                    contentDescription = "Send",
                    tint = Color.White
                )
            }
        }
    }
}