package com.odtheking.odin.features.impl.skyblock

import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.NumberSetting
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.isEtherwarpItem
import com.odtheking.odin.utils.skyblock.LocationUtils

object EasyEtherwarp : Module(
    name = "EasyEtherwarp",
    description = "Left-clicking Etherwarp-enabled item activates Etherwarp."
) {
    private enum class EtherwarpState { IDLE, SNEAKING, INTERACTING }

    private var state = EtherwarpState.IDLE
    private var attackKeyWasDown = false

    private var lastTeleportTime = 0L

    private var cooldown by NumberSetting("Cooldown", 500L, 20L, max = 1000L, desc = "Cooldown of using EasyEtherwarp (in milliseconds).")
    val alwaysShow by BooleanSetting("Always Show", false, desc = "Show Etherwarp overlay whenever an Etherwarp-enabled item is held.")

    init {
        on<TickEvent.End> {
            if (!LocationUtils.isInSkyblock) return@on
            val player = mc.player ?: return@on

            when (state) {
                EtherwarpState.SNEAKING -> {
                    mc.gameMode?.useItem(player, player.usedItemHand)
                    state = EtherwarpState.INTERACTING
                }
                EtherwarpState.INTERACTING -> {
                    mc.options.keyShift.isDown = false
                    state = EtherwarpState.IDLE
                }
                EtherwarpState.IDLE -> {}
            }

            if (state == EtherwarpState.IDLE) {
                val held = player.mainHandItem
                if (held.isEtherwarpItem() == null) return@on
                val attackKeyIsDown = mc.options.keyAttack.isDown
                val curTime = System.currentTimeMillis()

                // Do not allow holding left-click for multiple TPs, and only allow one TP every 500ms.
                // Without either of these checks, there is a high chance for AC flags.
                if (attackKeyIsDown && curTime - lastTeleportTime >= cooldown && !attackKeyWasDown) {
                    lastTeleportTime = curTime
                    mc.options.keyShift.isDown = true
                    state = EtherwarpState.SNEAKING
                }
                attackKeyWasDown = attackKeyIsDown
            }
        }
    }
}