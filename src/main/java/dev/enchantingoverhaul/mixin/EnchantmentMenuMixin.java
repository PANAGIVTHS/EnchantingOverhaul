package dev.enchantingoverhaul.mixin;

import dev.enchantingoverhaul.core.VanillaEnchantingPolicy;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve the placed table/container, but never generate or execute random offers. */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin extends AbstractContainerMenu {
    @Shadow @Final public int[] costs;
    @Shadow @Final public int[] enchantClue;
    @Shadow @Final public int[] levelClue;

    protected EnchantmentMenuMixin(MenuType<?> type, int containerId) {
        super(type, containerId);
    }

    @Unique
    private void enchantingoverhaul$clearOffers() {
        VanillaEnchantingPolicy.clearTableOffers(this.costs, this.enchantClue, this.levelClue);
        // Use vanilla DataSlots so every slot refresh clears the client's old offers too.
        this.broadcastChanges();
    }

    @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true, require = 1)
    private void enchantingoverhaul$disableRandomOffers(Container container, CallbackInfo ci) {
        this.enchantingoverhaul$clearOffers();
        ci.cancel();
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true, require = 1)
    private void enchantingoverhaul$rejectRandomRoll(Player player, int buttonId,
                                                     CallbackInfoReturnable<Boolean> cir) {
        this.enchantingoverhaul$clearOffers();
        cir.setReturnValue(VanillaEnchantingPolicy.tableButtonAllowed(buttonId));
    }
}
