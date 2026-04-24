package com.odtheking.odin.features.impl.skyblock

import com.mojang.blaze3d.platform.InputConstants
import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.KeybindSetting.Companion.isDown
import com.odtheking.odin.clickgui.settings.impl.NumberSetting
import com.odtheking.odin.clickgui.settings.impl.SelectorSetting
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.events.core.onSend
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.isEtherwarpItem
import com.odtheking.odin.utils.randInt
import com.odtheking.odin.utils.skyblock.LocationUtils
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket

object QuickWarp : Module(
    name = "QuickWarp",
    description = "Introduces new, faster ways to Etherwarp when using an Etherwarp-enabled item."
) {
    private enum class EtherwarpState { IDLE, SNEAKING, INTERACTING }
    private var state = EtherwarpState.IDLE

    private var cooldown by NumberSetting("Cooldown", 500L, 20L, max = 1000L, desc = "Cooldown of using EasyEtherwarp (in milliseconds).")
    val instantWarp by BooleanSetting(name = "Instant Warp", default = false, desc = "Alternative mode of EasyEtherwarp, which always shows overlay when item is held, and instantly warps upon left-clicking.")
    var delaySpeed by SelectorSetting(name = "Delay Speed", default = "Medium", options = arrayListOf("Fast", "Medium", "Slow"), desc = "Delay speed of EasyEtherwarp")

    private data class DelayProfile(
        val sneakMin: Int, val sneakMax: Int,
        val releaseMin: Int, val releaseMax: Int,
        val jumpMin: Int, val jumpMax: Int
    )

    private val delayProfiles = mapOf(
        0 to DelayProfile(sneakMin = 1, sneakMax = 2, releaseMin = 1, releaseMax = 2, jumpMin = 1, jumpMax = 3),
        1 to DelayProfile(sneakMin = 3, sneakMax = 6, releaseMin = 3, releaseMax = 5, jumpMin = 1, jumpMax = 4),
        2 to DelayProfile(sneakMin = 4, sneakMax = 8, releaseMin = 3, releaseMax = 7, jumpMin = 2, jumpMax = 5)
    )

    private fun currentProfile() = delayProfiles[delaySpeed] ?: delayProfiles[1]!!

    fun shouldSuppressLeftClick(): Boolean =
        enabled && LocationUtils.isInSkyblock && mc.player?.mainHandItem?.isEtherwarpItem() != null

    private var reqSneakTicks = 0
    private var curSneakTicks = 0
    private var reqReleaseTicks = 0
    private var curReleaseTicks = 0
    private var reqJumpTicks = 0
    private var curJumpTicks = 0

    private var jumpLocked = false
    private var keysLocked = false

    private var attackKeyWasDown = false
    private var lastTeleportTime = 0L

    init {
        // Makes the player not move vertically while holding left-click for the TP.
        on<TickEvent.Start> {
            val player = mc.player ?: return@on
            if (keysLocked) {
                    mc.options.keyShift.isDown = true
                if (player.abilities.flying) {
                    curJumpTicks++
                    if (curJumpTicks >= reqJumpTicks) {
                        mc.options.keyJump.isDown = true
                        jumpLocked = true
                    }
                }
            }
        }

        onSend<ServerboundSwingPacket> {
            if (!enabled) return@onSend
            val held = mc.player?.mainHandItem ?: return@onSend
            if (held.isEtherwarpItem() == null) return@onSend
            it.cancel()
        }

        onSend<ServerboundPlayerActionPacket> {
            if (!shouldSuppressLeftClick()) return@onSend
            when (action) {
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK -> it.cancel()
                else -> {}
            }
        }

        on<TickEvent.End> {
            if (!LocationUtils.isInSkyblock) return@on
            val player = mc.player ?: return@on

            when (state) {
                EtherwarpState.SNEAKING -> {
                    curSneakTicks++
                    if (curSneakTicks >= reqSneakTicks && (instantWarp || !mc.options.keyAttack.isDown)) {
                        mc.gameMode?.useItem(player, player.usedItemHand)
                        state = EtherwarpState.INTERACTING
                    }
                }
                EtherwarpState.INTERACTING -> {
                    curReleaseTicks++
                    if (curReleaseTicks >= reqReleaseTicks) {
                        mc.options.keyShift.isDown = InputConstants.getKey(mc.options.keyShift.saveString()).isDown()
                        mc.options.keyJump.isDown = InputConstants.getKey(mc.options.keyJump.saveString()).isDown()
                        keysLocked = false
                        jumpLocked = false
                        state = EtherwarpState.IDLE
                    }
                }
                EtherwarpState.IDLE -> {}
            }

            if (state == EtherwarpState.IDLE) {
                val held = player.mainHandItem
                if (held.isEtherwarpItem() == null) {
                    return@on
                }

                val attackKeyIsDown = mc.options.keyAttack.isDown
                val curTime = System.currentTimeMillis()

                // Do not allow holding left-click for multiple TPs, and only allow one TP every 500ms.
                // Without either of these checks, there is a high chance for AC flags.
                if (attackKeyIsDown && curTime - lastTeleportTime >= cooldown && !attackKeyWasDown) {
                    keysLocked = true
                    lastTeleportTime = curTime
                    var profile = currentProfile()

                    // Get random values for delays based on the selected profile.
                    reqSneakTicks = randInt(profile.sneakMin, profile.sneakMax)
                    reqReleaseTicks = randInt(profile.releaseMin, profile.releaseMax)
                    reqJumpTicks = randInt(profile.jumpMin, profile.jumpMax)

                    // Reset counters to zero.
                    curSneakTicks = 0
                    curReleaseTicks = 0
                    curJumpTicks = 0

                    state = EtherwarpState.SNEAKING
                }
                attackKeyWasDown = attackKeyIsDown
            }
        }
    }
}