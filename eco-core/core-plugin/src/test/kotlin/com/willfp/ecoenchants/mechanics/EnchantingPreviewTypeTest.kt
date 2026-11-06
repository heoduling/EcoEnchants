package com.willfp.ecoenchants.mechanics

import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class EnchantingPreviewTypeTest {
    private fun material(name: String): Material {
        val result = Material.getMaterial(name.uppercase())
        assertNotNull(result, "Missing material $name")
        return result!!
    }

    @Test
    fun `all seven tiers of each ordinary tool share their own preview class`() {
        for (family in listOf("sword", "pickaxe", "axe", "shovel", "hoe", "spear")) {
            for (tier in listOf("wooden", "stone", "copper", "iron", "golden", "diamond", "netherite")) {
                assertEquals("ecoenchants:preview/$family", enchantingPreviewType(material("${tier}_$family")))
            }
        }
    }

    @Test
    fun `armor tiers share a class only within the same equipment slot`() {
        for (family in listOf("helmet", "chestplate", "leggings", "boots")) {
            for (tier in listOf("leather", "chainmail", "copper", "iron", "golden", "diamond", "netherite")) {
                assertEquals("ecoenchants:preview/$family", enchantingPreviewType(material("${tier}_$family")))
            }
        }
        assertEquals("ecoenchants:preview/helmet", enchantingPreviewType(Material.TURTLE_HELMET))
    }

    @Test
    fun `single materials and unrelated items preserve their original keys`() {
        val cases = mapOf(
            Material.BOW to "minecraft:bow", Material.CROSSBOW to "minecraft:crossbow",
            Material.BOOK to "minecraft:book", Material.ENCHANTED_BOOK to "minecraft:enchanted_book",
            Material.TRIDENT to "minecraft:trident", Material.MACE to "minecraft:mace",
            Material.SHIELD to "minecraft:shield", Material.FISHING_ROD to "minecraft:fishing_rod",
            Material.DIAMOND_BLOCK to "minecraft:diamond_block", Material.PLAYER_HEAD to "minecraft:player_head",
            Material.AIR to "minecraft:air"
        )
        for ((type, expected) in cases) assertEquals(expected, enchantingPreviewType(type))
    }
}
