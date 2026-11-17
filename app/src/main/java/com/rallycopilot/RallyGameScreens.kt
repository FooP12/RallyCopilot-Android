package com.rallycopilot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.rallycopilot.game.RallyGameRepository
import com.rallycopilot.game.RallyGameState

private val GameLime = Color(0xFFB7F34A)
private val GamePanel = Color(0xFF1B2329)
private val GameMuted = Color(0xFF9AA7AE)

private data class ShopItem(val id: String, val title: String, val description: String, val price: Int, val icon: String)

private val shopItems = listOf(
    ShopItem("theme_neon", "Неоновая трасса", "Акцентная тема интерфейса", 120, "✦"),
    ShopItem("badge_rookie", "Значок «Новичок»", "Первый знак в коллекцию профиля", 60, "★"),
    ShopItem("badge_rally", "Значок «Ралли»", "Для тех, кто не ищет лёгких дорог", 180, "🏁"),
    ShopItem("avatar_pilot", "Аватар «Пилот»", "Новый стиль профиля", 90, "◉"),
    ShopItem("map_night", "Ночная карта", "Тёмная карта с контрастными акцентами", 150, "▦"),
    ShopItem("title_explorer", "Титул «Исследователь»", "Отображается рядом с именем", 220, "⌖")
)

private data class Achievement(val id: String, val title: String, val description: String, val reward: Int)

private val achievements = listOf(
    Achievement("first_trip", "Первый выезд", "Заверши первую поездку длиной от 100 метров", 25),
    Achievement("first_km", "Первый километр", "Проедь первый километр с активной поездкой", 20),
    Achievement("ten_km", "Разогрев", "Накатай суммарно 10 километров", 40),
    Achievement("five_trips", "Постоянный пилот", "Заверши пять поездок", 60),
    Achievement("fifty_km", "Исследователь дорог", "Преодолей 50 километров", 80),
    Achievement("hundred_km", "Сотня за плечами", "Преодолей 100 километров", 150)
)

@Composable
fun RallyShopScreen(state: RallyGameState, repository: RallyGameRepository, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("МАГАЗИН ПИЛОТА", color = GameLime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("Снаряжение и стиль", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Surface(color = GamePanel, shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ТВОЙ БАЛАНС", color = GameMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("${state.coins} RC", color = GameLime, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                Text("◈", color = GameLime, fontSize = 32.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(shopItems) { item ->
                val owned = item.id in state.purchasedItems
                val equipped = item.id in state.equippedItems
                Surface(color = GamePanel, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.icon, color = GameLime, fontSize = 26.sp, modifier = Modifier.width(42.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(item.description, color = GameMuted, fontSize = 11.sp)
                            Text("${item.price} RC", color = GameLime, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { if (owned) repository.equip(item.id) else repository.purchase(item.id, item.price) },
                            enabled = !equipped && (owned || state.coins >= item.price),
                            colors = ButtonDefaults.buttonColors(containerColor = GameLime, contentColor = Color(0xFF101418), disabledContainerColor = Color(0xFF39434A))
                        ) { Text(if (equipped) "НАДЕТО" else if (owned) "НАДЕТЬ" else "КУПИТЬ", fontSize = 9.sp) }
                    }
                }
            }
        }
    }
}

@Composable
fun RallyAchievementsScreen(state: RallyGameState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("ПРОГРЕСС ПИЛОТА", color = GameLime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("Достижения", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Surface(color = GamePanel, shape = RoundedCornerShape(14.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Открыто: ${state.unlockedAchievements.size} / ${achievements.size}", color = Color.White, fontWeight = FontWeight.Bold)
                Text("${state.coins} RC", color = GameLime, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(achievements) { achievement ->
                val unlocked = achievement.id in state.unlockedAchievements
                Surface(color = GamePanel, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (unlocked) "🏆" else "◇", color = if (unlocked) GameLime else GameMuted, fontSize = 25.sp, modifier = Modifier.width(42.dp))
                        Column(Modifier.weight(1f)) {
                            Text(achievement.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(achievement.description, color = GameMuted, fontSize = 11.sp)
                            Text("Награда: ${achievement.reward} RC", color = GameLime, fontSize = 11.sp)
                        }
                        Text(if (unlocked) "ЕСТЬ" else "ЗАКРЫТО", color = if (unlocked) GameLime else GameMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RallyTripPanel(state: RallyGameState, repository: RallyGameRepository, modifier: Modifier = Modifier) {
    Surface(modifier, color = GamePanel.copy(alpha = 0.96f), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (state.tripActive) "ПОЕЗДКА АКТИВНА" else "ИГРОВОЙ ПРОГРЕСС", color = GameLime, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(if (state.tripActive) "${"%.1f".format(state.tripKilometers)} км · +5 RC за каждый полный километр" else "${state.coins} RC · ${"%.1f".format(state.totalKilometers)} км всего", color = Color.White, fontSize = 12.sp)
                }
                Button(
                    onClick = { if (state.tripActive) repository.finishTrip() else repository.startTrip() },
                    colors = ButtonDefaults.buttonColors(containerColor = GameLime, contentColor = Color(0xFF101418))
                ) { Text(if (state.tripActive) "ФИНИШ" else "СТАРТ", fontSize = 9.sp) }
            }
        }
    }
}

@Composable
fun RallyProfileStats(state: RallyGameState, modifier: Modifier = Modifier) {
    Surface(modifier, color = GamePanel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("СТАТИСТИКА ПИЛОТА", color = GameMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatValue("Баланс", "${state.coins} RC")
                StatValue("Пробег", "${"%.1f".format(state.totalKilometers)} км")
                StatValue("Поездки", state.completedTrips.toString())
            }
            Spacer(Modifier.height(10.dp))
            Text("Достижения: ${state.unlockedAchievements.size} · Предметы: ${state.purchasedItems.size} · Надето: ${state.equippedItems.size}", color = GameLime, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StatValue(label: String, value: String) {
    Column {
        Text(label, color = GameMuted, fontSize = 10.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun RallyGarageScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("rally_garage", android.content.Context.MODE_PRIVATE) }
    var vehicleName by remember { mutableStateOf(preferences.getString("vehicle_name", "") ?: "") }
    var vehicleYear by remember { mutableStateOf(preferences.getString("vehicle_year", "") ?: "") }
    var vehicleEngine by remember { mutableStateOf(preferences.getString("vehicle_engine", "") ?: "") }
    var saved by remember { mutableStateOf(vehicleName.isNotBlank()) }

    Column(modifier.fillMaxSize().padding(18.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("ТВОЙ АВТОМОБИЛЬ", color = GameLime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("Гараж", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Surface(color = GamePanel, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(if (saved) "АКТИВНЫЙ АВТОМОБИЛЬ" else "ДОБАВИТЬ АВТОМОБИЛЬ", color = GameMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = vehicleName, onValueChange = { vehicleName = it; saved = false },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Марка и модель") },
                    placeholder = { Text("Например, Nissan Pathfinder") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = vehicleYear, onValueChange = { vehicleYear = it.filter(Char::isDigit).take(4); saved = false },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Год выпуска") },
                    placeholder = { Text("Например, 2011") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = vehicleEngine, onValueChange = { vehicleEngine = it; saved = false },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Двигатель / модификация") },
                    placeholder = { Text("Например, 2.5 дизель") }
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        preferences.edit().putString("vehicle_name", vehicleName.trim())
                            .putString("vehicle_year", vehicleYear.trim())
                            .putString("vehicle_engine", vehicleEngine.trim()).apply()
                        saved = vehicleName.isNotBlank()
                    },
                    enabled = vehicleName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GameLime, contentColor = Color(0xFF101418))
                ) { Text("СОХРАНИТЬ АВТОМОБИЛЬ", fontWeight = FontWeight.Bold) }
                if (saved) {
                    Spacer(Modifier.height(8.dp))
                    Text("Автомобиль сохранён на этом устройстве.", color = GameLime, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(color = GamePanel, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("СЛЕДУЮЩИЕ ВОЗМОЖНОСТИ ГАРАЖА", color = GameLime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("История обслуживания, расходы на топливо, напоминания о ТО и статистика поездок будут привязаны к выбранному автомобилю.", color = GameMuted, fontSize = 11.sp)
            }
        }
    }
}
