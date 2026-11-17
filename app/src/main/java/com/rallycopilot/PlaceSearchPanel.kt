package com.rallycopilot

import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.maplibre.android.geometry.LatLng
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.cos

private data class PlaceSearchResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float? = null
)

@Composable
fun PlaceSearchPanel(
    currentLocation: Location?,
    onPlaceSelected: (LatLng) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceSearchResult>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lime = Color(0xFFB7F34A)
    val panel = Color(0xF21B2329)

    Surface(modifier = modifier.fillMaxWidth(), color = panel, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text("КУДА ЕДЕМ?", color = lime, fontSize = 10.sp)
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f),
                    singleLine = true, placeholder = { Text("Адрес или место") }, enabled = !loading
                )
                Button(
                    onClick = {
                        if (query.isBlank()) error = "Введи адрес или название места"
                        else {
                            loading = true
                            error = ""
                            results = emptyList()
                            scope.launch {
                                try {
                                    results = searchPlaces(query.trim(), currentLocation)
                                    if (results.isEmpty()) error = "Ничего не найдено. Уточни запрос."
                                } catch (_: Exception) {
                                    error = "Поиск не удался. Проверь интернет и повтори."
                                } finally { loading = false }
                            }
                        }
                    },
                    enabled = !loading && query.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = lime, contentColor = Color(0xFF101418))
                ) {
                    if (loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(18.dp).height(18.dp))
                    else Text("НАЙТИ", fontSize = 10.sp)
                }
            }
            if (error.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(error, color = Color(0xFFFF9B83), fontSize = 11.sp)
            }
            if (results.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                if (currentLocation != null) Text("БЛИЖАЙШИЕ РЕЗУЛЬТАТЫ ВЫШЕ", color = lime, fontSize = 9.sp)
                LazyColumn(modifier = Modifier.height((results.size * 64).coerceAtMost(240).dp)) {
                    items(results) { place ->
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                onPlaceSelected(LatLng(place.latitude, place.longitude))
                                results = emptyList()
                            }.padding(vertical = 8.dp)
                        ) {
                            Text(place.name.substringBefore(",").ifBlank { place.name }, color = Color.White, fontSize = 13.sp)
                            Text(place.name, color = Color.LightGray, fontSize = 10.sp, maxLines = 2)
                            place.distanceMeters?.let { Text(formatPlaceDistance(it), color = lime, fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun searchPlaces(query: String, currentLocation: Location?): List<PlaceSearchResult> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(query, "UTF-8")
    val viewbox = currentLocation?.let { location ->
        val latitudeDelta = 0.18
        val longitudeDelta = 0.18 / cos(Math.toRadians(location.latitude)).coerceAtLeast(0.15)
        val west = location.longitude - longitudeDelta
        val east = location.longitude + longitudeDelta
        val north = location.latitude + latitudeDelta
        val south = location.latitude - latitudeDelta
        "&viewbox=$west,$north,$east,$south&bounded=0"
    }.orEmpty()
    val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=jsonv2&limit=10&addressdetails=1$viewbox")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000
        readTimeout = 12000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "RallyCopilot/0.6.2 Android address search")
        setRequestProperty("Accept-Language", "ru,en")
    }
    try {
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONArray(body)
        val found = (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val lat = item.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
            val lon = item.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
            val name = item.optString("display_name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val distance = currentLocation?.let {
                val output = FloatArray(1)
                Location.distanceBetween(it.latitude, it.longitude, lat, lon, output)
                output[0]
            }
            PlaceSearchResult(name, lat, lon, distance)
        }
        if (currentLocation == null) found else found.sortedBy { it.distanceMeters ?: Float.MAX_VALUE }
    } finally { connection.disconnect() }
}

private fun formatPlaceDistance(meters: Float): String =
    if (meters >= 1000f) "≈ %.1f км от вас".format(meters / 1000f) else "≈ ${meters.toInt()} м от вас"
