package com.willfp.ecoenchants.mechanics

import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.TestableItem
import com.willfp.eco.core.recipe.parts.EmptyTestableItem
import com.willfp.ecoenchants.plugin
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.enchantments.EnchantmentOffer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.enchantment.PrepareItemEnchantEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

private data class PreparedEnchant(
    val candidate: AiyatsbusEnchantCandidate,
    val level: Int
)

private data class PreparedEnchantingTable(
    val bonus: Int,
    val offers: List<PreparedEnchant?>
)

object EnchantingTableSupport : Listener {
    private val prepared = ConcurrentHashMap<UUID, PreparedEnchantingTable>()

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (plugin.configYml.getBool("enchanting-table.enabled") && event.player.enchantmentSeed == 0) {
            event.player.enchantmentSeed = Random.nextInt(1, Int.MAX_VALUE)
        }
    }

    @EventHandler
    fun onLeave(event: PlayerQuitEvent) {
        prepared.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onPrepare(event: PrepareItemEnchantEvent) {
        if (!plugin.configYml.getBool("enchanting-table.enabled")) {
            prepared.remove(event.enchanter.uniqueId)
            return
        }

        val player = event.enchanter
        val item = event.item.clone().also {
            if (it.type == Material.BOOK) {
                it.type = Material.ENCHANTED_BOOK
            }
        }
        val bonus = event.enchantmentBonus.coerceAtMost(16)
        // The core reinitializes a saved zero seed on login. Initialize it once
        // here so a new player cannot reroll this round by reconnecting.
        if (player.enchantmentSeed == 0) {
            player.enchantmentSeed = Random.nextInt(1, Int.MAX_VALUE)
        }
        // Names, durability and other mutable metadata must not reroll offers.
        // Use a stable key (not enum identity hash), independent of the world.
        val random = Random(item.type.key.toString().hashCode() + player.enchantmentSeed)
        // eco's Set.copyOf registry order is randomized between JVMs. A stable
        // seed needs stable candidate order too, including rarity-group order.
        val pool = tableEnchantCandidates(player)
            .filter { it.canEnchant(item, emptyList()) }
            .sortedWith(compareBy({ it.enchantment.key.namespace }, { it.enchantment.key.key }))

        val offers = (0..2).map { index ->
            // A malformed/zero-weight rarity must not leave an empty button.
            val candidate = pool.drawAiyatsbus(random, countRarityPopulation = true)
                ?: pool.firstOrNull()
                ?: return@map null
            PreparedEnchant(
                candidate,
                calculateAiyatsbusLevel(
                    "enchanting-table",
                    candidate,
                    index + 1,
                    bonus,
                    player,
                    random
                )
            )
        }

        for (index in offers.indices) {
            val preparedOffer = offers[index] ?: continue
            val existingOffer = event.offers.getOrNull(index)
            val offer = existingOffer ?: EnchantmentOffer(
                preparedOffer.candidate.enchantment,
                preparedOffer.level,
                index + 1
            ).also { event.offers[index] = it }
            offer.enchantment = preparedOffer.candidate.enchantment
            offer.enchantmentLevel = preparedOffer.level
        }

        prepared[player.uniqueId] = PreparedEnchantingTable(bonus, offers)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEnchant(event: EnchantItemEvent) {
        if (!plugin.configYml.getBool("enchanting-table.enabled")) {
            return
        }

        val player = event.enchanter
        // A later listener can cancel this attempt. Keep the hint for a retry;
        // the next prepare replaces it, and quitting removes it.
        val state = prepared[player.uniqueId] ?: return
        val hint = state.offers.getOrNull(event.whichButton()) ?: return
        val item = event.item.clone()
        val isExtraEnchantable = ExtraItemSupport.extraEnchantableItems.any { it.matches(item) }

        if (isExtraEnchantable && player.gameMode != GameMode.CREATIVE) {
            val lapis = event.inventory.getItem(1)
            val lapisCost = event.whichButton() + 1
            if (lapis == null || lapis.amount < lapisCost) {
                event.isCancelled = true
                return
            }
            lapis.amount -= lapisCost
            event.inventory.setItem(1, lapis)
        }

        if (item.type == Material.BOOK) {
            item.type = Material.ENCHANTED_BOOK
        }

        val selected = linkedMapOf(
            hint.candidate.enchantment to hint.level
        )
        val button = event.whichButton() + 1
        val extraAmount = calculateAiyatsbusAmount("enchanting-table", button, player)
        val pool = tableEnchantCandidates(player).filter {
            it.canEnchant(item, selected.keys)
        }

        repeat(extraAmount) {
            val candidate = pool
                .filter { it.enchantment !in selected && it.canEnchant(item, selected.keys) }
                .drawAiyatsbus()
                ?: return@repeat
            selected[candidate.enchantment] = calculateAiyatsbusLevel(
                "enchanting-table",
                candidate,
                button,
                state.bonus,
                player,
                subtractRandom = false
            )
        }

        event.enchantsToAdd.clear()
        event.enchantsToAdd.putAll(selected)

        if (item.type == Material.ENCHANTED_BOOK) {
            plugin.scheduler.run {
                if (!event.isCancelled) {
                    val postEnchantItem = event.inventory.getItem(0)
                    val meta = postEnchantItem?.itemMeta as? EnchantmentStorageMeta
                    for ((enchant, level) in event.enchantsToAdd) {
                        meta?.addStoredEnchant(enchant, level, true)
                    }
                    postEnchantItem?.itemMeta = meta
                }
            }
        }
    }
}

object ExtraItemSupport {
    internal val extraEnchantableItems = CopyOnWriteArrayList<TestableItem>()

    internal fun reload() {
        extraEnchantableItems.clear()
        extraEnchantableItems.addAll(plugin.targetsYml.getStrings("extra-enchantable-items").map {
            Items.lookup(it)
        }.filterNot { it is EmptyTestableItem })
    }
}
