package dev.enchantingoverhaul.core;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reads class bytes only; never loads, initializes, transforms or bootstraps Minecraft classes. */
class MixinTargetContractsTest {
    private static final String CALLBACK = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    private static final String CALLBACK_RETURN = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";
    private static final String OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";

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
    private static String descriptor(List<Type> args, Type result) {
        return Type.getMethodDescriptor(result, args.toArray(Type[]::new));
    }
    private static List<Type> args(String descriptor) {
        return new ArrayList<>(Arrays.asList(Type.getArgumentTypes(descriptor)));
    }
    private static void handler(MethodNode handler, List<Type> expected, Type result, String label) {
        assertEquals(descriptor(expected, result), handler.desc, label);
    }
    private static boolean isStatic(int flags) { return (flags & Opcodes.ACC_STATIC) != 0; }

    @Test void requiredMixinsHaveExactBytecodeTargetsAndCompatibleHandlers() throws Exception {
        try (var stream = getClass().getResourceAsStream("/enchantingoverhaul.mixins.json")) {
            assertNotNull(stream);
            var config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(config.get("required").getAsBoolean());
            assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
            int classes = 0, injections = 0, shadows = 0;
            for (var entry : config.getAsJsonArray("mixins")) {
                String name = config.get("package").getAsString().replace('.', '/') + "/" + entry.getAsString();
                ClassNode mixin = bytes(name);
                var annotation = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations).stream()
                        .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
                var targetValues = values(annotation);
                String targetName = targetValues.containsKey("value")
                        ? ((Type)((List<?>)targetValues.get("value")).getFirst()).getInternalName()
                        : ((String)((List<?>)targetValues.get("targets")).getFirst()).replace('.', '/');
                ClassNode target = bytes(targetName);
                for (FieldNode field : mixin.fields) {
                    if (annotations(field.visibleAnnotations, field.invisibleAnnotations).stream()
                            .noneMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;"))) continue;
                    var actual = target.fields.stream().filter(f -> f.name.equals(field.name) && f.desc.equals(field.desc)).findFirst();
                    assertTrue(actual.isPresent(), name + " shadow " + field.name);
                    assertEquals(isStatic(field.access), isStatic(actual.orElseThrow().access));
                    shadows++;
                }
                for (MethodNode callback : mixin.methods) {
                    for (AnnotationNode injection : annotations(callback.visibleAnnotations, callback.invisibleAnnotations)) {
                        String kind = injection.desc.substring(injection.desc.lastIndexOf('/') + 1).replace(";", "");
                        if (!List.of("Inject", "Redirect", "ModifyVariable", "WrapOperation", "WrapMethod").contains(kind)) continue;
                        var data = values(injection);
                        for (Object selectorObject : (List<?>)data.get("method")) {
                            String selector = (String)selectorObject;
                            var targets = target.methods.stream().filter(m -> selector.equals(m.name) || selector.equals(m.name + m.desc)).toList();
                            assertEquals(1, targets.size(), name + " selector " + selector);
                            MethodNode method = targets.getFirst();
                            String label = name + "." + callback.name + " -> " + selector;
                            assertEquals(isStatic(method.access), isStatic(callback.access), label + " staticness");
                            if (kind.equals("Inject")) {
                                var expected = args(method.desc);
                                expected.add(Type.getType(Type.getReturnType(method.desc).equals(Type.VOID_TYPE) ? CALLBACK : CALLBACK_RETURN));
                                handler(callback, expected, Type.VOID_TYPE, label);
                            } else if (kind.equals("WrapMethod")) {
                                var expected = args(method.desc);
                                expected.add(Type.getType(OPERATION));
                                handler(callback, expected, Type.getReturnType(method.desc), label);
                            } else if (kind.equals("ModifyVariable")) {
                                var callbackArgs = Type.getArgumentTypes(callback.desc);
                                assertEquals(1, callbackArgs.length, label);
                                assertEquals(callbackArgs[0], Type.getReturnType(callback.desc), label);
                                assertEquals(1L, args(method.desc).stream().filter(callbackArgs[0]::equals).count(), label + " unambiguous argument");
                                assertEquals(true, data.get("argsOnly"), label);
                            } else {
                                Object atValue = data.get("at");
                                var at = values(atValue instanceof List<?> list ? (AnnotationNode)list.getFirst() : (AnnotationNode)atValue);
                                String selectorTarget = (String)at.get("target");
                                var matches = new ArrayList<AbstractInsnNode>();
                                List<Type> expected;
                                Type result;
                                if ("NEW".equals(at.get("value"))) {
                                    result = Type.getReturnType(selectorTarget);
                                    for (var instruction : method.instructions) if (instruction instanceof TypeInsnNode t
                                            && t.getOpcode() == Opcodes.NEW && t.desc.equals(result.getInternalName())) matches.add(t);
                                    expected = args(selectorTarget);
                                } else {
                                    for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                                            && selectorTarget.equals("L" + call.owner + ";" + call.name + call.desc)) matches.add(call);
                                    assertFalse(matches.isEmpty(), label + " invocation " + selectorTarget);
                                    var call = (MethodInsnNode)matches.getFirst();
                                    expected = args(call.desc);
                                    if (call.getOpcode() != Opcodes.INVOKESTATIC) expected.addFirst(Type.getObjectType(call.owner));
                                    result = Type.getReturnType(call.desc);
                                }
                                int ordinal = (Integer)at.getOrDefault("ordinal", -1);
                                if (ordinal >= 0) assertTrue(matches.size() > ordinal, label + " ordinal");
                                else assertEquals(1, matches.size(), label + " unique invocation");
                                if (kind.equals("WrapOperation")) expected.add(Type.getType(OPERATION));
                                if (Type.getArgumentTypes(callback.desc).length > expected.size()) expected.addAll(args(method.desc));
                                handler(callback, expected, result, label);
                            }
                            injections++;
                        }
                    }
                }
                classes++;
            }
            assertEquals(13, classes);
            assertEquals(19, injections);
            assertEquals(13, shadows);
        }
    }
}
