# DroidLink ADB

An Android application that implements a pure-Kotlin ADB (Android Debug Bridge) client for managing Android devices over TCP/IP — no root required (except for local debugging).

## Features

- **Connection Management** — Connect to devices via IP:Port with full ADB handshake (CNXN → AUTH → CNXN), RSA key generation and storage
- **App Management** — List, install (streaming APK), uninstall, disable, enable, and clear data for applications
- **File Manager** — Browse, push, and pull files using ADB Sync protocol
- **Interactive Shell** — Execute shell commands with a terminal-style interface
- **Screen Mirror** — Scrcpy-based screen mirroring with H.264 decoding via MediaCodec (planned)

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture | MVVM + Clean Architecture |
| Network | Pure Kotlin Socket (ADB protocol) |
| Async | Coroutines & Flow |
| Database | Room |
| Storage | Storage Access Framework (SAF) |

## Architecture

```
app/src/main/java/com/droidlink/app/
├── adb/                    # ADB protocol implementation
│   ├── connection/         # Connection & stream management
│   ├── crypto/             # RSA key management
│   └── protocol/           # Message format & sync protocol
├── data/                   # Data layer
│   ├── local/              # Room database, DAOs, entities
│   └── repository/         # Repository implementations
├── domain/                 # Domain layer
│   ├── model/              # Domain models
│   └── repository/         # Repository interfaces
└── presentation/           # Presentation layer
    ├── navigation/         # Navigation graph
    ├── screens/            # Screen composables & ViewModels
    └── theme/              # Material 3 theme
```

## Requirements

- Android 14+ (API 34) for target
- Min SDK 26 (Android 8.0)
- Target device must have ADB over TCP/IP enabled (`adb tcpip 5555`)

## Building

```bash
./gradlew assembleDebug
```

## License

This project is for educational and development purposes.