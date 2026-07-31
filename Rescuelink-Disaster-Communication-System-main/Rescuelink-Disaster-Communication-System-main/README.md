# RescueLink 🛟

RescueLink is an offline-first disaster communication system built for Android. It leverages Google's Nearby Connections API to create a peer-to-peer mesh network, allowing users to send SOS alerts and chat messages even when cellular networks and Wi-Fi infrastructure are completely down.

## 🌟 Features

*   **Peer-to-Peer Mesh Networking**: Automatically discovers and connects to nearby devices to form a resilient communication mesh.
*   **Offline SOS Alerts**: Broadcast emergency alerts with location and battery metadata to nearby peers.
*   **Rule-Based Keyword Triage**: Offline keyword matching that suggests an emergency type (medical, fire, flood, etc.) from the details you type. This is simple `contains()` keyword matching, not AI or NLP.
*   **Decentralized Chat**: Communicate with other users on the mesh network without internet access.
*   **Disaster Map**: View active SOS alerts and critical points of interest on an offline-capable map (powered by osmdroid).
*   **Emergency Contacts**: Manage and quickly access important contacts.
*   **Multilingual**: UI available in English plus Hindi, Tamil, Telugu, Kannada, and Malayalam (falls back to English for any untranslated string).

## 🛠 Tech Stack

*   **Platform**: Android (Java)
*   **Architecture**: MVVM (Model-View-ViewModel)
*   **Networking**: Google Play Services Nearby Connections API
*   **Database**: Room Database (SQLite)
*   **Mapping**: osmdroid (OpenStreetMap)
*   **Background Tasks**: A foreground service keeps the mesh alive; a periodic WorkManager job re-broadcasts any messages still pending relay to connected peers.

## 🚀 Getting Started

### Prerequisites
*   Android Studio Ladybug (or newer)
*   Android SDK 35
*   A physical Android device (Nearby Connections API relies heavily on Bluetooth and Wi-Fi Direct hardware which doesn't work well on emulators).

### Installation

1.  Clone the repository:
    ```bash
    git clone https://github.com/Kumaran18v/Rescuelink-Disaster-Communication-System.git
    ```
2.  Open the project in Android Studio.
3.  Sync the Gradle project.
4.  Build and run the app on your physical device.

### Permissions Required
The application requires several critical permissions to function properly:
*   Location (Fine & Coarse) for map positioning and Nearby Connections discovery.
*   Bluetooth & Wi-Fi (Nearby Devices) for establishing the mesh network.
*   Post Notifications to keep the mesh service running reliably in the background.

## 🤝 Contributing

We welcome contributions! Please see our [CONTRIBUTING.md](CONTRIBUTING.md) for details on how to get started.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
