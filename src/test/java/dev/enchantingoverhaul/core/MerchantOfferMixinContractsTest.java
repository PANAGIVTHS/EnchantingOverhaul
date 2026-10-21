package dev.enchantingoverhaul.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exact cached class-file contracts only; does not initialize or transform Minecraft. */
class MerchantOfferMixinContractsTest {
    private static final String ROOT = "dev/enchantingoverhaul/";
    private static final String MERCHANT = "net/minecraft/world/item/trading/Merchant";
    private static final String OFFERS = "Lnet/minecraft/world/item/trading/MerchantOffers;";
    private static final String MENU = "net/minecraft/world/inventory/MerchantMenu";
    private static final List<String> MIXINS = List.of("MerchantMenuOffersMixin", "MerchantContainerOffersMixin",
            "MerchantResultSlotOffersMixin", "ServerPlayerMerchantOffersMixin");

    private ClassNode bytes(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/" + name + ".class")) {
            assertNotNull(stream, name);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        var result = new ArrayList<AnnotationNode>();
        if (visible != null) result.addAll(visible);
        if (invisible != null) result.addAll(invisible);
        return result;
    }
    private static Map<String, Object> values(AnnotationNode node) {
        var result = new HashMap<String, Object>();
        if (node.values != null) for (int i = 0; i < node.values.size(); i += 2)
            result.put((String)node.values.get(i), node.values.get(i + 1));
        return result;
    }
    private static boolean isStatic(int flags) { return (flags & Opcodes.ACC_STATIC) != 0; }
    private static List<Type> args(String desc) { return new ArrayList<>(Arrays.asList(Type.getArgumentTypes(desc))); }
    private static MethodNode method(ClassNode node, String name) {
        var matches = node.methods.stream().filter(m -> m.name.equals(name)).toList();
        assertEquals(1, matches.size(), node.name + "." + name);
        return matches.getFirst();
    }
    private static List<MethodInsnNode> calls(MethodNode method, String owner, String name) {
        var result = new ArrayList<MethodInsnNode>();
        for (var insn : method.instructions) if (insn instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name)) result.add(call);
        return result;
    }
    private static AnnotationNode injectionAt(Map<String, Object> data) {
        Object at = data.get("at");
        return at instanceof List<?> list ? (AnnotationNode)list.getFirst() : (AnnotationNode)at;
    }

    @Test void newMixinsHaveExactTargetsShadowsAndHandlerDescriptors() throws Exception {
        int injectionCount = 0, shadowCount = 0;
        for (String simple : MIXINS) {
            var mixin = bytes(ROOT + "mixin/" + simple);
            var annotation = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations).stream()
                    .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
            var target = bytes(((Type)((List<?>)values(annotation).get("value")).getFirst()).getInternalName());
            for (FieldNode field : mixin.fields) {
                if (annotations(field.visibleAnnotations, field.invisibleAnnotations).stream()
                        .noneMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;"))) continue;
                var actual = target.fields.stream().filter(f -> f.name.equals(field.name) && f.desc.equals(field.desc))
                        .findFirst().orElseThrow();
                assertEquals(isStatic(actual.access), isStatic(field.access));
                assertEquals((actual.access & Opcodes.ACC_FINAL) != 0,
                        annotations(field.visibleAnnotations, field.invisibleAnnotations).stream()
                                .anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Final;")));
                shadowCount++;
            }
            for (MethodNode callback : mixin.methods) {
                for (AnnotationNode injection : annotations(callback.visibleAnnotations, callback.invisibleAnnotations)) {
                    if (injection.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")) {
                        var actual = method(target, callback.name);
                        assertEquals(actual.desc, callback.desc);
                        assertEquals(isStatic(actual.access), isStatic(callback.access));
                        continue;
                    }
                    String kind = injection.desc.substring(injection.desc.lastIndexOf('/') + 1).replace(";", "");
                    if (!List.of("Inject", "ModifyVariable", "WrapOperation").contains(kind)) continue;
                    var data = values(injection);
                    for (Object selector : (List<?>)data.get("method")) {
                        var actual = method(target, (String)selector);
                        String label = simple + "." + callback.name;
                        assertEquals(isStatic(actual.access), isStatic(callback.access), label);
                        var at = values(injectionAt(data));
                        List<Type> expected;
                        Type result;
                        if (kind.equals("Inject")) {
                            assertTrue(List.of("HEAD", "RETURN").contains(at.get("value")), label);
                            assertEquals(true, data.get("cancellable"), label);
                            expected = args(actual.desc);
                            expected.add(Type.getType("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;"));
                            result = Type.VOID_TYPE;
                        } else if (kind.equals("ModifyVariable")) {
                            assertEquals("HEAD", at.get("value"), label);
                            assertEquals(true, data.get("argsOnly"), label);
                            expected = List.of(Type.getType(OFFERS));
                            assertEquals(1, args(actual.desc).stream().filter(Type.getType(OFFERS)::equals).count(), label);
                            result = Type.getType(OFFERS);
                        } else {
                            String invoke = (String)at.get("target");
                            var matches = new ArrayList<MethodInsnNode>();
                            for (var insn : actual.instructions) if (insn instanceof MethodInsnNode call
                                    && invoke.equals("L" + call.owner + ";" + call.name + call.desc)) matches.add(call);
                            assertEquals(1, matches.size(), label);
                            var call = matches.getFirst();
                            expected = args(call.desc);
                            assertNotEquals(Opcodes.INVOKESTATIC, call.getOpcode());
                            expected.addFirst(Type.getObjectType(call.owner));
                            expected.add(Type.getType("Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;"));
                            result = Type.getReturnType(call.desc);
                        }
                        assertEquals(Type.getMethodDescriptor(result, expected.toArray(Type[]::new)), callback.desc, label);
                        injectionCount++;
                    }
                }
            }
        }
        assertEquals(5, injectionCount);
        assertEquals(5, shadowCount);
    }

    @Test void initialAndPriceRefreshPacketsUseServerPlayerSendMethod() throws Exception {
        var initial = method(bytes(MERCHANT), "openTradingScreen");
        var refresh = method(bytes("net/minecraft/world/entity/npc/villager/Villager"), "updateSpecialPrices");
        for (var caller : List.of(initial, refresh)) {
            var call = calls(caller, "net/minecraft/world/entity/player/Player", "sendMerchantOffers");
            assertEquals(1, call.size());
            assertEquals("(I" + OFFERS + "IIZZ)V", call.getFirst().desc);
            assertEquals(Opcodes.INVOKEVIRTUAL, call.getFirst().getOpcode());
        }
        assertEquals("(I" + OFFERS + "IIZZ)V",
                method(bytes("net/minecraft/server/level/ServerPlayer"), "sendMerchantOffers").desc);
    }

    @Test void forgedSelectionAndAutofillReachTheSameFilteredMenuAndContainerViews() throws Exception {
        var packet = method(bytes("net/minecraft/server/network/ServerGamePacketListenerImpl"), "handleSelectTrade");
        assertEquals(1, calls(packet, MENU, "setSelectionHint").size());
        assertEquals(1, calls(packet, MENU, "tryMoveItems").size());
        var menu = bytes(MENU);
        assertEquals(2, calls(method(menu, "tryMoveItems"), MENU, "getOffers").size());
        assertEquals(1, calls(method(menu, "getOffers"), MERCHANT, "getOffers").size());
        var container = bytes("net/minecraft/world/inventory/MerchantContainer");
        assertEquals(1, calls(method(container, "setSelectionHint"), container.name, "updateSellItem").size());
        assertEquals(1, calls(method(container, "updateSellItem"), MERCHANT, "getOffers").size());
        assertEquals(2, calls(method(container, "updateSellItem"),
                "net/minecraft/world/item/trading/MerchantOffers", "getRecipeFor").size());
    }

    @Test void pickupAndShiftClickGuardsPrecedeOutputRemovalInsteadOfDeletingPaidItems() throws Exception {
        String restriction = ROOT + "game/MerchantOfferRestrictions";
        var menuGuard = method(bytes(ROOT + "mixin/MerchantMenuOffersMixin"), "enchantingoverhaul$guardQuickPurchase");
        var slotGuard = method(bytes(ROOT + "mixin/MerchantResultSlotOffersMixin"), "enchantingoverhaul$guardPurchase");
        for (var guard : List.of(menuGuard, slotGuard)) {
            assertEquals(1, calls(guard, restriction, "permitsPickup").size());
            assertTrue(calls(guard, "net/minecraft/world/item/trading/MerchantOffer", "take").isEmpty());
        }
        var quickMove = method(bytes(MENU), "quickMoveStack");
        var calls = new ArrayList<String>();
        for (var insn : quickMove.instructions) if (insn instanceof MethodInsnNode call) calls.add(call.name);
        assertTrue(calls.indexOf("moveItemStackTo") < calls.indexOf("onTake"));
        var resultSlot = bytes("net/minecraft/world/inventory/MerchantResultSlot");
        assertEquals(2, calls(method(resultSlot, "mayPickup"),
                "net/minecraft/world/item/trading/MerchantOffer", "satisfiedBy").size());
        var pickup = method(bytes(restriction), "permitsPickup");
        assertEquals(2, calls(pickup, restriction, "permits").size());
    }

    @Test void emptyFilteredViewsClearOnlyTheUnpaidOutputPreviewAndFutureXp() throws Exception {
        var wrapper = method(bytes(ROOT + "mixin/MerchantContainerOffersMixin"), "enchantingoverhaul$matchPresentedOrder");
        int previewWrites = 0, xpWrites = 0;
        for (var insn : wrapper.instructions) {
            if (insn instanceof MethodInsnNode call && call.name.equals("setItem")) {
                assertEquals("(ILnet/minecraft/world/item/ItemStack;)V", call.desc);
                assertInstanceOf(FieldInsnNode.class, insn.getPrevious());
                var empty = (FieldInsnNode)insn.getPrevious();
                assertEquals("net/minecraft/world/item/ItemStack", empty.owner);
                assertEquals("EMPTY", empty.name);
                assertEquals(Opcodes.ICONST_2, empty.getPrevious().getOpcode());
                previewWrites++;
            }
            if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD) {
                assertEquals("futureXp", field.name);
                assertEquals(Opcodes.ICONST_0, field.getPrevious().getOpcode());
                xpWrites++;
            }
        }
        assertEquals(1, previewWrites);
        assertEquals(1, xpWrites);
        var actual = method(bytes("net/minecraft/world/inventory/MerchantContainer"), "updateSellItem");
        int reset = -1, getOffers = -1, index = 0;
        for (var insn : actual.instructions) {
            if (insn instanceof FieldInsnNode field && field.name.equals("activeOffer")
                    && field.getOpcode() == Opcodes.PUTFIELD && field.getPrevious().getOpcode() == Opcodes.ACONST_NULL)
                reset = index;
            if (insn instanceof MethodInsnNode call && call.owner.equals(MERCHANT) && call.name.equals("getOffers"))
                getOffers = index;
            index++;
        }
        assertTrue(reset >= 0 && reset < getOffers, "vanilla must clear activeOffer before the filter executes");
    }

    @Test void restrictionsReadBothComponentsWithoutMutatingOffersItemsOrPersistentEntityLists() throws Exception {
        var adapter = bytes(ROOT + "game/MerchantOfferRestrictions");
        var components = new ArrayList<String>();
        for (var insn : method(adapter, "permits").instructions) if (insn instanceof FieldInsnNode field
                && field.owner.equals("net/minecraft/core/component/DataComponents")) components.add(field.name);
        assertEquals(List.of("ENCHANTMENTS", "STORED_ENCHANTMENTS"), components);
        for (var node : List.of(adapter, bytes(ROOT + "mixin/MerchantMenuOffersMixin"),
                bytes(ROOT + "mixin/MerchantContainerOffersMixin"), bytes(ROOT + "mixin/MerchantResultSlotOffersMixin"),
                bytes(ROOT + "mixin/ServerPlayerMerchantOffersMixin"))) {
            for (var m : node.methods) for (var insn : m.instructions) if (insn instanceof MethodInsnNode call) {
                assertFalse(List.of("overrideOffers", "setOffers", "setToOutOfStock", "setSpecialPriceDiff",
                        "increaseUses", "resetUses", "removeIf", "removeAll", "clear", "shrink", "transmuteCopy").contains(call.name),
                        node.name + "." + m.name + " mutates " + call.name);
                if (call.owner.equals("net/minecraft/world/item/ItemStack"))
                    assertFalse(List.of("set", "remove", "copy", "setCount").contains(call.name));
            }
        }
    }
}
