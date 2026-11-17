package com.rallycopilot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

private data class PlaceSearchResult(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

@Composable
fun PlaceSearchPanel(
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

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = panel,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text("КУДА ЕДЕМ?", color = lime, fontSize = 10.sp)
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Адрес или место") },
                    enabled = !loading
                )
                Button(
                    onClick = {
                        if (query.isBlank()) {
                            error = "Введи адрес или название места"
                        } else {
                            loading = true
                            error = ""
                            results = emptyList()
                            scope.launch {
                                try {
                                    results = searchPlaces(query.trim())
                                    if (results.isEmpty()) error = "Ничего не найдено. Уточни запрос."
                                } catch (e: Exception) {
                                    error = "Поиск не удался. Проверь интернет и повтори."
                                } finally {
                                    loading = false
                                }
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
                LazyColumn(modifier = Modifier.height((results.size * 58).coerceAtMost(220).dp)) {
                    items(results) { place ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPlaceSelected(LatLng(place.latitude, place.longitude))
                                    results = emptyList()
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(place.name.substringBefore(",").ifBlank { place.name }, color = Color.White, fontSize = 13.sp)
                            Text(place.name, color = Color.LightGray, fontSize = 10.sp, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

private suspend fun searchPlaces(query: String): List<PlaceSearchResult> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(query, "UTF-8")
    val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=jsonv2&limit=5&addressdetails=1")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000
        readTimeout = 12000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "RallyCopilot/0.6 Android address search")
        setRequestProperty("Accept-Language", "ru,en")
    }
    try {
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONArray(body)
        (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val lat = item.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
            val lon = item.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
            val name = item.optString("display_name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PlaceSearchResult(name, lat, lon)
        }
    } finally {
        connection.disconnect()
    }
}
