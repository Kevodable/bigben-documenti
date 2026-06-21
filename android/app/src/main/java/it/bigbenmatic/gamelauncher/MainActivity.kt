package it.bigbenmatic.gamelauncher

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Screen { HOME, PIN_ENTRY, SETTINGS, DIAGNOSTICS }

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PrefsManager
    private val resumeTick = mutableStateOf(0)
    // Bumped on every touch/key the launcher receives; drives the idle-timeout reset.
    private val interactionTick = mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        hideSystemBars()
        KioskManager.syncAllowedPackages(this, prefs.getSelectedPackages())

        setContent {
            LauncherApp(
                prefs = prefs,
                resumeTick = resumeTick.value,
                interactionTick = interactionTick.value,
            )
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        interactionTick.value = android.os.SystemClock.elapsedRealtime()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        KioskManager.engage(this)
        resumeTick.value++
        interactionTick.value = android.os.SystemClock.elapsedRealtime()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LauncherApp(prefs: PrefsManager, resumeTick: Int, interactionTick: Long) {
    val context = LocalContext.current
    val fleetApp = context.applicationContext as FleetApp
    val config by fleetApp.configRepository.config.collectAsState()
    val connectionStatus by fleetApp.configRepository.connectionStatus.collectAsState()

    var screen by remember { mutableStateOf(Screen.HOME) }
    var selectedPackages by remember { mutableStateOf(prefs.getSelectedPackages()) }
    var allApps by remember { mutableStateOf(InstalledAppsRepository.getAllLaunchableApps(context)) }

    fun refreshApps() {
        allApps = InstalledAppsRepository.getAllLaunchableApps(context)
        // Drop selections for apps that were uninstalled in the meantime.
        val stillInstalled = allApps.map { it.packageName }.toSet()
        if (!selectedPackages.all { it in stillInstalled }) {
            selectedPackages = selectedPackages.intersect(stillInstalled)
            prefs.setSelectedPackages(selectedPackages)
        }
    }

    LaunchedEffect(Unit) { refreshApps() }

    // Effective PIN: the remotely configured exit PIN wins over the local one when present.
    val effectivePin = config?.kiosk?.exitPin?.takeIf { it.isNotBlank() } ?: prefs.getPin()

    // Effective game list: when the fleet config defines visible games that are actually
    // installed, those drive the grid (remote control); otherwise we fall back to the
    // locally chosen games so the app keeps working with no or stale config.
    val installedByPkg = remember(allApps) { allApps.associateBy { it.packageName } }
    val remoteGames = config?.games.orEmpty()
        .filter { it.visible && it.packageName != null && installedByPkg.containsKey(it.packageName) }
    val usingRemoteGames = remoteGames.isNotEmpty()
    val effectiveGames: List<GameApp> = if (usingRemoteGames) {
        remoteGames.map { rg ->
            val base = installedByPkg[rg.packageName]!!
            if (rg.displayName != null) base.copy(label = rg.displayName) else base
        }
    } else {
        allApps.filter { it.packageName in selectedPackages }
    }

    // Keep the kiosk allow-list in sync with whatever is actually playable right now.
    val allowedPackages = if (usingRemoteGames) remoteGames.mapNotNull { it.packageName }.toSet() else selectedPackages
    LaunchedEffect(allowedPackages) { KioskManager.syncAllowedPackages(context, allowedPackages) }

    // Single-game stations: boot straight into the configured package each time we land on Home.
    val autoLaunch = config?.kiosk?.autoLaunchPackage
    LaunchedEffect(autoLaunch, resumeTick, screen) {
        if (screen == Screen.HOME && autoLaunch != null && installedByPkg.containsKey(autoLaunch)) {
            fleetApp.telemetryManager.recordGameLaunch(autoLaunch)
            InstalledAppsRepository.launch(context, autoLaunch)
        }
    }

    // Idle timeout: if the operator leaves a sub-screen open (Settings/PIN/Diagnostics)
    // and nobody touches the screen for idleTimeoutSeconds, return to the clean grid.
    // Runs only off the HOME screen, so it never disturbs the grid itself nor a running
    // game (while a game is foreground the launcher is on HOME behind it).
    val idleTimeoutSeconds = config?.kiosk?.idleTimeoutSeconds ?: 0
    LaunchedEffect(idleTimeoutSeconds, screen, interactionTick) {
        if (idleTimeoutSeconds > 0 && screen != Screen.HOME) {
            kotlinx.coroutines.delay(idleTimeoutSeconds * 1000L)
            screen = Screen.HOME
        }
    }

    val colorScheme = rememberColorScheme(config?.branding)

    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val operational = config?.operational
            when {
                operational?.status == "maintenance" && screen == Screen.HOME -> MaintenanceScreen(
                    message = operational.maintenanceMessage,
                    branding = config?.branding,
                    onRequestSettings = { screen = Screen.PIN_ENTRY },
                )
                screen == Screen.HOME -> HomeScreen(
                    games = effectiveGames,
                    columns = config?.layout?.columns ?: 0,
                    showLabels = config?.layout?.showLabels ?: true,
                    banner = operational?.banner,
                    branding = config?.branding,
                    onPlay = { pkg ->
                        fleetApp.telemetryManager.recordGameLaunch(pkg)
                        if (!InstalledAppsRepository.launch(context, pkg)) {
                            Toast.makeText(context, "Gioco non disponibile", Toast.LENGTH_SHORT).show()
                            refreshApps()
                        }
                    },
                    onRequestSettings = { screen = Screen.PIN_ENTRY },
                )
                screen == Screen.PIN_ENTRY -> PinEntryScreen(
                    expectedPin = effectivePin,
                    onSuccess = { screen = Screen.SETTINGS },
                    onCancel = { screen = Screen.HOME },
                )
                screen == Screen.SETTINGS -> SettingsScreen(
                    allApps = allApps,
                    selectedPackages = selectedPackages,
                    currentPin = prefs.getPin(),
                    remoteControlled = usingRemoteGames,
                    onToggle = { pkg, checked ->
                        selectedPackages = if (checked) selectedPackages + pkg else selectedPackages - pkg
                        prefs.setSelectedPackages(selectedPackages)
                    },
                    onChangePin = { newPin -> prefs.setPin(newPin) },
                    onOpenDiagnostics = { screen = Screen.DIAGNOSTICS },
                    onDone = { screen = Screen.HOME },
                )
                screen == Screen.DIAGNOSTICS -> DiagnosticsScreen(
                    config = config,
                    connectionStatus = connectionStatus,
                    onBack = { screen = Screen.SETTINGS },
                )
            }
        }
    }
}

// Palette sampled from the Kids Fun Planet logo.
private val LogoCoral = Color(0xFFCC7375)
private val LogoPink = Color(0xFFD89FA6)
private val LogoTan = Color(0xFFD1B085)
private val LogoSage = Color(0xFF8A9978)
private val LogoTeal = Color(0xFF7AACAD)
private val LogoSky = Color(0xFFB5DCEB)
private val LogoYellow = Color(0xFFFDCE72)

private fun parseColorOrNull(hex: String?): Color? =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

@Composable
private fun rememberColorScheme(branding: Branding?) = lightColorScheme(
    primary = parseColorOrNull(branding?.primaryColor) ?: Color(0xFF3346D6),
    secondary = parseColorOrNull(branding?.accentColor) ?: LogoYellow,
    background = Color(0xFFF0F4FF),
    surface = Color.White,
)

/** Background: a remote branding image if the config supplies one (downloaded once),
 * otherwise the built-in logo gradient. */
@Composable
private fun LauncherBackground(branding: Branding?, modifier: Modifier = Modifier) {
    val backgroundUrl = branding?.backgroundUrl
    var remoteBg by remember(backgroundUrl) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(backgroundUrl) {
        if (backgroundUrl != null) remoteBg = RemoteImageLoader.load(backgroundUrl)
    }
    val bmp = remoteBg
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        LogoBackground(modifier)
    }
}

@Composable
private fun LogoBackground(modifier: Modifier = Modifier) {
    val gradient = Brush.linearGradient(
        colors = listOf(LogoCoral, LogoPink, LogoTan, LogoSage, LogoTeal, LogoSky),
    )
    Box(modifier = modifier.fillMaxSize().background(gradient)) {
        Image(
            painter = painterResource(id = R.drawable.logo_fun_planet),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alpha = 0.22f,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.92f),
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
    games: List<GameApp>,
    columns: Int,
    showLabels: Boolean,
    banner: Banner?,
    branding: Branding?,
    onPlay: (String) -> Unit,
    onRequestSettings: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LauncherBackground(branding)

        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.55f))
                    .combinedClickable(onClick = {}, onLongClick = onRequestSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "Impostazioni", tint = Color.White)
            }
            Spacer(Modifier.height(12.dp))

            if (banner?.enabled == true && banner.text.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.85f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = banner.text,
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            if (games.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Nessun gioco configurato.\nTieni premuto l'icona in alto per aprire le impostazioni.",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                val gridCells = if (columns > 0) GridCells.Fixed(columns) else GridCells.Adaptive(minSize = 160.dp)
                LazyVerticalGrid(
                    columns = gridCells,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(games, key = { it.packageName }) { game ->
                        GameTile(game = game, showLabel = showLabels, onClick = { onPlay(game.packageName) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GameTile(game: GameApp, showLabel: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AndroidView(
            factory = { ctx -> ImageView(ctx) },
            update = { it.setImageDrawable(game.icon) },
            modifier = Modifier.size(96.dp),
        )
        if (showLabel) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = game.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MaintenanceScreen(
    message: String,
    branding: Branding?,
    onRequestSettings: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LauncherBackground(branding)
        // Hidden long-press affordance so an operator can still reach settings.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(64.dp)
                .combinedClickable(onClick = {}, onLongClick = onRequestSettings),
        )
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))) {
                Text(
                    text = message.ifBlank { "Postazione temporaneamente fuori servizio. Torna presto!" },
                    modifier = Modifier.padding(28.dp),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PinEntryScreen(expectedPin: String, onSuccess: () -> Unit, onCancel: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onCancel) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("Area riservata ai genitori", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= 8) { input = it; error = false } },
                    label = { Text("PIN") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    ),
                    isError = error,
                )
                if (error) {
                    Text("PIN errato", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Spacer(Modifier.height(16.dp))
                Row {
                    TextButton(onClick = onCancel) { Text("Annulla") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (input == expectedPin) onSuccess() else error = true
                    }) { Text("Entra") }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    allApps: List<GameApp>,
    selectedPackages: Set<String>,
    currentPin: String,
    remoteControlled: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onChangePin: (String) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    var newPin by remember { mutableStateOf("") }
    val kioskActive = remember { KioskManager.isDeviceOwner(context) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Impostazioni", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onOpenDiagnostics) { Text("Diagnostica") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onDone) { Text("Fatto") }
        }
        Spacer(Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (kioskActive) Color(0xFFE3F5E9) else Color(0xFFFDF0D9),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = if (kioskActive) "Modalità Kiosk: ATTIVA" else "Modalità Kiosk: NON ATTIVA",
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (kioskActive)
                        "L'app è bloccata a schermo intero: i bambini non possono uscire né aprire altre app."
                    else
                        "Per bloccare davvero l'uscita dall'app, configura questo tablet come dispositivo dedicato (vedi README.md, sezione Modalità Kiosk).",
                    fontSize = 12.sp,
                )
                if (kioskActive) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        (context as? android.app.Activity)?.let { KioskManager.release(it) }
                    }) { Text("Sblocca temporaneamente") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (remoteControlled) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE7EEFF)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "I giochi mostrati sono gestiti da remoto (config.json della flotta). " +
                        "Le selezioni qui sotto restano valide solo come riserva se la configurazione remota non è disponibile.",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        Text("Scegli quali giochi mostrare ai bambini:", color = Color.Gray)
        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            modifier = Modifier.weight(1f),
        ) {
            items(allApps, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AndroidView(
                        factory = { ctx -> ImageView(ctx) },
                        update = { it.setImageDrawable(app.icon) },
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, modifier = Modifier.weight(1f))
                    Checkbox(
                        checked = app.packageName in selectedPackages,
                        onCheckedChange = { checked -> onToggle(app.packageName, checked) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Cambia PIN genitori (attuale: $currentPin):", color = Color.Gray)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newPin,
                onValueChange = { if (it.length <= 8) newPin = it },
                label = { Text("Nuovo PIN") },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (newPin.isNotBlank()) {
                    onChangePin(newPin)
                    newPin = ""
                }
            }) { Text("Salva") }
        }
    }
}

@Composable
private fun DiagnosticsScreen(
    config: ResolvedConfig?,
    connectionStatus: ConnectionStatus,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val deviceId = remember { DeviceIdManager.getDeviceId(context) }
    val devicePrefs = remember { DevicePrefs(context) }
    val appVersion = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        }.getOrDefault(0)
    }
    val lastTelemetry = remember {
        val millis = devicePrefs.getLastTelemetrySuccessMillis()
        if (millis == 0L) "mai" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(millis))
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Diagnostica", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = onBack) { Text("Indietro") }
        }
        Spacer(Modifier.height(16.dp))

        DiagnosticRow("ID dispositivo", deviceId)
        DiagnosticRow("Etichetta (label)", config?.deviceLabel ?: "— (non assegnata nel config remoto)")
        DiagnosticRow("Versione app", appVersion.toString())
        DiagnosticRow("Versione config attiva", config?.configVersion?.toString() ?: "nessuna")
        DiagnosticRow("Ultimo contatto telemetria", lastTelemetry)
        DiagnosticRow(
            "Stato connessione config",
            if (connectionStatus == ConnectionStatus.ONLINE) "online" else "offline",
        )
        DiagnosticRow("Stato operativo", config?.operational?.status ?: "—")

        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4FF))) {
            Text(
                text = "Comunica al gestore l'ID dispositivo qui sopra per aggiungere o configurare " +
                    "questo monitor nel file config.json della flotta.",
                modifier = Modifier.padding(12.dp),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Divider(Modifier.padding(top = 6.dp))
    }
}
