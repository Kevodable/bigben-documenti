# Giochi del Parco — Launcher Android per bambini

App Android pensata per essere installata su un monitor/tablet Android collocato in un parchetto giochi. Sostituisce il launcher di sistema con una schermata semplice e colorata che mostra solo i giochi gratuiti scelti dal gestore. Quando il bambino chiude il gioco (o preme il tasto Home), l'app torna automaticamente alla schermata con l'elenco dei giochi.

## Come funziona

- L'app si registra come **launcher predefinito** (categoria `HOME`), oltre che come normale app. Al primo avvio del dispositivo (o disinstallando/disabilitando temporaneamente l'altro launcher) Android chiederà di scegliere il launcher predefinito: selezionare "Giochi del Parco" e impostarlo come predefinito ("sempre").
- Perché Android torna da solo alla griglia: quando l'app launcher è quella predefinita, la sua Activity resta in fondo allo stack. Quando il gioco lanciato viene chiuso (back ripetuto, uscita dal gioco, crash) o quando si preme il tasto Home, il sistema riporta automaticamente in primo piano la nostra schermata, senza bisogno di codice aggiuntivo.
- Nessuna icona di altre app o barra di sistema è visibile: l'interfaccia è a schermo intero (immersive mode).

## Configurazione dei giochi (per il gestore/genitore)

1. Installare dal Google Play Store i giochi gratuiti desiderati sul dispositivo, normalmente.
2. Aprire "Giochi del Parco" e tenere premuto il titolo "I miei giochi" per accedere all'area riservata.
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

## Possibili estensioni future (non incluse)

- Modalità "kiosk" rinforzata con screen pinning automatico o provisioning come Device Owner, per impedire del tutto l'accesso alle impostazioni di sistema o alla barra delle notifiche.
- Timer di gioco per limitare la durata di ogni sessione.
- Download/aggiornamento dei giochi gestito da remoto (MDM).
