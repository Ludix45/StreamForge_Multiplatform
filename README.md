# StreamForge Multiplatform

<p align="center">
  <img src="desktopApp/src/desktopMain/resources/streamforge-logo.png" alt="StreamForge Logo" width="150">
</p>

<p align="center">
  <strong>The Ultimate Desktop & Mobile Companion for Authorized Playback Sources.</strong>
</p>

<p align="center">
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-Multiplatform-blue.svg?logo=kotlin" alt="Kotlin Multiplatform"></a>
  <a href="https://www.jetbrains.com/lp/compose-multiplatform/"><img src="https://img.shields.io/badge/Compose-Multiplatform-orange.svg?logo=jetpackcompose" alt="Compose Multiplatform"></a>
  <a href="https://github.com/Ludix45/StreamForge_Multiplatform/releases/"><img src="https://img.shields.io/github/v/release/Ludix45/StreamForge_Multiplatform?include_prereleases" alt="Latest Version"></a>
  <img src="https://img.shields.io/badge/JDK-21-red.svg" alt="JDK 21">
</p>

---

## 🚀 Overview

**StreamForge** is a modern, cross-platform media solution designed to aggregate and stream content from various authorized sources. Built with **Kotlin Multiplatform** and **Compose Multiplatform**, it provides a seamless and consistent experience across Windows, macOS, Linux, and Android.

StreamForge leverages advanced scraping technology and a robust playback engine (LibVLC) to deliver high-quality HLS/DASH streams in a clean, Material 3 interface.

### 📥 Download

[![Download Windows Installer](https://img.shields.io/badge/Download-Windows%20.MSI-0078D4?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/Ludix45/StreamForge_Multiplatform/releases/download/v1.2.1/StreamForge-1.2.1.msi)
[![Download Android APK](https://img.shields.io/badge/Download-Android%20.APK-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Ludix45/StreamForge_Multiplatform/releases/download/v15/StreamForge.V15.apk)

*For other platforms (macOS, Linux), please check the [Releases](https://github.com/Ludix45/StreamForge_Multiplatform/releases) page.*

---

## ✨ Key Features

- **🔍 Unified Multi-Provider Search**: Scrapes and aggregates results from multiple providers including StreamingCommunity, AnimeUnity, AnimeWorld, EuroStreaming, and Cinezo.
- **📈 Smart Ranking**: Integrated scoring system that prioritizes the most relevant search results.
- **📽️ Professional Playback**: Embedded media player powered by LibVLC/vlcj for maximum stability and format support (HLS/DASH).
- **🏷️ Metadata Enrichment**: Automatically fetches posters, summaries, and trending data using the **TMDB (The Movie Database)** API.
- **🔐 Advanced Decryption**: Custom-built handlers for AES-CBC, PBKDF2, and XOR protected streaming payloads.
- **📱 Responsive UI**: A beautiful Material 3 interface that adapts perfectly to both desktop windows and mobile screens.
- **🌐 Dual Language Support**: Optimized for both Italian and English sources.

---

## 🛠️ Tech Stack

- **Language**: Kotlin 2.2.x
- **UI Framework**: Compose Multiplatform (Desktop & Android)
- **Networking**: OkHttp (with DNS-over-HTTPS), Ktor Client
- **Parsing**: Jsoup, Kotlinx Serialization
- **Media Engine**: LibVLC / VLCJ / JNA
- **Concurrency**: Kotlin Coroutines
- **Build System**: Gradle Kotlin DSL

---

## 📂 Project Structure

```text
├── app/             # Android-specific implementation & UI
├── desktopApp/      # Desktop-specific entry point & packaging (Windows, Mac, Linux)
├── shared/          # Common business logic, models, and shared UI components
└── gradle/          # Dependency management (Version Catalogs)
```

---

## 🏗️ Building from Source

### Prerequisites
- **JDK 21** (Required for Skiko rendering and compatibility)
- **Android Studio** (or IntelliJ IDEA)

### Desktop App
To build and package the desktop application:

```bash
# Windows MSI
./gradlew :desktopApp:packageMsi

# Linux DEB
./gradlew :desktopApp:packageDeb

# macOS DMG
./gradlew :desktopApp:packageDmg
```

### Android App
To build the Android APK:

```bash
./gradlew :app:assembleRelease
```

---

## ⚙️ Configuration

The project uses a `.env` file for keys. Copy `.env.example` to `.env` and configure:

- `GEMINI_API_KEY`: Required for AI-powered features.
- `TMDB_API_KEY`: Used for metadata enrichment.

---

## 🤝 Contributing

Contributions are welcome! If you'd like to improve the scrapers, the UI, or add new providers, please:
1. Fork the repository.
2. Create a feature branch.
3. Submit a Pull Request.

---

## ⚖️ Disclaimer

StreamForge is a tool designed to aggregate links from public sites. It does not host any media content. Users are responsible for ensuring they have the legal right to access the content they view through this application. Use responsibly and in accordance with your local laws.

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Developed with ❤️ by <a href="https://github.com/Ludix45">Ludix45</a>
</p>
