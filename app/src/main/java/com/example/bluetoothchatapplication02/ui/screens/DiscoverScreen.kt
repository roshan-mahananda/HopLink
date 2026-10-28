package com.example.bluetoothchatapplication02.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetoothchatapplication02.model.BluetoothDevice
import com.example.bluetoothchatapplication02.viewmodel.MeshPeer
import com.example.bluetoothchatapplication02.ui.components.BluePrimary

@Composable
fun DiscoverScreen(
    devices: List<BluetoothDevice>,
    meshPeers: List<MeshPeer>,
    onDeviceClick: (BluetoothDevice) -> Unit,
    onMeshPeerClick: (MeshPeer) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Discovered Mesh Nodes",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Directly Available (1-Hop)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = BluePrimary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (devices.isEmpty()) {
                item {
                    Text(
                        text = "Scanning for nearby HopLink nodes...",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                items(devices) { device ->
                    DeviceCard(device = device, onDeviceClick = onDeviceClick)
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Extended Mesh Network (Out of Range)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (meshPeers.isEmpty()) {
                item {
                    Text(
                        text = "No distant nodes detected on the mesh.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                items(meshPeers) { peer ->
                    MeshPeerCard(peer = peer, onMeshPeerClick = onMeshPeerClick)
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: BluetoothDevice,
    onDeviceClick: (BluetoothDevice) -> Unit
) {
    Card(
        onClick = { onDeviceClick(device) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.userAlias ?: device.deviceName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "BLE Node",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = device.deviceAddress,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = { onDeviceClick(device) },
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Connect", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun MeshPeerCard(
    peer: MeshPeer,
    onMeshPeerClick: (MeshPeer) -> Unit
) {
    Card(
        onClick = { onMeshPeerClick(peer) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = peer.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Mesh Node",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Node ID: ${peer.nodeId}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = { onMeshPeerClick(peer) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Message", color = MaterialTheme.colorScheme.onSecondary, fontSize = 12.sp)
            }
        }
    }
}