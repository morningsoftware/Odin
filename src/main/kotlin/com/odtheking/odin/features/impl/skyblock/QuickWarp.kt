package com.odtheking.odin.features.impl.skyblock

import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.NumberSetting
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.isEtherwarpItem
import com.odtheking.odin.utils.skyblock.LocationUtils

object QuickWarp : Module(
    name = "QuickWarp",
    description = "Introduces new, faster ways to Etherwarp when using an Etherwarp-enabled item."
) {
    private enum class EtherwarpState { IDLE, SNEAKING, INTERACTING }

    private var state = EtherwarpState.IDLE
    private var attackKeyWasDown = false

    private var lastTeleportTime = 0L

    private var cooldown by NumberSetting("Cooldown", 500L, 20L, max = 1000L, desc = "Cooldown of using EasyEtherwarp (in milliseconds).")
    val instantWarp by BooleanSetting(name = "Instant Warp", default = false, desc = "Alternative mode of EasyEtherwarp, which always shows overlay when item is held, and instantly warps upon left-clicking.")

    init {
        on<TickEvent.End> {
            if (!LocationUtils.isInSkyblock) return@on
            val player = mc.player ?: return@on

            when (state) {
                EtherwarpState.SNEAKING -> {
                    if (instantWarp || !mc.options.keyAttack.isDown) {
                        mc.gameMode?.useItem(player, player.usedItemHand)
                        state = EtherwarpState.INTERACTING
                    }
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