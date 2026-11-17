package com.rallycopilot

import android.location.Location
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

private data class RallyWeather(
    val temperature: Int,
    val feelsLike: Int,
    val wind: Int,
    val precipitation: Double,
    val description: String
)

@Composable
fun RallyWeatherCard(location: Location?, modifier: Modifier = Modifier) {
    var weather by remember { mutableStateOf<RallyWeather?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val latitudeKey = location?.latitude?.times(100)?.roundToInt()
    val longitudeKey = location?.longitude?.times(100)?.roundToInt()

    LaunchedEffect(latitudeKey, longitudeKey) {
        if (latitudeKey == null || longitudeKey == null) {
            weather = null
            return@LaunchedEffect
        }
        loading = true
        failed = false
        try {
            weather = fetchRallyWeather(latitudeKey / 100.0, longitudeKey / 100.0)
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }

    Surface(modifier.fillMaxWidth(), color = Color(0xE51B2329), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
            Text("ПОГОДА РЯДОМ", color = Color(0xFFB7F34A), fontSize = 9.sp)
            when {
                location == null -> Text("Включи GPS, чтобы узнать погоду по местоположению", color = Color.White, fontSize = 11.sp)
                loading -> Row {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFFB7F34A))
                    Spacer(Modifier.width(8.dp))
                    Text("Получаю прогноз…", color = Color.White, fontSize = 11.sp)
                }
                weather != null -> {
                    val value = weather!!
                    Text("${value.temperature}°C · ${value.description}", color = Color.White, fontSize = 13.sp)
                    Text("Ощущается ${value.feelsLike}° · ветер ${value.wind} км/ч · осадки ${"%.1f".format(value.precipitation)} мм", color = Color.LightGray, fontSize = 10.sp)
                }
                failed -> Text("Погода временно недоступна. Проверь интернет.", color = Color(0xFFFFA08A), fontSize = 10.sp)
                else -> Text("Нет данных о погоде", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

private suspend fun fetchRallyWeather(latitude: Double, longitude: Double): RallyWeather = withContext(Dispatchers.IO) {
    val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m&timezone=auto")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 9000
        readTimeout = 10000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "RallyCopilot/0.6.3 Android weather")
    }
    try {
        val current = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
        val code = current.optInt("weather_code", -1)
        val description = when (code) {
            0 -> "ясно"
            1 -> "преимущественно ясно"
            2 -> "переменная облачность"
            3 -> "пасмурно"
            45, 48 -> "туман"
            51, 53, 55 -> "морось"
            56, 57 -> "ледяная морось"
            61, 63, 65 -> "дождь"
            66, 67 -> "ледяной дождь"
            71, 73, 75, 77 -> "снег"
            80, 81, 82 -> "ливень"
            85, 86 -> "снегопад"
            95, 96, 99 -> "гроза"
            else -> "условия уточняются"
        }
        RallyWeather(
            current.optDouble("temperature_2m").roundToInt(),
            current.optDouble("apparent_temperature").roundToInt(),
            current.optDouble("wind_speed_10m").roundToInt(),
            current.optDouble("precipitation"),
            description
        )
    } finally {
        connection.disconnect()
    }
}
