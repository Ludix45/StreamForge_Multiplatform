# Guida alla Creazione dei File di Installazione

Questa guida spiega come generare i pacchetti di installazione per StreamForge su diverse piattaforme.

## 1. Windows (MSI / EXE)
Per creare l'installer per Windows, devi eseguire il comando da un PC Windows.

*   **Generare MSI (Consigliato)**:
    ```bash
    ./gradlew :desktopApp:packageMsi
    ```
    Il file verrà generato in `desktopApp/build/compose/binaries/main/msi/`.

*   **Generare EXE**:
    ```bash
    ./gradlew :desktopApp:packageExe
    ```
    Il file verrà generato in `desktopApp/build/compose/binaries/main/exe/`.

## 5. Pulizia Dati e Aggiornamenti
*   **Installazione Pulita**: L'installer (.msi, .deb, .dmg) non include i tuoi dati locali (preferiti, cronologia). Ogni volta che generi un installer, questo risulterà come "vuoto" per un nuovo utente.
*   **Persistenza Aggiornamenti**: I dati dell'utente vengono salvati nel Registro di Sistema (Windows) o nella Home dell'utente. Quando l'utente installa una versione aggiornata dell'app, i suoi preferiti e i progressi verranno mantenuti automaticamente perché il sistema punta agli stessi percorsi di memoria personali.

## 2. macOS (DMG / PKG)
Per creare il pacchetto per Mac, devi eseguire il comando da un computer macOS.

*   **Generare DMG**:
    ```bash
    ./gradlew :desktopApp:packageDmg
    ```
    Il file verrà generato in `desktopApp/build/compose/binaries/main/dmg/`.

*   **Nota**: Per evitare blocchi di sicurezza su macOS, l'app dovrebbe essere firmata e "notarizzata" con un account Apple Developer.

## 3. Linux (DEB / RPM / AppImage)
Per creare il pacchetto per Linux, devi eseguire il comando da un sistema Linux (es. Ubuntu).

*   **Generare DEB (Debian/Ubuntu)**:
    ```bash
    ./gradlew :desktopApp:packageDeb
    ```
*   **Generare RPM (Fedora/RedHat)**:
    ```bash
    ./gradlew :desktopApp:packageRpm
    ```

### 📦 Creazione AppImage
Sebbene il plugin Compose non supporti AppImage direttamente, puoi generarlo seguendo questi step su Linux:

1.  **Genera la distribuzione**:
    ```bash
    ./gradlew :desktopApp:createDistributable
    ```
2.  **Scarica appimagetool**:
    ```bash
    wget https://github.com/AppImage/AppImageKit/releases/download/13/appimagetool-x86_64.AppImage
    chmod +x appimagetool-x86_64.AppImage
    ```
3.  **Crea l'AppImage**:
    ```bash
    ./appimagetool-x86_64.AppImage desktopApp/build/compose/binaries/main/app/ StreamForge.AppImage
    ```

---

## 🛠️ Gestione Librerie Native (mpv)

Per funzionare correttamente, il player video ha bisogno della libreria `mpv`. Hai due opzioni:

### Opzione A: Bundling (Consigliata per Windows)
Inserisci i file nelle cartelle dedicate prima di creare l'installer. Verranno inclusi nel pacchetto e l'app sarà "auto-consistente".
*   **Windows**: `packagingDir/windows/libmpv-2.dll`
*   **Linux**: `packagingDir/linux/libmpv.so.2`
*   **macOS**: `packagingDir/macos/libmpv.2.dylib`

### Opzione B: Dipendenza di Sistema (Fallback per Linux/macOS)
Se non includi i file sopra, l'utente dovrà installare `mpv` sul proprio sistema affinché l'app funzioni:

*   **Linux (Ubuntu/Debian)**:
    ```bash
    sudo apt update && sudo apt install libmpv2
    ```
*   **macOS (Homebrew)**:
    ```bash
    brew install mpv
    ```
*   **Windows**: È caldamente consigliato usare l'Opzione A (Bundling) perché Windows non ha un gestore di pacchetti standard come Linux.

---

## 🤖 Automazione Build (GitHub Actions)
Per generare tutti gli installer (Windows, Mac, Linux) contemporaneamente senza dover cambiare computer, puoi usare questo file di configurazione per GitHub Actions da salvare in `.github/workflows/release.yml`:

```yaml
name: Release Build

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    strategy:
      matrix:
        os: [windows-latest, ubuntu-latest, macos-latest]
    runs-on: ${{ matrix.os }}

    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Build and Package (Windows)
        if: matrix.os == 'windows-latest'
        run: ./gradlew :desktopApp:packageMsi

      - name: Build and Package (Linux)
        if: matrix.os == 'ubuntu-latest'
        run: ./gradlew :desktopApp:packageDeb :desktopApp:packageRpm

      - name: Build and Package (macOS)
        if: matrix.os == 'macos-latest'
        run: ./gradlew :desktopApp:packageDmg

      - name: Upload Artifacts
        uses: actions/upload-artifact@v4
        with:
          name: ${{ matrix.os }}-binaries
          path: |
            desktopApp/build/compose/binaries/main/msi/*.msi
            desktopApp/build/compose/binaries/main/deb/*.deb
            desktopApp/build/compose/binaries/main/rpm/*.rpm
            desktopApp/build/compose/binaries/main/dmg/*.dmg
```

## 4. Android (APK)
Per generare l'APK da distribuire per i dispositivi Android:

*   **Generare APK di Debug**:
    ```bash
    ./gradlew :app:assembleDebug
    ```
    Il file si troverà in `app/build/outputs/apk/debug/`.

*   **Generare APK di Release (Firmato)**:
    1. Configura il keystore in `app/build.gradle.kts`.
    2. Esegui:
       ```bash
       ./gradlew :app:assembleRelease
       ```
    Il file si troverà in `app/build/outputs/apk/release/`.

---

### Note Generali
*   Tutti i comandi per il Desktop richiedono una connessione internet per scaricare il runtime Java (JDK) specifico per la piattaforma di destinazione.
*   La versione dell'app può essere modificata in `desktopApp/build.gradle.kts` alla voce `packageVersion`.
