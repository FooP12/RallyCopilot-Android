package com.rallycopilot.game

import android.content.SharedPreferences
import android.location.Location
import androidx.compose.runtime.mutableStateOf
import kotlin.math.floor

data class RallyGameState(
    val coins: Int = 0,
    val totalKilometers: Double = 0.0,
    val tripKilometers: Double = 0.0,
    val completedTrips: Int = 0,
    val tripActive: Boolean = false,
    val unlockedAchievements: Set<String> = emptySet(),
    val purchasedItems: Set<String> = emptySet(),
    val equippedItems: Set<String> = emptySet()
)

class RallyGameRepository(private val preferences: SharedPreferences) {
    val state = mutableStateOf(load())
    private var lastLocation: Location? = null

    fun startTrip() {
        if (state.value.tripActive) return
        lastLocation = null
        update(state.value.copy(tripKilometers = 0.0, tripActive = true))
    }

    fun finishTrip() {
        val current = state.value
        if (!current.tripActive) return
        var next = current.copy(tripActive = false, completedTrips = current.completedTrips + if (current.tripKilometers >= 0.1) 1 else 0)
        next = unlock(next, "first_trip", next.completedTrips >= 1, 25)
        next = unlock(next, "five_trips", next.completedTrips >= 5, 60)
        update(next)
        lastLocation = null
    }

    fun onLocationChanged(location: Location) {
        if (!state.value.tripActive || location.accuracy > 50f) {
            lastLocation = location
            return
        }
        val previous = lastLocation
        lastLocation = location
        if (previous == null) return
        val meters = previous.distanceTo(location)
        if (meters < 5f || meters > 1000f) return

        val current = state.value
        val addedKm = meters / 1000.0
        val oldTripKm = current.tripKilometers
        val newTripKm = oldTripKm + addedKm
        val newTotalKm = current.totalKilometers + addedKm
        val earnedKilometerCoins = (floor(newTotalKm) - floor(current.totalKilometers)).toInt() * 5
        var next = current.copy(
            totalKilometers = newTotalKm,
            tripKilometers = newTripKm,
            coins = current.coins + earnedKilometerCoins
        )
        next = unlock(next, "first_km", newTotalKm >= 1.0, 20)
        next = unlock(next, "ten_km", newTotalKm >= 10.0, 40)
        next = unlock(next, "fifty_km", newTotalKm >= 50.0, 80)
        next = unlock(next, "hundred_km", newTotalKm >= 100.0, 150)
        update(next)
    }

    fun purchase(itemId: String, price: Int): Boolean {
        val current = state.value
        if (itemId in current.purchasedItems || current.coins < price) return false
        update(current.copy(coins = current.coins - price, purchasedItems = current.purchasedItems + itemId, equippedItems = current.equippedItems + itemId))
        return true
    }

    fun equip(itemId: String) {
        val current = state.value
        if (itemId !in current.purchasedItems) return
        update(current.copy(equippedItems = current.equippedItems + itemId))
    }

    private fun unlock(current: RallyGameState, id: String, condition: Boolean, reward: Int): RallyGameState {
        if (!condition || id in current.unlockedAchievements) return current
        return current.copy(
            coins = current.coins + reward,
            unlockedAchievements = current.unlockedAchievements + id
        )
    }

    private fun update(value: RallyGameState) {
        state.value = value
        preferences.edit()
            .putInt("coins", value.coins)
            .putLong("total_km_milli", (value.totalKilometers * 1000.0).toLong())
            .putInt("completed_trips", value.completedTrips)
            .putStringSet("unlocked_achievements", value.unlockedAchievements)
            .putStringSet("purchased_items", value.purchasedItems)
            .putStringSet("equipped_items", value.equippedItems)
            .apply()
    }

    private fun load(): RallyGameState = RallyGameState(
        coins = preferences.getInt("coins", 0),
        totalKilometers = preferences.getLong("total_km_milli", 0L) / 1000.0,
        completedTrips = preferences.getInt("completed_trips", 0),
        unlockedAchievements = preferences.getStringSet("unlocked_achievements", emptySet())?.toSet() ?: emptySet(),
        purchasedItems = preferences.getStringSet("purchased_items", emptySet())?.toSet() ?: emptySet(),
        equippedItems = preferences.getStringSet("equipped_items", emptySet())?.toSet() ?: emptySet()
    )
}
