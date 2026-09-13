package com.example.bluetoothchatapplication02

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.bluetoothchatapplication02.bluetooth.BluetoothAdvertiser
import com.example.bluetoothchatapplication02.bluetooth.BluetoothLeService
import com.example.bluetoothchatapplication02.bluetooth.BluetoothScanner
import com.example.bluetoothchatapplication02.bluetooth.BluetoothSupport
import com.example.bluetoothchatapplication02.ui.components.HopLinkBottomNav
import com.example.bluetoothchatapplication02.ui.components.Screen
import com.example.bluetoothchatapplication02.ui.screens.ChatScreen
import com.example.bluetoothchatapplication02.ui.screens.DiscoverScreen
import com.example.bluetoothchatapplication02.ui.screens.HopLinkDashboardScreen
import com.example.bluetoothchatapplication02.ui.screens.ProfileDialog
import com.example.bluetoothchatapplication02.ui.screens.SosScreen
import com.example.bluetoothchatapplication02.ui.theme.BluetoothChatApplication02Theme
import com.example.bluetoothchatapplication02.viewmodel.BluetoothViewModel

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {

    private lateinit var bluetoothSupport: BluetoothSupport
    private lateinit var bluetoothScanner: BluetoothScanner
    private lateinit var bluetoothAdvertiser: BluetoothAdvertiser
    private val viewModel: BluetoothViewModel by viewModels()

    private var bluetoothService: BluetoothLeService? = null
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            bluetoothService = (service as BluetoothLeService.LocalBinder).getService()
            bluetoothService?.let { bluetooth ->
                if (!bluetooth.initialize()) {
                    Log.e("MainActivity", "Unable to initialize Bluetooth")
                    finish()
                }
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bluetoothService = null
        }
    }

    private val gattUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
                }
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                }
                BluetoothLeService.ACTION_NAME_AVAILABLE -> {
                    val address = intent.getStringExtra(BluetoothLeService.EXTRA_ADDRESS) ?: return
                    val name = intent.getStringExtra(BluetoothLeService.EXTRA_NAME) ?: return

                    viewModel.updateDeviceAlias(address, name)
                    viewModel.setConnectedDeviceName(name)
                }
                BluetoothLeService.ACTION_DATA_AVAILABLE -> {
                    val message = intent.getStringExtra(BluetoothLeService.EXTRA_DATA) ?: return
                    viewModel.receiveChatMessage(message)
                }
            }
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.clearDiscoveredDevices()
            startDiscovery()
        } else {
            Log.e("MainActivity", "User declined to enable Bluetooth")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothSupport = BluetoothSupport(this)
        bluetoothScanner = BluetoothScanner()
        bluetoothAdvertiser = BluetoothAdvertiser()

        requestBluetoothPermissions()

        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        enableEdgeToEdge()
        setContent {
            BluetoothChatApplication02Theme {
                val discoveredDevices by viewModel.discoveredDevices.collectAsState()
                val activeRelays by viewModel.activeRelays.collectAsState()
                val queuedMessages by viewModel.queuedMessages.collectAsState()

                val chatMessages by viewModel.chatMessages.collectAsState()
                val connectedPeerName by viewModel.connectedDeviceName.collectAsState()

                var isHopLinkOn by remember {
                    mutableStateOf(bluetoothSupport.getBluetoothAdapter()?.isEnabled == true)
                }

                var currentRoute by remember { mutableStateOf(Screen.Home.route) }
                var savedUsername by remember {
                    mutableStateOf(
                        getSharedPreferences("HopLinkPrefs", MODE_PRIVATE).getString("USER_ALIAS", "") ?: ""
                    )
                }

                var showProfileDialog by remember { mutableStateOf(savedUsername.isEmpty()) }

                if (showProfileDialog) {
                    ProfileDialog(
                        currentName = savedUsername,
                        isFirstLaunch = savedUsername.isEmpty(),
                        onDismiss = { showProfileDialog = false },
                        onSave = { newName ->
                            getSharedPreferences("HopLinkPrefs", MODE_PRIVATE)
                                .edit()
                                .putString("USER_ALIAS", newName)
                                .apply()

                            savedUsername = newName
                            showProfileDialog = false

                            val adapter = bluetoothSupport.getBluetoothAdapter()
                            if (isHopLinkOn && adapter != null) {
                                bluetoothAdvertiser.stopAdvertising(adapter)
                                bluetoothAdvertiser.startAdvertising(adapter, savedUsername)
                            }
                        }
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        HopLinkBottomNav(currentRoute = currentRoute) { selectedScreen ->
                            currentRoute = selectedScreen.route
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentRoute) {
                            Screen.Home.route -> {
                                HopLinkDashboardScreen(
                                    userName = savedUsername,
                                    onEditClick = { showProfileDialog = true },
                                    discoveredCount = discoveredDevices.size,
                                    isBluetoothOn = isHopLinkOn,
                                    activeRelaysCount = activeRelays,
                                    queuedMessagesCount = queuedMessages,
                                    onToggleBluetooth = { isOn ->
                                        isHopLinkOn = isOn
                                        val adapter = bluetoothSupport.getBluetoothAdapter()
                                        if (isOn) {
                                            if (adapter?.isEnabled == false) {
                                                bluetoothSupport.requestEnableBluetooth(enableBluetoothLauncher)
                                            } else {
                                                viewModel.clearDiscoveredDevices()
                                                startDiscovery()
                                            }
                                        } else {
                                            bluetoothScanner.stopScan(adapter)
                                            bluetoothAdvertiser.stopAdvertising(adapter)
                                            viewModel.clearDiscoveredDevices()
                                        }
                                    },
                                    onDiscoverClick = {
                                        currentRoute = Screen.Discover.route
                                    }
                                )
                            }

                            Screen.Discover.route -> {
                                DiscoverScreen(
                                    devices = discoveredDevices,
                                    onDeviceClick = { device ->
                                        viewModel.setConnectedDeviceName(device.userAlias ?: device.deviceName)

                                        bluetoothService?.connect(device.deviceAddress)
                                        currentRoute = Screen.Chats.route
                                    }
                                )
                            }

                            Screen.Chats.route -> {
                                ChatScreen(
                                    peerName = connectedPeerName,
                                    messages = chatMessages,
                                    onSendMessage = { messageText ->
                                        viewModel.addLocalMessage(messageText, savedUsername)

                                        bluetoothService?.sendMessage(messageText)
                                    },
                                    onBackClick = {
                                        currentRoute = Screen.Home.route
                                    }
                                )
                            }

                            Screen.SOS.route -> {
                                SosScreen(
                                    onTriggerSos = {
                                        viewModel.updateQueuedMessages(queuedMessages + 1)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this,
            gattUpdateReceiver,
            makeGattUpdateIntentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(gattUpdateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
        val adapter = bluetoothSupport.getBluetoothAdapter()
        bluetoothScanner.stopScan(adapter)
        bluetoothAdvertiser.stopAdvertising(adapter)
    }

    private fun startDiscovery() {
        val adapter = bluetoothSupport.getBluetoothAdapter()
        if (adapter != null && adapter.isEnabled) {
            val prefs = getSharedPreferences("HopLinkPrefs", MODE_PRIVATE)
            val savedName = prefs.getString("USER_ALIAS", "Anonymous") ?: "Anonymous"
            bluetoothAdvertiser.startAdvertising(adapter, savedName)

            bluetoothScanner.scanLeDevice(adapter) { discoveredDevice ->
                viewModel.addDiscoveredDevice(discoveredDevice)
            }
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_ADVERTISE // <-- Add this line
                ),
                100
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ),
                100
            )
        }
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED)
            addAction(BluetoothLeService.ACTION_DATA_AVAILABLE)
            addAction(BluetoothLeService.ACTION_NAME_AVAILABLE)
        }
    }
}