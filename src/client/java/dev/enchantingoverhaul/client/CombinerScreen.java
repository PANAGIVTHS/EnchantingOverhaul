package dev.enchantingoverhaul.client;

import dev.enchantingoverhaul.core.Catalyst;
import dev.enchantingoverhaul.core.WidgetPreviewPolicy;
import dev.enchantingoverhaul.core.SynthesisIngredient;
import dev.enchantingoverhaul.game.Enchantments;
import dev.enchantingoverhaul.game.SynthesisService;
import dev.enchantingoverhaul.menu.CombinerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Plain recipe/storage interface; visual design can be replaced independently of transactions. */
public final class CombinerScreen extends AbstractContainerScreen<CombinerMenu> {
    private Button combine;
    public CombinerScreen(CombinerMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title,CombinerMenu.WIDTH,CombinerMenu.HEIGHT); inventoryLabelY=138;
    }
    @Override protected void init() {
        super.init();
        combine=addRenderableWidget(Button.builder(Component.literal("Synthesize"),b->{
            if(minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0);
        }).bounds(leftPos+168,topPos+114,144,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
        combine.active=menu.status()==SynthesisService.Status.READY;
        super.extractRenderState(graphics,mouseX,mouseY,delta);
        if (WidgetPreviewPolicy.requested(combine.visible, combine.isHovered(), combine.isFocused(),
                minecraft.getLastInputType().isKeyboard()))
            WorkstationTooltip.show(graphics,font,recipeDetails(),mouseX,mouseY);
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
        super.extractBackground(graphics,mouseX,mouseY,delta);
        graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xffc6c6c6);
        graphics.fill(leftPos+158,topPos+19,leftPos+159,topPos+135,0xff777777);
        for(var slot:menu.slots) {
            graphics.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xff505050);
            graphics.fill(leftPos+slot.x,topPos+slot.y,leftPos+slot.x+16,topPos+slot.y+16,0xff8b8b8b);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics,int mouseX,int mouseY) {
        super.extractLabels(graphics,mouseX,mouseY);
        text(graphics,"Book A",8,16); text(graphics,"Book B",52,16); text(graphics,"Result",114,16);
        text(graphics,"Ingredients (20 slots)",8,52);
        var recipe=menu.recipe();
        if(recipe==null) {
            wrapped(graphics,"Matching books",168,22,144,0xff303030);
        } else {
            String name=Enchantments.find(minecraft.level,recipe.enchantmentId()).map(e->e.value().description().getString()).orElse(recipe.enchantmentId());
            wrapped(graphics,name+" "+recipe.inputLevel()+" -> "+recipe.resultLevel(),168,22,144,0xff303030);
            int y=47;
            for(var ingredient:recipe.ingredients()) {
                if (y >= 107) break;
                y=wrapped(graphics,ingredient.count()+" x "+ingredientName(ingredient),168,y,144,0xff303030,Math.max(1,(107-y)/10))+3;
            }
        }
        wrapped(graphics,statusText(),180,151,130,menu.status()==SynthesisService.Status.READY?0xff216821:0xff862525,5);
    }
    private String ingredientName(SynthesisIngredient ingredient) {
        if(ingredient.itemIds().size()>1) return "Wool (any color)";
        var name=Enchantments.catalystName(new Catalyst(ingredient.itemIds().getFirst(),ingredient.potionId()));
        return name.getString()+(ingredient.standardEquipment()?" (standard)":"");
    }
    private void text(GuiGraphicsExtractor graphics,String text,int x,int y) { graphics.text(font,text,x,y,0xff303030,false); }
    private int wrapped(GuiGraphicsExtractor graphics,String text,int x,int y,int width,int color) {
        return wrapped(graphics,text,x,y,width,color,6);
    }
    private int wrapped(GuiGraphicsExtractor graphics,String text,int x,int y,int width,int color,int maxLines) {
        var lines=font.split(Component.literal(text),width);
        for(int i=0;i<Math.min(lines.size(),maxLines);i++) {
            graphics.text(font,lines.get(i),x,y,color,false); y+=10;
        }
        return y;
    }
    private String recipeDetails() {
        var recipe=menu.recipe();
        if(recipe==null) return statusText();
        String name=Enchantments.find(minecraft.level,recipe.enchantmentId())
                .map(e->e.value().description().getString()).orElse(recipe.enchantmentId());
        StringBuilder details=new StringBuilder(name+" "+recipe.inputLevel()+" -> "+recipe.resultLevel());
        for(var ingredient:recipe.ingredients())
            details.append("\n").append(ingredient.count()).append(" x ").append(ingredientName(ingredient));
        if (menu.status() != SynthesisService.Status.READY) details.append("\n").append(statusText());
        return details.toString();
    }
    private String statusText() {
        return switch(menu.status()) {
            case READY -> "Ready";
            case CURSED_BOOKS -> "Cursed book";
            case MISMATCHED_BOOKS -> "Books do not match";
            case MAXIMUM_LEVEL -> "Maximum level";
            case NO_RECIPE -> "No recipe";
            case MISSING_INGREDIENTS -> "Missing ingredients";
            case OUTPUT_BLOCKED -> "Result slot occupied";
            case NO_BOTTLE_SPACE -> "No room for bottles";
            case INVALID_BOOKS -> "Requires two matching books";
        };
    }
}
