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

*   **Requisiti**: Assicurati di avere `libmpv-2.dll` nella cartella `desktopApp/src/desktopMain/resources/` affinché venga incluso nell'installazione.

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

## 3. Linux (DEB / RPM)
Per creare il pacchetto per Linux, devi eseguire il comando da un sistema Linux (es. Ubuntu).

*   **Generare DEB (Debian/Ubuntu)**:
    ```bash
    ./gradlew :desktopApp:packageDeb
    ```
*   **Generare RPM (Fedora/RedHat)**:
    ```bash
    ./gradlew :desktopApp:packageRpm
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
