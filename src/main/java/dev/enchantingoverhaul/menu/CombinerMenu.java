package dev.enchantingoverhaul.menu;

import dev.enchantingoverhaul.ModContent;
import dev.enchantingoverhaul.game.SynthesisService;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Two book inputs, take-only output and exactly twenty manually supplied ingredient slots. */
public final class CombinerMenu extends AbstractContainerMenu {
    public static final int WIDTH=320, HEIGHT=232;
    public static final int STORAGE_START=3, STORAGE_END=23;
    private final Container inventory;
    private final Player owner;
    // status ordinal and catalog recipe index+1 (zero means no matching recipe).
    private final SimpleContainerData data = new SimpleContainerData(2);
    public CombinerMenu(int id, Inventory playerInventory, Container inventory) {
        super(ModContent.COMBINER_MENU,id);
        checkContainerSize(inventory,23);
        this.inventory=inventory; this.owner=playerInventory.player;
        data.set(0,SynthesisService.Status.INVALID_BOOKS.ordinal());
        for (int i=0;i<2;i++) addSlot(new Slot(inventory,i,18+i*44,26) {
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(Items.ENCHANTED_BOOK); }
        });
        addSlot(new Slot(inventory,2,124,26) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int i=0;i<20;i++) addSlot(new Slot(inventory,3+i,8+(i%5)*28,64+(i/5)*18));
        addStandardInventorySlots(playerInventory,8,150);
        addDataSlots(data);
    }
    public SynthesisService.Status status() { return SynthesisService.Status.values()[Math.clamp(data.get(0),0,SynthesisService.Status.values().length-1)]; }
    public int recipeIndex() { return data.get(1)-1; }
    public dev.enchantingoverhaul.core.SynthesisRecipe recipe() {
        int i=recipeIndex();
        var recipes=SynthesisService.CATALOG.recipes();
        return i>=0 && i<recipes.size()? recipes.get(i):null;
    }
    private void refresh() {
        var plan=SynthesisService.plan(owner,inventory);
        data.set(0,plan.status().ordinal());
        data.set(1,plan.recipe()==null? 0 : SynthesisService.CATALOG.recipes().indexOf(plan.recipe())+1);
    }
    @Override public boolean stillValid(Player player) { return owner==player && inventory.stillValid(player); }
    @Override public void broadcastChanges() {
        if(!owner.level().isClientSide() && stillValid(owner)) refresh();
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player,int button) {
        if(button!=0 || player.level().isClientSide() || player.isSpectator() || !stillValid(player)) return false;
        SynthesisService.apply(player,inventory);
        refresh(); super.broadcastChanges(); return true;
    }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(!stillValid(player)||index<0||index>=slots.size()||!slots.get(index).hasItem()) return ItemStack.EMPTY;
        Slot slot=slots.get(index); ItemStack stack=slot.getItem(), copy=stack.copy();
        boolean moved;
        if(index<23) moved=moveItemStackTo(stack,23,slots.size(),true);
        else if(stack.is(Items.ENCHANTED_BOOK)) moved=moveItemStackTo(stack,0,2,false);
        else moved=moveItemStackTo(stack,3,23,false);
        if(!moved) return ItemStack.EMPTY;
        if(stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player,stack); return copy;
    }
}
