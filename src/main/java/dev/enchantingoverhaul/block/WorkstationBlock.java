package dev.enchantingoverhaul.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Common physical foundation; only the Replicator has an operation menu in this checkpoint. */
public final class WorkstationBlock extends BaseEntityBlock {
    private final Kind kind;

    public WorkstationBlock(Kind kind, BlockBehaviour.Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorkstationBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel) {
            if (kind == Kind.REPLICATOR) {
                if (level.getBlockEntity(pos) instanceof WorkstationBlockEntity workstation) {
                    player.openMenu(workstation);
                }
            } else {
                player.sendOverlayMessage(Component.translatable("message.enchantingoverhaul.not_implemented"));
            }
        }
        return InteractionResult.SUCCESS;
    }

    public enum Kind {
        REPLICATOR("enchantment_replicator", 3),
        EXTRACTOR("enchantment_extractor", 3),
        COMBINER("enchanted_book_combiner", 23);

        public final String id;
        public final int inventorySize;

        Kind(String id, int inventorySize) {
            this.id = id;
            this.inventorySize = inventorySize;
        }

        public int inventorySize() {
            return inventorySize;
        }

        public String translationKey() {
            return "block.enchantingoverhaul." + id;
        }
    }
}
