# HopLink 🛜

**Peer-to-Peer Off-Grid BLE Mesh Communication System**

HopLink is a decentralized, offline Android messaging application that utilizes Bluetooth Low Energy (BLE) to create a self-sustaining mesh network. Designed for environments without cellular or internet coverage, HopLink allows devices to communicate directly (1-hop) or relay messages through intermediate devices (multi-hop) using a highly efficient, connectionless advertising flooding architecture.

## 🚀 Key Features

* **Off-Grid Messaging:** Communicate purely via BLE without Wi-Fi, cellular data, or centralized servers.
* **Connectionless Mesh Routing:** Messages are broadcasted into the air as ambient BLE packets, eliminating slow connection handshakes and allowing instant data transmission.
* **Multi-Hop Relay:** Messages reach beyond a single device's Bluetooth range. Devices transparently relay packets to distant peers based on a defined Time-To-Live (TTL) limit (default: 10 hops).
* **Unique Node Routing:** Replaces standard MAC address routing with a custom randomized 5-digit `Node ID` (e.g., `48291`) to ensure privacy and cross-device consistency.
* **Emergency SOS Broadcast:** A dedicated global broadcast mode that bypasses specific target IDs to alert all nodes in the mesh network simultaneously.
* **Local Persistence:** All conversations and discovered mesh peers are securely cached on the device using a local SQLite database (Room).

## 📡 How the Mesh Works

HopLink utilizes a lightweight, custom 15-byte packet header to manage network flooding and prevent endless loops. 

**Packet Structure (15 Bytes + Payload):**
1. **TTL (1 Byte):** Tracks the remaining hops. Decrements by 1 on each relay. Drops at 0.
2. **Message ID (4 Bytes):** A unique UUID hash. Devices cache recent Message IDs to silently drop duplicates and prevent echo loops.
3. **Sender Node ID (4 Bytes):** The originating device's unique identifier.
4. **Destination Node ID (4 Bytes):** The target device's identifier (`0` indicates a global/SOS broadcast).
5. **Payload Length (2 Bytes):** The byte size of the attached UTF-8 text payload.

When a device receives a packet, it checks the Message ID cache. If it's new and the TTL > 1, the device decrements the TTL and re-broadcasts the payload to its surroundings, expanding the network's physical reach.

## 🛠️ Architecture & Tech Stack

The application follows the MVVM (Model-View-ViewModel) architecture and is built entirely in Kotlin using modern Android development practices.

* **UI:** Jetpack Compose, Material Design 3
* **State Management:** Kotlin Coroutines & StateFlow (`BluetoothViewModel.kt`)
* **Local Storage:** Room Database (`HopLinkDatabase`, `ChatMessageDao`)
* **Networking:** Android `BluetoothGattServer`, `BluetoothLeScanner`, and `BluetoothLeAdvertiser`
* **Background Execution:** Android Foreground Services (`BluetoothLeService.kt`)

### Package Structure

* **`bluetooth/`**: The core networking engine. Handles raw byte buffering, packet creation, GATT service lifecycle, and active BLE scanning/advertising.
* **`data/`**: The local persistence layer managing SQLite operations and chat history retrieval.
* **`model/`**: Core data classes (`BluetoothDevice`, `ChatMessage`, `MeshPeer`) defining network nodes and payloads.
* **`ui/`**: Jetpack Compose UI layer containing reusable components, the navigation graph, and full-screen views (`HomeScreen`, `ChatScreen`, `DiscoverScreen`).
* **`viewmodel/`**: Central state holder bridging the background BLE service and the Compose UI UI.

## 📱 Installation & Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/roshan-mahananda/HopLink.git
2. Open the project in Android Studio.

3. Sync Gradle files.

4. Build and install the APK onto at least two physical Android devices (BLE advertising and scanning cannot be reliably tested on standard Android emulators).

5. Ensure Bluetooth and Location services are enabled on the testing devices. Grant the necessary BLUETOOTH_SCAN, BLUETOOTH_CONNECT, and BLUETOOTH_ADVERTISE permissions upon first launch.
