package com.willfp.ecoenchants.mechanics

import com.willfp.ecoenchants.EcoEnchantsPlugin
import io.mockk.every
import io.mockk.mockk
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EnchantingTableSupportTest {
    private var seed = 0
    private lateinit var plugin: EcoEnchantsPlugin
    private lateinit var player: Player

    @BeforeEach
    fun setup() {
        seed = 0
        plugin = mockk(relaxed = true)
        every { plugin.configYml.getBool("enchanting-table.enabled") } returns true
        Class.forName("com.willfp.ecoenchants.EcoEnchantsPluginKt")
            .getDeclaredField("plugin").apply { isAccessible = true }.set(null, plugin)
        player = mockk()
        every { player.enchantmentSeed } answers { seed }
        every { player.enchantmentSeed = any() } answers { seed = firstArg() }
    }

    @Test
    fun `a new player receives one nonzero round before opening a menu`() {
        val event = PlayerJoinEvent(player, Component.empty())
        EnchantingTableSupport.onJoin(event)
        val first = seed
        assertNotEquals(0, first)
        EnchantingTableSupport.onJoin(event)
        assertEquals(first, seed)
    }

    @Test
    fun `joining preserves an existing negative round`() {
        seed = -123456789
        EnchantingTableSupport.onJoin(PlayerJoinEvent(player, Component.empty()))
        assertEquals(-123456789, seed)
    }

    @Test
    fun `disabled table support does not initialize player state`() {
        every { plugin.configYml.getBool("enchanting-table.enabled") } returns false
        EnchantingTableSupport.onJoin(PlayerJoinEvent(player, Component.empty()))
        assertEquals(0, seed)
    }
}
