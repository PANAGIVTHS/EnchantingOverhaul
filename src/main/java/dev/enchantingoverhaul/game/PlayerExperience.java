package dev.enchantingoverhaul.game;

import dev.enchantingoverhaul.core.ExperienceMath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.world.entity.player.Player;

/** Uses the visible level/bar, not totalExperience (which /experience and anvils can leave stale). */
public final class PlayerExperience {
    private PlayerExperience() {}
    public static long available(Player player) {
        return ExperienceMath.points(player.experienceLevel, player.experienceProgress);
    }
    public static void debit(Player player, int amount) {
        long before = available(player);
        if (amount < 0 || before < amount) throw new IllegalArgumentException("Insufficient XP");
        var remaining = ExperienceMath.state(before - amount);
        player.experienceLevel = remaining.level();
        player.experienceProgress = remaining.progress();
        player.totalExperience = (int)Math.min(Integer.MAX_VALUE, before - amount);
        // Force synchronization even when /experience left totalExperience equal to the
        // server's last-sent value. Waiting for vanilla's totalExperience-only check can miss it.
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetExperiencePacket(player.experienceProgress,
                    player.totalExperience, player.experienceLevel));
        }
    }
}
