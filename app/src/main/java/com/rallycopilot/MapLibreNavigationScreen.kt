package com.rallycopilot

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.net.URL

private val RallyLime = Color(0xFFB7F34A)
private val RallyPanel = Color(0xF21B2329)
private const val DARK_STYLE = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
private const val OSRM_BASE = "https://router.project-osrm.org/route/v1/driving/"

private data class RouteResult(
    val coordinates: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val firstInstruction: String
)

@Composable
fun MapLibreNavigationScreen(
    currentLocation: Location?,
    onEnableGps: () -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val latestLocation = rememberUpdatedState(currentLocation)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf<LatLng?>(null) }
    var routeStatus by remember { mutableStateOf("Нажми и удерживай точку на карте, чтобы выбрать финиш") }
    var routeDistance by remember { mutableStateOf("") }
    var routeEta by remember { mutableStateOf("") }
    var nextInstruction by remember { mutableStateOf("") }
    var routeBusy by remember { mutableStateOf(false) }
    var centeredOnGps by remember { mutableStateOf(false) }
    var lastRouteRequestAt by remember { mutableLongStateOf(0L) }
    val mapView = remember { MapView(context).apply { onCreate(null) } }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { mapInstance ->
            map = mapInstance
            mapInstance.setStyle(Style.Builder().fromUri(DARK_STYLE)) { style ->
                style.addSource(GeoJsonSource("rally-user-source", FeatureCollection.fromFeatures(emptyArray<Feature>())))
                style.addLayer(CircleLayer("rally-user-layer", "rally-user-source").withProperties(
                    circleColor("#B7F34A"), circleRadius(8f)
                ))
                style.addSource(GeoJsonSource("rally-destination-source", FeatureCollection.fromFeatures(emptyArray<Feature>())))
                style.addLayer(CircleLayer("rally-destination-layer", "rally-destination-source").withProperties(
                    circleColor("#FF5A36"), circleRadius(7f)
                ))
                style.addSource(GeoJsonSource("rally-route-source", FeatureCollection.fromFeatures(emptyArray<Feature>())))
                style.addLayer(LineLayer("rally-route-layer", "rally-route-source").withProperties(
                    lineColor("#B7F34A"), lineWidth(5f), lineCap("round"), lineJoin("round")
                ))
                styleReady = true
                mapInstance.addOnMapLongClickListener { point ->
                    val selected = LatLng(point.latitude, point.longitude)
                    destination = selected
                    routeDistance = ""
                    routeEta = ""
                    nextInstruction = ""
                    mapInstance.style?.getSourceAs<GeoJsonSource>("rally-destination-source")?.setGeoJson(
                        Feature.fromGeometry(Point.fromLngLat(selected.longitude, selected.latitude))
                    )
                    mapInstance.style?.getSourceAs<GeoJsonSource>("rally-route-source")?.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
                    val origin = latestLocation.value
                    if (origin == null) {
                        routeStatus = "Финиш выбран. Сначала включи GPS"
                    } else if (System.currentTimeMillis() - lastRouteRequestAt < 1100L) {
                        routeStatus = "Подожди секунду перед следующим расчётом маршрута"
                    } else {
                        lastRouteRequestAt = System.currentTimeMillis()
                        routeBusy = true
                        routeStatus = "Считаю маршрут. Штурман пока молчит, он занят математикой."
                        scope.launch {
                            try {
                                val result = fetchOsrmRoute(origin.latitude, origin.longitude, selected.latitude, selected.longitude)
                                routeDistance = formatDistance(result.distanceMeters)
                                routeEta = formatDuration(result.durationSeconds)
                                nextInstruction = result.firstInstruction
                                val line = LineString.fromLngLats(result.coordinates.map { Point.fromLngLat(it.longitude, it.latitude) })
                                mapInstance.style?.getSourceAs<GeoJsonSource>("rally-route-source")?.setGeoJson(Feature.fromGeometry(line))
                                routeStatus = "Маршрут построен. Это предварительный расчёт, не автопилот."
                                if (result.firstInstruction.isNotBlank()) onSpeak("Маршрут построен. ${result.firstInstruction}")
                                val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                                    .include(LatLng(origin.latitude, origin.longitude)).include(selected).build()
                                mapInstance.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80), 700)
                            } catch (e: Exception) {
                                routeStatus = "Не удалось построить маршрут: ${e.message ?: "проверь интернет"}"
                            } finally {
                                routeBusy = false
                            }
                        }
                    }
                    true
                }
            }
        }
    }

    LaunchedEffect(currentLocation, styleReady) {
        val location = currentLocation
        val activeMap = map
        if (location != null && styleReady && activeMap != null) {
            val point = LatLng(location.latitude, location.longitude)
            activeMap.style?.getSourceAs<GeoJsonSource>("rally-user-source")?.setGeoJson(
                Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude))
            )
            if (!centeredOnGps) {
                activeMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder().target(point).zoom(14.5).build()
                ), 700)
                centeredOnGps = true
            }
            if (destination == null) routeStatus = "GPS на связи. Выбери финиш долгим нажатием"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) onEnableGps()
    }

    Box(modifier.fillMaxSize().background(Color(0xFF101418))) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.TopStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = RallyPanel, shape = RoundedCornerShape(12.dp)) {
                Text("RALLY COPILOT · MAPLIBRE", color = RallyLime, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontSize = 11.sp)
            }
            Surface(color = RallyPanel, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(routeStatus, color = Color.White, fontSize = 12.sp)
                    if (routeBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = RallyLime)
                    if (routeDistance.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("$routeDistance · $routeEta", color = RallyLime, fontSize = 13.sp)
                    }
                    if (nextInstruction.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text("ПЕРВАЯ ПОДСКАЗКА", color = RallyLime, fontSize = 10.sp)
                        Text(nextInstruction, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
            color = RallyPanel, shape = RoundedCornerShape(8.dp)
        ) {
            Text("© OpenStreetMap contributors · © CARTO", color = Color.LightGray, fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Button(onClick = {
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (fine || coarse) onEnableGps() else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = RallyPanel, contentColor = RallyLime)) { Text("МОЁ МЕСТО", fontSize = 10.sp) }
            OutlinedButton(onClick = {
                destination = null; routeDistance = ""; routeEta = ""; nextInstruction = ""
                routeStatus = "Нажми и удерживай точку на карте, чтобы выбрать финиш"
                map?.style?.getSourceAs<GeoJsonSource>("rally-destination-source")?.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
                map?.style?.getSourceAs<GeoJsonSource>("rally-route-source")?.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("СБРОС", fontSize = 10.sp) }
        }
    }
}

private suspend fun fetchOsrmRoute(startLat: Double, startLon: Double, endLat: Double, endLon: Double): RouteResult = withContext(Dispatchers.IO) {
    val url = URL("$OSRM_BASE$startLon,$startLat;$endLon,$endLat?overview=full&geometries=geojson&steps=true")
    val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
        connectTimeout = 12000
        readTimeout = 15000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "RallyCopilot/0.3 Android navigation prototype")
    }
    try {
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        if (json.optString("code") != "Ok") error(json.optString("message", "OSRM вернул ошибку"))
        val route = json.getJSONArray("routes").getJSONObject(0)
        val coordinatesJson = route.getJSONObject("geometry").getJSONArray("coordinates")
        val coordinates = (0 until coordinatesJson.length()).map { i ->
            val pair = coordinatesJson.getJSONArray(i)
            LatLng(pair.getDouble(1), pair.getDouble(0))
        }
        val steps = route.getJSONArray("legs").getJSONObject(0).getJSONArray("steps")
        val maneuverStep = (0 until steps.length())
            .map { steps.getJSONObject(it) }
            .firstOrNull { it.getJSONObject("maneuver").optString("type") !in setOf("depart", "arrive") }
            ?: if (steps.length() > 0) steps.getJSONObject(0) else null
        val firstInstruction = maneuverStep?.let { step ->
            val maneuver = step.getJSONObject("maneuver")
            val street = step.optString("name").takeIf { it.isNotBlank() }
            val instruction = rallyInstruction(maneuver.optString("type"), maneuver.optString("modifier"), street)
            val metersToManeuver = step.optDouble("distance", 0.0)
            if (metersToManeuver > 0) "Через ${formatDistance(metersToManeuver)} $instruction" else instruction
        } ?: "Следуй по маршруту"
        RouteResult(coordinates, route.getDouble("distance"), route.getDouble("duration"), firstInstruction)
    } finally { connection.disconnect() }
}

private fun rallyInstruction(type: String, modifier: String, street: String?): String {
    val direction = when {
        modifier.contains("left") -> "налево"
        modifier.contains("right") -> "направо"
        modifier == "uturn" -> "разворот"
        modifier == "straight" -> "прямо"
        else -> "по маршруту"
    }
    val action = when (type) {
        "depart" -> "Старт. Держи курс"
        "arrive" -> "Финиш рядом"
        "roundabout", "rotary", "roundabout turn" -> "На круговом движении съезд $direction"
        "turn", "end of road", "fork", "continue", "new name", "merge", "on ramp", "off ramp" -> "Дальше $direction"
        else -> "Дальше $direction"
    }
    return if (street != null) "$action, на $street" else action
}

private fun formatDistance(meters: Double): String = if (meters >= 1000) "%.1f км".format(meters / 1000.0) else "${meters.toInt()} м"
private fun formatDuration(seconds: Double): String {
    val minutes = (seconds / 60).toInt()
    return if (minutes < 60) "$minutes мин" else "${minutes / 60} ч ${minutes % 60} мин"
}
