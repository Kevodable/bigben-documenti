# Kids Fun Planet — Launcher Android per bambini

App Android pensata per essere installata su un monitor/tablet Android collocato in un parchetto giochi. Sostituisce il launcher di sistema con una schermata semplice e colorata (logo "Kids Fun Planet") che mostra solo i giochi gratuiti scelti dal gestore. Quando il bambino chiude il gioco (o preme il tasto Home), l'app torna automaticamente alla schermata con l'elenco dei giochi.

## Come funziona

- L'app si registra come **launcher predefinito** (categoria `HOME`), oltre che come normale app. Al primo avvio del dispositivo (o disinstallando/disabilitando temporaneamente l'altro launcher) Android chiederà di scegliere il launcher predefinito: selezionare "Kids Fun Planet" e impostarlo come predefinito ("sempre").
- Perché Android torna da solo alla griglia: quando l'app launcher è quella predefinita, la sua Activity resta in fondo allo stack. Quando il gioco lanciato viene chiuso (back ripetuto, uscita dal gioco, crash) o quando si preme il tasto Home, il sistema riporta automaticamente in primo piano la nostra schermata, senza bisogno di codice aggiuntivo.
- Nessuna icona di altre app o barra di sistema è visibile: l'interfaccia è a schermo intero (immersive mode).

## Modalità Kiosk (uscita bloccata) — consigliata per il tablet del parco

Per impedire davvero ai bambini di uscire dall'app (niente Home, niente Recenti, niente barra delle notifiche, nessun'altra app apribile a parte i giochi selezionati), l'app sfrutta la **Lock Task Mode** di Android, attivabile registrando l'app come **Device Owner** del tablet. È una procedura *una tantum*, da fare una volta sola su un tablet dedicato solo a questo uso (non un telefono personale).

Requisiti: il tablet deve essere **senza nessun account Google configurato** (va fatto su un dispositivo nuovo o dopo un reset alle impostazioni di fabbrica, saltando la configurazione dell'account durante il setup iniziale), con il debug USB attivo e collegato a un PC con `adb`.

Procedura:

1. Sul tablet: Impostazioni > Info sul telefono > tocca 7 volte "Numero build" per attivare le Opzioni sviluppatore, poi Impostazioni > Sistema > Opzioni sviluppatore > attiva "Debug USB".
2. Collega il tablet al PC via USB e autorizza il debug quando richiesto.
3. Installa l'app (se non già installata): `adb install app-debug.apk`.
4. Imposta l'app come Device Owner:
   ```
   adb shell dpm set-device-owner it.bigbenmatic.gamelauncher/.DeviceOwnerReceiver
   ```
5. Apri l'app: la schermata Impostazioni (PIN) mostrerà "Modalità Kiosk: ATTIVA". Da quel momento il tablet resta bloccato sull'app e sui giochi selezionati.
6. Per fare manutenzione (es. installare nuovi giochi dal Play Store), apri le Impostazioni dell'app col PIN e tocca **"Sblocca temporaneamente"**: questo sospende il blocco finché non si rientra nell'app.

> Se il comando `set-device-owner` fallisce con "not allowed" significa che sul dispositivo è già presente un account o un altro profilo: serve un reset di fabbrica e ripetere la procedura prima di aggiungere account.

Senza questa configurazione, l'app funziona comunque come launcher predefinito a schermo intero (buona protezione per l'uso quotidiano), ma un utente esperto potrebbe comunque raggiungere le impostazioni di sistema tramite le scorciatoie del produttore del tablet.

## Configurazione dei giochi (per il gestore/genitore)

1. Installare dal Google Play Store i giochi gratuiti desiderati sul dispositivo, normalmente.
2. Aprire "Kids Fun Planet" e tenere premuto il logo per accedere all'area riservata.
3. Inserire il PIN (default `1234`, modificabile nelle impostazioni).
4. Selezionare con le checkbox quali app devono apparire ai bambini.
5. (Opzionale) Cambiare il PIN per maggiore sicurezza.

## Build del progetto

Questo repository contiene il progetto Gradle/Kotlin completo (cartella `android/`). Per generare l'APK:

1. Apri la cartella `android/` con **Android Studio** (versione recente, es. 2024.x) — Android Studio scaricherà/genererà automaticamente il Gradle Wrapper e le dipendenze.
2. Collega il dispositivo/monitor Android (o un emulatore) e premi **Run**, oppure genera l'APK con `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
3. Installa l'APK sul monitor del parchetto (es. tramite ADB: `adb install app-debug.apk`).
4. Imposta l'app come launcher predefinito quando richiesto dal sistema (Impostazioni > App > App predefinite > App Home, su alcuni dispositivi).

### Da riga di comando (con Android SDK installato)

```
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> Nota: la cartella non include il file binario `gradlew`/`gradle-wrapper.jar`: Android Studio lo rigenera automaticamente all'apertura del progetto. In alternativa, eseguire `gradle wrapper` una volta con Gradle installato localmente.

## Requisiti minimi

- Android 7.0 (API 24) o superiore.
- I giochi mostrati devono essere già installati sul dispositivo (scaricati da Google Play); l'app non li scarica automaticamente.

## Gestione remota della flotta

I monitor possono essere gestiti da remoto senza recarsi sul posto, anche dopo
l'attivazione del device-owner. Il sistema è composto da tre moduli.

### Modulo 1 — Configurazione remota (`config.json` su GitHub Pages)

- L'app scarica `config.json` all'avvio e poi lo ricontrolla ogni `pollIntervalMinutes`
  (default 15, letto dal file stesso).
- L'URL è impostato in `ConfigRepository.kt` (`FLEET_CONFIG_URL`). Il file di esempio è
  in `launcher/config.json` di questo repository, pensato per essere pubblicato su
  **GitHub Pages** sotto il percorso dedicato `/launcher/` per non entrare in conflitto
  con il sito esistente nella root del repo (`index.html`).
- **Logica di merge** (rispettata esattamente): si parte da `defaults`; si cerca il proprio
  `deviceId` nella mappa `devices`; se presente, i campi indicati per quel dispositivo
  sovrascrivono i default (quelli non indicati restano ai default). Per l'array `games`, le
  voci per-dispositivo sono parziali (`id` + campi cambiati come `visible`/`order`): la
  definizione completa (`package`, `displayName`, `iconUrl`) viene da `defaults.games` e
  viene "patchata".
- **Versioning**: si confronta `configVersion` con quello salvato localmente; se uguale si
  salta, se diverso si applica, si persiste e si logga.
- Cosa si controlla da remoto: elenco/visibilità/ordine/nome+icona/categoria dei giochi,
  branding (logo/colori/sfondo/lingua), layout griglia (colonne, etichette), comportamento
  kiosk (PIN di uscita, auto-launch di un singolo gioco), stato operativo (active/maintenance
  + messaggio), banner promozionali.
- **Offline-first**: l'ultima config valida viene messa in cache e riusata se la rete non è
  disponibile, così il monitor continua a funzionare anche scollegato.

### Modulo 2 — ID dispositivo + schermata Diagnostica

- Al primo avvio viene generato un UUID stabile (`DeviceIdManager`), persistito e incluso in
  ogni download config e in ogni invio di telemetria.
- Una schermata **Diagnostica** (raggiungibile da Impostazioni, quindi protetta dal PIN)
  mostra: `deviceId`, etichetta, versione app, `configVersion` attiva, ultimo contatto
  telemetria, stato connessione. Serve per leggere l'ID sul dispositivo (senza ADB) e
  aggiungerlo alla mappa `devices` del `config.json`.

### Modulo 3 — Telemetria (Google Apps Script → Google Sheet)

GitHub Pages è statico e non può ricevere dati, quindi la telemetria **non** va su github.io:
il ricevitore è un **Google Apps Script** che scrive su un Google Sheet (gratuito, senza
server, consultabile da telefono).

- L'app invia: **heartbeat** leggero ogni `heartbeatIntervalMinutes` (default 5) e un
  **report completo** ogni `reportIntervalMinutes` (default 30) con batteria, uptime e
  conteggio lanci per gioco dall'ultimo report.
- Gli invii sono **resilienti**: i payload vengono accodati localmente e ritrasmessi quando
  torna la rete; non bloccano mai la UI (girano su thread di background nell'`Application`).
- Codice ricevitore e istruzioni di deploy: `launcher/apps-script/Code.gs`. In sintesi: nuovo
  Google Sheet → Estensioni > Apps Script → incolla `Code.gs` → esegui `setup` → distribuisci
  come "App web" (accesso: Chiunque) → copia l'URL `/exec` in `config.json` →
  `defaults.telemetry.reportUrl`.
- **Alert Telegram**: predisposto ma non attivo (funzione `checkOfflineMonitors` in `Code.gs`),
  da completare lato script ricevente quando servirà.

### Compatibilità device-owner / lock task

- Polling config e telemetria girano nell'oggetto `Application` (`FleetApp`) su coroutine di
  background: non interferiscono con `startLockTask`/`stopLockTask` né con la griglia. Il
  kiosk resta integro.
- La allow-list del lock task (`setLockTaskPackages`) viene tenuta sincronizzata con i giochi
  effettivamente mostrati (remoti o locali), così i giochi gestiti da remoto restano lanciabili
  dentro il kiosk.
- **Aggiornamento APK silenzioso** (`update.apkUrl`/`silentInstall`): lo schema è già previsto
  nel `config.json`, ma l'installazione silenziosa via `PackageInstaller` sotto device-owner
  **non è ancora implementata** in questa versione — è il punto più delicato (rischio di
  rompere il kiosk se gestito male) e va aggiunto e testato a parte. Per ora gli aggiornamenti
  dell'app si fanno via ADB. Questo è l'unico scostamento dallo schema fornito.
- `idleTimeoutSeconds`, `sessionLimitSeconds`, `maxVolumePercent`, `brightnessPercent` sono
  letti dalla config e disponibili nel modello dati, ma il loro enforcement attivo (timer di
  inattività, limite sessione, controllo volume/luminosità) non è ancora cablato nella UI:
  predisposto, da completare in un secondo momento.

## Possibili estensioni future (non incluse)

- Enforcement di idle timeout, limite sessione, volume e luminosità da config.
- Installazione APK silenziosa via `PackageInstaller` sotto device-owner.
- Alert Telegram lato Apps Script per monitor offline/crash.
