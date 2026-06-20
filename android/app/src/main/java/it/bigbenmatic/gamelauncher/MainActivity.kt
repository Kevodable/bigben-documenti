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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

private enum class Screen { HOME, PIN_ENTRY, SETTINGS }

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        hideSystemBars()
        KioskManager.syncAllowedPackages(this, prefs.getSelectedPackages())

        setContent {
            LauncherApp(prefs = prefs)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        KioskManager.engage(this)
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
private fun LauncherApp(prefs: PrefsManager) {
    val context = LocalContext.current
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
    LaunchedEffect(selectedPackages) { KioskManager.syncAllowedPackages(context, selectedPackages) }

    MaterialTheme(colorScheme = kidsColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    games = allApps.filter { it.packageName in selectedPackages },
                    onPlay = { pkg ->
                        if (!InstalledAppsRepository.launch(context, pkg)) {
                            Toast.makeText(context, "Gioco non disponibile", Toast.LENGTH_SHORT).show()
                            refreshApps()
                        }
                    },
                    onRequestSettings = { screen = Screen.PIN_ENTRY },
                )
                Screen.PIN_ENTRY -> PinEntryScreen(
                    expectedPin = prefs.getPin(),
                    onSuccess = { screen = Screen.SETTINGS },
                    onCancel = { screen = Screen.HOME },
                )
                Screen.SETTINGS -> SettingsScreen(
                    allApps = allApps,
                    selectedPackages = selectedPackages,
                    currentPin = prefs.getPin(),
                    onToggle = { pkg, checked ->
                        selectedPackages = if (checked) selectedPackages + pkg else selectedPackages - pkg
                        prefs.setSelectedPackages(selectedPackages)
                    },
                    onChangePin = { newPin -> prefs.setPin(newPin) },
                    onDone = { screen = Screen.HOME },
                )
            }
        }
    }
}

@Composable
private fun kidsColorScheme() = lightColorScheme(
    primary = Color(0xFF3346D6),
    secondary = Color(0xFFFFC107),
    background = Color(0xFFF0F4FF),
    surface = Color.White,
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
    games: List<GameApp>,
    onPlay: (String) -> Unit,
    onRequestSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = onRequestSettings,
                ),
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_fun_planet),
                contentDescription = "Kids Fun Planet",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(90.dp),
            )
        }
        Spacer(Modifier.height(16.dp))

        if (games.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nessun gioco configurato.\nTieni premuto il logo per aprire le impostazioni.",
                    fontSize = 18.sp,
                    color = Color.Gray,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(games, key = { it.packageName }) { game ->
                    GameTile(game = game, onClick = { onPlay(game.packageName) })
                }
            }
        }
    }
}

@Composable
private fun GameTile(game: GameApp, onClick: () -> Unit) {
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
        Spacer(Modifier.height(8.dp))
        Text(
            text = game.label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
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
    onToggle: (String, Boolean) -> Unit,
    onChangePin: (String) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    var newPin by remember { mutableStateOf("") }
    val kioskActive = remember { KioskManager.isDeviceOwner(context) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Impostazioni", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
