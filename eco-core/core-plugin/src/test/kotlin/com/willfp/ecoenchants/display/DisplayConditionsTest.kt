package com.willfp.ecoenchants.display

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.Holder
import com.willfp.libreforge.conditions.Condition
import com.willfp.libreforge.conditions.ConditionBlock
import com.willfp.libreforge.conditions.ConditionList
import com.willfp.libreforge.effects.EffectBlock
import com.willfp.libreforge.effects.EffectList
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DisplayConditionsTest {
    private fun condition(force: Boolean = false, lines: List<String> = emptyList()): ConditionBlock<*> =
        ConditionBlock::class.java.constructors.single().newInstance(
            mockk<Condition<Any>>(), mockk<Config>(), Unit, null, lines, force, false
        ) as ConditionBlock<*>

    private fun holder(top: List<ConditionBlock<*>>, nested: List<ConditionBlock<*>> = emptyList()): Holder {
        val effect = mockk<EffectBlock>()
        every { effect.conditions } returns ConditionList(nested)
        return mockk<Holder>().also {
            every { it.conditions } returns ConditionList(top)
            every { it.effects } returns EffectList(listOf(effect))
        }
    }

    @Test fun `conditions without display requests do not need a display holder`() {
        assertFalse(holder(listOf(condition()), listOf(condition())).hasDisplayConditions())
    }
    @Test fun `force show without lines still needs the name marker`() {
        assertTrue(holder(listOf(condition(force = true))).hasDisplayConditions())
    }
    @Test fun `lines imply display even without the force flag`() {
        assertTrue(holder(listOf(condition(lines = listOf("Missing requirement")))).hasDisplayConditions())
    }
    @Test fun `effect conditions are included in the display decision`() {
        assertTrue(holder(emptyList(), listOf(condition(force = true))).hasDisplayConditions())
        assertTrue(holder(emptyList(), listOf(condition(lines = listOf("Effect requirement")))).hasDisplayConditions())
    }
    @Test fun `the current compiled rules are checked without a stale result cache`() {
        val rules = mutableListOf<ConditionBlock<*>>()
        val current = holder(rules)
        assertFalse(current.hasDisplayConditions())
        rules.add(condition(force = true))
        assertTrue(current.hasDisplayConditions())
    }
}
