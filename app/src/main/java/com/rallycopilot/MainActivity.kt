package com.rallycopilot

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rallycopilot.game.RallyGameRepository
import com.rallycopilot.game.RallyGameState
import org.maplibre.android.MapLibre
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady by mutableStateOf(false)
    private var availableVoices by mutableStateOf<List<Voice>>(emptyList())
    private var selectedVoiceName by mutableStateOf("")
    private var voiceEnabled by mutableStateOf(true)
    private var speechRate by mutableFloatStateOf(1.0f)
    private var speechPitch by mutableFloatStateOf(1.0f)
    private var currentLocation by mutableStateOf<Location?>(null)
    private lateinit var gameRepository: RallyGameRepository
    private val locationManager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private val locationListener = LocationListener { location ->
        currentLocation = location
        if (::gameRepository.isInitialized) gameRepository.onLocationChanged(location)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(applicationContext)
        val prefs = getSharedPreferences("rally_copilot", MODE_PRIVATE)
        gameRepository = RallyGameRepository(getSharedPreferences("rally_game", MODE_PRIVATE))
        selectedVoiceName = prefs.getString("voice_name", "") ?: ""
        voiceEnabled = prefs.getBoolean("voice_enabled", true)
        speechRate = prefs.getFloat("speech_rate", 1.0f).coerceIn(0.75f, 1.25f)
        speechPitch = prefs.getFloat("speech_pitch", 1.0f).coerceIn(0.8f, 1.2f)
        tts = TextToSpeech(this, this)
        setContent {
            val gameState by gameRepository.state
            RallyCopilotApp(
                voices = availableVoices,
                selectedVoiceName = selectedVoiceName,
                voiceReady = ttsReady,
                voiceEnabled = voiceEnabled,
                speechRate = speechRate,
                speechPitch = speechPitch,
                onVoiceEnabledChanged = { enabled ->
                    voiceEnabled = enabled
                    prefs.edit().putBoolean("voice_enabled", enabled).apply()
                    if (!enabled) tts?.stop()
                },
                onSpeechRateChanged = { rate ->
                    speechRate = rate.coerceIn(0.75f, 1.25f)
                    prefs.edit().putFloat("speech_rate", speechRate).apply()
                    tts?.setSpeechRate(speechRate)
                },
                onSpeechPitchChanged = { pitch ->
                    speechPitch = pitch.coerceIn(0.8f, 1.2f)
                    prefs.edit().putFloat("speech_pitch", speechPitch).apply()
                    tts?.setPitch(speechPitch)
                },
                onVoiceSelected = { voice ->
                    selectedVoiceName = voice.name
                    prefs.edit().putString("voice_name", voice.name).apply()
                    tts?.voice = voice
                },
                onTestVoice = { speakSample() },
                currentLocation = currentLocation,
                onEnableGps = { startLocationTracking() },
                onSpeak = { phrase -> speak(phrase) },
                gameRepository = gameRepository,
                gameState = gameState
            )
        }
    }

    private fun startLocationTracking() {
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        try {
            val provider = if (fine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            locationManager.getLastKnownLocation(provider)?.let {
                currentLocation = it
                gameRepository.onLocationChanged(it)
            }
            if (fine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500L, 3f, locationListener)
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 8f, locationListener)
            }
        } catch (_: SecurityException) { }
    }

    private fun stopLocationTracking() {
        try { locationManager.removeUpdates(locationListener) } catch (_: SecurityException) { }
    }

    override fun onStart() {
        super.onStart()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationTracking()
        }
    }

    override fun onStop() {
        stopLocationTracking()
        super.onStop()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            tts?.language = Locale("ru", "RU")
            tts?.setSpeechRate(speechRate)
            tts?.setPitch(speechPitch)
            val all = tts?.voices?.toList().orEmpty()
            availableVoices = all.filter { it.locale.language == "ru" }
                .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenByDescending { it.quality }.thenBy { it.name })
            if (availableVoices.isEmpty()) availableVoices = all.sortedBy { it.name }
            val chosen = availableVoices.firstOrNull { it.name == selectedVoiceName } ?: availableVoices.firstOrNull()
            if (chosen != null) {
                selectedVoiceName = chosen.name
                tts?.voice = chosen
                getSharedPreferences("rally_copilot", MODE_PRIVATE).edit().putString("voice_name", chosen.name).apply()
            }
        }
    }

    private fun speakSample() = speak("Приготовиться! Через триста метров правый четыре. Не режь, там сюрприз.")

    private fun speak(phrase: String) {
        if (!voiceEnabled || !ttsReady || phrase.isBlank()) return
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f) }
        tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, "rally_" + System.currentTimeMillis())
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun RallyCopilotApp(
    voices: List<Voice>,
    selectedVoiceName: String,
    voiceReady: Boolean,
    voiceEnabled: Boolean,
    speechRate: Float,
    speechPitch: Float,
    onVoiceEnabledChanged: (Boolean) -> Unit,
    onSpeechRateChanged: (Float) -> Unit,
    onSpeechPitchChanged: (Float) -> Unit,
    onVoiceSelected: (Voice) -> Unit,
    onTestVoice: () -> Unit,
    currentLocation: Location?,
    onEnableGps: () -> Unit,
    onSpeak: (String) -> Unit,
    gameRepository: RallyGameRepository,
    gameState: RallyGameState
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val night = Color(0xFF101418)
    val lime = Color(0xFFB7F34A)
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = night, surface = Color(0xFF1B2329), primary = lime, onPrimary = night,
            onBackground = Color.White, onSurface = Color.White
        )
    ) {
        Scaffold(
            containerColor = night,
            bottomBar = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val labels = listOf("Заезд", "Гараж", "Магазин", "Достижения", "Профиль")
                    val icons = listOf(Icons.Default.Map, Icons.Default.Garage, Icons.Default.ShoppingCart, Icons.Default.EmojiEvents, Icons.Default.Person)
                    labels.forEachIndexed { index, label ->
                        Column(
                            Modifier.weight(1f).clickable { selectedTab = index }.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(icons[index], contentDescription = label, tint = if (selectedTab == index) lime else Color.White)
                            Text(label, color = if (selectedTab == index) lime else Color.White, fontSize = 10.sp)
                        }
                    }
                }
            }
        ) { padding ->
            when (selectedTab) {
                0 -> MapLibreNavigationScreen(currentLocation, onEnableGps, onSpeak, gameRepository, gameState, Modifier.padding(padding))
                1 -> RallyGarageScreen(Modifier.padding(padding))
                2 -> RallyShopScreen(gameState, gameRepository, Modifier.padding(padding))
                3 -> RallyAchievementsScreen(gameState, Modifier.padding(padding))
                else -> ProfileScreen(
                    voices, selectedVoiceName, voiceReady, voiceEnabled, speechRate, speechPitch,
                    onVoiceEnabledChanged, onSpeechRateChanged, onSpeechPitchChanged,
                    onVoiceSelected, onTestVoice, gameState, Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    voices: List<Voice>,
    selectedVoiceName: String,
    voiceReady: Boolean,
    voiceEnabled: Boolean,
    speechRate: Float,
    speechPitch: Float,
    onVoiceEnabledChanged: (Boolean) -> Unit,
    onSpeechRateChanged: (Float) -> Unit,
    onSpeechPitchChanged: (Float) -> Unit,
    onVoiceSelected: (Voice) -> Unit,
    onTestVoice: () -> Unit,
    gameState: RallyGameState,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = voices.firstOrNull { it.name == selectedVoiceName }
    val lime = Color(0xFFB7F34A)
    val panel = Color(0xFF1B2329)
    val muted = Color(0xFF9AA7AE)

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("ПРОФИЛЬ ПИЛОТА", color = lime, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Твой Rally Copilot", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        RallyProfileStats(gameState, Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Surface(color = panel, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("ГОЛОС ШТУРМАНА", color = muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(selected?.let { "${it.name} · ${it.locale.displayName}" } ?: "Загрузка голосов…", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Box {
                    OutlinedTextField(
                        value = selected?.name ?: if (voiceReady) "Нет доступных голосов" else "Загрузка…",
                        onValueChange = {}, readOnly = true, label = { Text("Выбрать голос") },
                        trailingIcon = { Icon(Icons.Default.Person, contentDescription = "Открыть список голосов") },
                        modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                        enabled = voices.isNotEmpty()
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        voices.forEach { voice ->
                            DropdownMenuItem(
                                text = { Text("${voice.name} (${voice.locale.displayName})") },
                                onClick = { onVoiceSelected(voice); expanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (voices.isEmpty() && voiceReady) "На устройстве не найдено голосов TTS. Установи русский голос в настройках Android."
                    else "Используются голоса, установленные в системе Android.",
                    color = muted, fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onTestVoice, enabled = voiceReady && selected != null && voiceEnabled, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = lime, contentColor = Color(0xFF101418))
                ) { Text("ПРОСЛУШАТЬ ШТУРМАНА", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("ГОЛОСОВЫЕ ПОДСКАЗКИ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Команды по маршруту и предупреждения о манёврах", color = muted, fontSize = 11.sp)
                    }
                    Switch(checked = voiceEnabled, onCheckedChange = onVoiceEnabledChanged)
                }
                Spacer(Modifier.height(10.dp))
                Text("СКОРОСТЬ РЕЧИ · " + "%.2f×".format(speechRate), color = muted, fontSize = 11.sp)
                Slider(value = speechRate, onValueChange = onSpeechRateChanged, valueRange = 0.75f..1.25f)
                Text("ВЫСОТА ГОЛОСА · " + "%.2f×".format(speechPitch), color = muted, fontSize = 11.sp)
                Slider(value = speechPitch, onValueChange = onSpeechPitchChanged, valueRange = 0.8f..1.2f)
            }
        }
        Spacer(Modifier.height(14.dp))
        Surface(color = panel, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("СТИЛЬ ШТУРМАНА", color = muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Короткие команды по делу", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("«Через 500 метров поверни направо. Дальше держись главной дороги.»", color = lime, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Штурман заранее предупреждает о поворотах, развязках и финише. При уходе с маршрута предупредит о перестроении.", color = muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Surface(color = panel, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("ВЕРСИЯ ПРИЛОЖЕНИЯ", color = muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Rally Copilot 0.9.0", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Игровой профиль · MapLibre · OpenStreetMap", color = muted, fontSize = 11.sp)
            }
        }
    }
}
