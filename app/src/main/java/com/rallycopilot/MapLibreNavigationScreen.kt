package com.rallycopilot

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
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
import com.rallycopilot.game.RallyGameRepository
import com.rallycopilot.game.RallyGameState
import com.rallycopilot.core.GuidanceStep
import com.rallycopilot.core.RallyGuidanceEngine

private val RallyLime = Color(0xFFB7F34A)
private val RallyPanel = Color(0xF21B2329)
private const val DARK_STYLE = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
private const val OSRM_BASE = "https://router.project-osrm.org/route/v1/driving/"

private data class RouteResult(
    val coordinates: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val firstInstruction: String,
    val steps: List<GuidanceStep>
)

@Composable
fun MapLibreNavigationScreen(
    currentLocation: Location?,
    onEnableGps: () -> Unit,
    onSpeak: (String) -> Unit,
    gameRepository: RallyGameRepository,
    gameState: RallyGameState,
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
    var routeSteps by remember { mutableStateOf<List<GuidanceStep>>(emptyList()) }
    var routeCoordinates by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeProgressMeters by remember { mutableDoubleStateOf(0.0) }
    var offRouteFixes by remember { mutableIntStateOf(0) }
    var rerouting by remember { mutableStateOf(false) }
    var lastRerouteAt by remember { mutableLongStateOf(0L) }
    val guidanceEngine = remember { RallyGuidanceEngine() }
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
                    routeSteps = emptyList()
                    routeCoordinates = emptyList()
                    routeProgressMeters = 0.0
                    offRouteFixes = 0
                    guidanceEngine.reset()
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
                                routeSteps = result.steps
                                routeCoordinates = result.coordinates
                                routeProgressMeters = 0.0
                                offRouteFixes = 0
                                guidanceEngine.reset()
                                nextInstruction = guidanceEngine.preview(0.0, routeSteps)
                                val line = LineString.fromLngLats(result.coordinates.map { Point.fromLngLat(it.longitude, it.latitude) })
                                mapInstance.style?.getSourceAs<GeoJsonSource>("rally-route-source")?.setGeoJson(Feature.fromGeometry(line))
                                routeStatus = "Маршрут построен. Это предварительный расчёт, не автопилот."
                                onSpeak(routeIntroduction(result))
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

    LaunchedEffect(currentLocation, routeCoordinates, routeSteps, destination) {
        val location = currentLocation ?: return@LaunchedEffect
        if (routeCoordinates.size < 2 || routeSteps.isEmpty() || location.accuracy > 50f) return@LaunchedEffect

        val snap = nearestRouteProgress(location, routeCoordinates)
        offRouteFixes = if (snap.distanceFromRouteMeters > 65.0) offRouteFixes + 1 else 0

        val maxAdvance = maxOf(100.0, location.speed.toDouble() * 2.0 + 35.0)
        val progress = maxOf(routeProgressMeters, minOf(snap.progressMeters, routeProgressMeters + maxAdvance))
        routeProgressMeters = progress

        guidanceEngine.update(progress, routeSteps)?.let { onSpeak(it) }
        nextInstruction = guidanceEngine.preview(progress, routeSteps)

        val now = System.currentTimeMillis()
        val target = destination
        if (offRouteFixes >= 3 && target != null && !rerouting && now - lastRerouteAt >= 12000L) {
            rerouting = true
            offRouteFixes = 0
            lastRerouteAt = now
            scope.launch {
                try {
                    val updated = fetchOsrmRoute(location.latitude, location.longitude, target.latitude, target.longitude)
                    if (destination == target) {
                        routeDistance = formatDistance(updated.distanceMeters)
                        routeEta = formatDuration(updated.durationSeconds)
                        routeSteps = updated.steps
                        routeCoordinates = updated.coordinates
                        routeProgressMeters = 0.0
                        guidanceEngine.reset()
                        nextInstruction = guidanceEngine.preview(0.0, routeSteps)
                        val line = LineString.fromLngLats(updated.coordinates.map { Point.fromLngLat(it.longitude, it.latitude) })
                        map?.style?.getSourceAs<GeoJsonSource>("rally-route-source")?.setGeoJson(Feature.fromGeometry(line))
                        routeStatus = "Маршрут перестроен"
                        onSpeak("Ты отклонился от маршрута. Маршрут перестроен. " + nextInstruction)
                    }
                } catch (_: Exception) {
                    routeStatus = "Не удалось перестроить маршрут. Продолжай к ближайшей дороге."
                    onSpeak("Ты отклонился от маршрута. Не удалось перестроить путь. Будь внимателен.")
                } finally {
                    rerouting = false
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) onEnableGps()
    }

    Box(modifier.fillMaxSize().background(Color(0xFF101418))) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PlaceSearchPanel(currentLocation = currentLocation, onPlaceSelected = { selected ->
                destination = selected
                routeDistance = ""
                routeEta = ""
                nextInstruction = ""
                routeSteps = emptyList()
                routeCoordinates = emptyList()
                routeProgressMeters = 0.0
                offRouteFixes = 0
                guidanceEngine.reset()
                map?.style?.getSourceAs<GeoJsonSource>("rally-destination-source")?.setGeoJson(
                    Feature.fromGeometry(Point.fromLngLat(selected.longitude, selected.latitude))
                )
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(selected, 14.5), 650)
                val origin = currentLocation
                if (origin == null) {
                    routeStatus = "Адрес выбран. Включи GPS, чтобы построить маршрут"
                } else if (!routeBusy) {
                    routeBusy = true
                    routeStatus = "Считаю маршрут до выбранного места…"
                    scope.launch {
                        try {
                            val result = fetchOsrmRoute(origin.latitude, origin.longitude, selected.latitude, selected.longitude)
                            routeDistance = formatDistance(result.distanceMeters)
                            routeEta = formatDuration(result.durationSeconds)
                            routeSteps = result.steps
                            routeCoordinates = result.coordinates
                            routeProgressMeters = 0.0
                            offRouteFixes = 0
                            guidanceEngine.reset()
                            nextInstruction = guidanceEngine.preview(0.0, routeSteps)
                            val line = LineString.fromLngLats(result.coordinates.map { Point.fromLngLat(it.longitude, it.latitude) })
                            map?.style?.getSourceAs<GeoJsonSource>("rally-route-source")?.setGeoJson(Feature.fromGeometry(line))
                            routeStatus = "Маршрут построен. Это предварительный расчёт, не автопилот."
                            onSpeak(routeIntroduction(result))
                            val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                                .include(LatLng(origin.latitude, origin.longitude)).include(selected).build()
                            map?.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80), 700)
                        } catch (e: Exception) {
                            routeStatus = "Не удалось построить маршрут: ${e.message ?: "проверь интернет"}"
                        } finally {
                            routeBusy = false
                        }
                    }
                }
            })
        }
        RallyWeatherCard(currentLocation, Modifier.align(Alignment.TopEnd).padding(top = 148.dp, end = 10.dp).widthIn(max = 150.dp))
        if (routeBusy || routeDistance.isNotBlank() || nextInstruction.isNotBlank()) {
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.82f).padding(bottom = 116.dp), color = RallyPanel, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    if (routeBusy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = RallyLime)
                        Text("Строю маршрут…", color = Color.White, fontSize = 11.sp)
                    } else {
                        if (routeDistance.isNotBlank()) Text("$routeDistance · $routeEta", color = RallyLime, fontSize = 12.sp)
                        if (nextInstruction.isNotBlank()) Text(nextInstruction, color = Color.White, fontSize = 12.sp)
                        if (nextInstruction.isNotBlank()) {
                            TextButton(onClick = { onSpeak("Повторяю. " + nextInstruction) }, contentPadding = PaddingValues(0.dp)) {
                                Text("ПОВТОРИТЬ КОМАНДУ", color = RallyLime, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
        Surface(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 104.dp), color = RallyPanel.copy(alpha = 0.78f), shape = RoundedCornerShape(5.dp)) {
            Text("© OpenStreetMap · CARTO", color = Color.LightGray, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
        }
        Row(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (fine || coarse) onEnableGps() else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("МОЁ МЕСТО", fontSize = 10.sp) }
            OutlinedButton(onClick = {
                destination = null; routeDistance = ""; routeEta = ""; nextInstruction = ""
                routeSteps = emptyList(); routeCoordinates = emptyList(); routeProgressMeters = 0.0
                offRouteFixes = 0; guidanceEngine.reset()
                routeStatus = "Нажми и удерживай точку на карте, чтобы выбрать финиш"
                map?.style?.getSourceAs<GeoJsonSource>("rally-destination-source")?.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
                map?.style?.getSourceAs<GeoJsonSource>("rally-route-source")?.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
            }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("СБРОС", fontSize = 10.sp) }
        }
        Surface(Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp).size(66.dp).clickable { if (gameState.tripActive) gameRepository.finishTrip() else gameRepository.startTrip() }, color = RallyLime, shape = CircleShape, shadowElevation = 8.dp) {
            Box(contentAlignment = Alignment.Center) { Text(if (gameState.tripActive) "СТОП" else "СТАРТ", color = Color(0xFF101418), fontSize = 11.sp) }
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
        val stepsJson = route.getJSONArray("legs").getJSONObject(0).getJSONArray("steps")
        val guidanceSteps = mutableListOf<GuidanceStep>()
        var distanceFromStart = 0.0
        for (index in 0 until stepsJson.length()) {
            val step = stepsJson.getJSONObject(index)
            val maneuver = step.getJSONObject("maneuver")
            val type = maneuver.optString("type")
            if (type != "depart") {
                val street = step.optString("name").takeIf { it.isNotBlank() }
                val instruction = rallyInstruction(type, maneuver.optString("modifier"), street, maneuver.optInt("exit", 0))
                guidanceSteps += GuidanceStep(
                    distanceFromStartMeters = distanceFromStart,
                    instruction = instruction,
                    isArrival = type == "arrive"
                )
            }
            distanceFromStart += step.optDouble("distance", 0.0)
        }
        val firstInstruction = guidanceSteps.firstOrNull()?.instruction ?: "Следуй по маршруту"
        RouteResult(coordinates, route.getDouble("distance"), route.getDouble("duration"), firstInstruction, guidanceSteps)
    } finally { connection.disconnect() }
}

private fun rallyInstruction(type: String, modifier: String, street: String?, exitNumber: Int): String {
    if (type == "arrive") return "Финиш. Остановись в пункте назначения"
    if (type == "roundabout" || type == "rotary" || type == "roundabout turn") {
        val exitText = if (exitNumber > 0) "съезд номер " + exitNumber else "нужный съезд"
        return "На круговом движении выбери " + exitText + (street?.let { ", затем на " + it } ?: "")
    }
    val direction = when {
        modifier.contains("sharp left") -> "резко налево"
        modifier.contains("slight left") -> "плавно налево"
        modifier.contains("left") -> "налево"
        modifier.contains("sharp right") -> "резко направо"
        modifier.contains("slight right") -> "плавно направо"
        modifier.contains("right") -> "направо"
        modifier == "uturn" -> "разворот"
        modifier == "straight" || modifier.isBlank() -> "прямо"
        else -> "по маршруту"
    }
    val action = when (type) {
        "fork" -> "На развилке держись " + direction
        "end of road" -> "В конце дороги поверни " + direction
        "merge" -> "Влейся в поток, держись " + direction
        "on ramp" -> "На съезде держись " + direction
        "off ramp" -> "Съезжай " + direction
        else -> "Поверни " + direction
    }
    return if (street != null) action + " на " + street else action
}

private fun routeIntroduction(route: RouteResult): String {
    val first = route.steps.firstOrNull()
    val next = if (first == null) "Следуй по маршруту" else
        "Первая команда через " + formatDistance(first.distanceFromStartMeters) + ": " + first.instruction
    return "Маршрут построен. Расстояние " + formatDistance(route.distanceMeters) +
        ". В пути примерно " + formatDuration(route.durationSeconds) + ". " + next
}

private data class RouteSnap(val distanceFromRouteMeters: Double, val progressMeters: Double)

private fun nearestRouteProgress(location: Location, coordinates: List<LatLng>): RouteSnap {
    var cumulative = 0.0
    var bestDistance = Double.MAX_VALUE
    var bestProgress = 0.0
    for (index in coordinates.indices) {
        val point = coordinates[index]
        if (index > 0) cumulative += haversineMeters(coordinates[index - 1], point)
        val result = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, point.latitude, point.longitude, result)
        if (result[0].toDouble() < bestDistance) {
            bestDistance = result[0].toDouble()
            bestProgress = cumulative
        }
    }
    return RouteSnap(bestDistance, bestProgress)
}

private fun haversineMeters(a: LatLng, b: LatLng): Double {
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val sinLat = kotlin.math.sin(dLat / 2.0)
    val sinLon = kotlin.math.sin(dLon / 2.0)
    val h = sinLat * sinLat + kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * sinLon * sinLon
    return 6371000.0 * 2.0 * kotlin.math.asin(kotlin.math.sqrt(h))
}

private fun formatDistance(meters: Double): String = if (meters >= 1000) "%.1f км".format(meters / 1000.0) else "${meters.toInt()} м"
private fun formatDuration(seconds: Double): String {
    val minutes = (seconds / 60).toInt()
    return if (minutes < 60) "$minutes мин" else "${minutes / 60} ч ${minutes % 60} мин"
}
