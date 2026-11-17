package com.rallycopilot

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import org.maplibre.android.MapLibre
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady by androidx.compose.runtime.mutableStateOf(false)
    private var availableVoices by androidx.compose.runtime.mutableStateOf<List<Voice>>(emptyList())
    private var selectedVoiceName by androidx.compose.runtime.mutableStateOf("")
    private var currentLocation by androidx.compose.runtime.mutableStateOf<Location?>(null)
    private val locationManager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private val locationListener = LocationListener { location -> currentLocation = location }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(applicationContext)
        val prefs = getSharedPreferences("rally_copilot", MODE_PRIVATE)
        selectedVoiceName = prefs.getString("voice_name", "") ?: ""
        tts = TextToSpeech(this, this)
        setContent {
            RallyCopilotApp(
                voices = availableVoices,
                selectedVoiceName = selectedVoiceName,
                voiceReady = ttsReady,
                onVoiceSelected = { voice ->
                    selectedVoiceName = voice.name
                    prefs.edit().putString("voice_name", voice.name).apply()
                    tts?.voice = voice
                },
                onTestVoice = { speakSample() },
                currentLocation = currentLocation,
                onEnableGps = { startLocationTracking() },
                onSpeak = { phrase -> speak(phrase) }
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
            locationManager.getLastKnownLocation(provider)?.let { currentLocation = it }
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
            tts?.language = Locale.getDefault()
            val all = tts?.voices?.toList().orEmpty()
            availableVoices = all.filter { it.locale.language == "ru" }
                .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenBy { it.name })
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
        if (ttsReady) tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "rally_callout")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@androidx.compose.runtime.Composable
fun RallyCopilotApp(
    voices: List<Voice>,
    selectedVoiceName: String,
    voiceReady: Boolean,
    onVoiceSelected: (Voice) -> Unit,
    onTestVoice: () -> Unit,
    currentLocation: Location?,
    onEnableGps: () -> Unit,
    onSpeak: (String) -> Unit
) {
    var selectedTab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val night = androidx.compose.ui.graphics.Color(0xFF101418)
    val panel = androidx.compose.ui.graphics.Color(0xFF1B2329)
    val lime = androidx.compose.ui.graphics.Color(0xFFB7F34A)
    androidx.compose.material3.MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            background = night, surface = panel, primary = lime, onPrimary = night,
            onBackground = androidx.compose.ui.graphics.Color.White,
            onSurface = androidx.compose.ui.graphics.Color.White
        )
    ) {
        androidx.compose.material3.Scaffold(
            containerColor = night,
            bottomBar = {
                androidx.compose.foundation.layout.Row(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
                ) {
                    val items = listOf("Заезд", "Гараж", "Рейтинг", "Профиль")
                    val icons = listOf(
                        androidx.compose.material.icons.Icons.Default.Map,
                        androidx.compose.material.icons.Icons.Default.Garage,
                        androidx.compose.material.icons.Icons.Default.EmojiEvents,
                        androidx.compose.material.icons.Icons.Default.Person
                    )
                    items.forEachIndexed { index, label ->
                        androidx.compose.foundation.layout.Column(
                            modifier = androidx.compose.ui.Modifier
                                .weight(1f)
                                .clickable { selectedTab = index }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                        ) {
                            androidx.compose.material3.Icon(
                                icons[index],
                                contentDescription = label,
                                tint = if (selectedTab == index) lime else androidx.compose.ui.graphics.Color.White
                            )
                            androidx.compose.material3.Text(
                                label,
                                color = if (selectedTab == index) lime else androidx.compose.ui.graphics.Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        ) { padding ->
            when (selectedTab) {
                0 -> MapLibreNavigationScreen(currentLocation, onEnableGps, onSpeak, androidx.compose.ui.Modifier.padding(padding))
                1 -> PlaceholderScreen("Гараж", androidx.compose.ui.Modifier.padding(padding))
                2 -> PlaceholderScreen("Рейтинг", androidx.compose.ui.Modifier.padding(padding))
                else -> ProfileScreen(voices, selectedVoiceName, voiceReady, onVoiceSelected, onTestVoice, androidx.compose.ui.Modifier.padding(padding))
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ProfileScreen(
    voices: List<Voice>, selectedVoiceName: String, voiceReady: Boolean,
    onVoiceSelected: (Voice) -> Unit, onTestVoice: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val selected = voices.firstOrNull { it.name == selectedVoiceName }
    val lime = androidx.compose.ui.graphics.Color(0xFFB7F34A)
    val panel = androidx.compose.ui.graphics.Color(0xFF1B2329)
    val muted = androidx.compose.ui.graphics.Color(0xFF9AA7AE)
    androidx.compose.foundation.layout.Column(
        modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp)
    ) {
        androidx.compose.material3.Text("НАСТРОЙКИ ШТУРМАНА", color = lime, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        androidx.compose.material3.Text("Голос и характер", color = androidx.compose.ui.graphics.Color.White, fontSize = 27.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
        androidx.compose.material3.Surface(color = panel, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
            androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
                androidx.compose.material3.Text("ГОЛОС", color = muted, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Text(selected?.let { "${it.name} · ${it.locale.displayName}" } ?: "Загрузка голосов…", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp)
                androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.Box {
                    androidx.compose.material3.OutlinedTextField(
                        value = selected?.name ?: if (voiceReady) "Нет доступных голосов" else "Загрузка…",
                        onValueChange = {},
                        readOnly = true,
                        label = { androidx.compose.material3.Text("Выбрать голос") },
                        trailingIcon = {
                            androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.Default.Map,
                                contentDescription = "Открыть список голосов"
                            )
                        },
                        modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                        enabled = voices.isNotEmpty()
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        voices.forEach { voice ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { androidx.compose.material3.Text("${voice.name} (${voice.locale.displayName})") },
                                onClick = { onVoiceSelected(voice); expanded = false }
                            )
                        }
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Text(
                    if (voices.isEmpty() && voiceReady) "На устройстве не найдено голосов TTS. Установи русский голос в настройках Android."
                    else "Доступны голоса, установленные в системе Android.", color = muted, fontSize = 11.sp
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Button(onClick = onTestVoice, enabled = voiceReady && selected != null, modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = lime, contentColor = androidx.compose.ui.graphics.Color(0xFF101418))) {
                    androidx.compose.material3.Text("ПРОСЛУШАТЬ ШТУРМАНА", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
        androidx.compose.material3.Surface(color = panel, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
            androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
                androidx.compose.material3.Text("МАНЕРА РЕЧИ", color = muted, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Text("Раллийный сленг с юмором и характером", color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Text("«Приготовиться! Через триста метров правый четыре. Не режь, там сюрприз.»", color = lime, fontSize = 16.sp)
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Text("Команды будут короткими и понятными. Шутки не мешают важным подсказкам.", color = muted, fontSize = 12.sp)
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
        androidx.compose.material3.Surface(color = panel, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
            androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
                androidx.compose.material3.Text("ВЕРСИЯ ПРИЛОЖЕНИЯ", color = muted, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                androidx.compose.material3.Text("Rally Copilot 0.6.2", color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                androidx.compose.material3.Text("Тестовая сборка · MapLibre / OpenStreetMap", color = muted, fontSize = 11.sp)
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
        androidx.compose.material3.Surface(color = panel, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
            androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
                androidx.compose.material3.Text("КАРТА И РЕЖИМ", color = muted, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Text("MapLibre · тёмная спортивная карта", color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                androidx.compose.material3.Text("OpenStreetMap · автомобильные маршруты OSRM", color = muted, fontSize = 12.sp)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PlaceholderScreen(title: String, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    androidx.compose.foundation.layout.Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        androidx.compose.material3.Text("$title\nРаздел будет добавлен следующим этапом", color = androidx.compose.ui.graphics.Color.White, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}
