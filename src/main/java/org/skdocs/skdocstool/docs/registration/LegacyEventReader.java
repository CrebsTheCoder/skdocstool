package org.skdocs.skdocstool.docs.registration;

import org.bukkit.plugin.Plugin;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.logging.Level;

public final class LegacyEventReader {
    private LegacyEventReader() {}

    public static LegacyEventIndex read(Plugin plugin) {
        LegacyEventIndex index = new LegacyEventIndex();
        if (plugin == null) return index;
        try {
            Path path = Path.of(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            try (JarFile jar = new JarFile(path.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (!entry.getName().endsWith(".class")) continue;
                    try (var input = jar.getInputStream(entry)) {
                        readClass(input.readAllBytes(), index);
                    } catch (AnalyzerException | IllegalArgumentException e) {
                        plugin.getLogger().log(Level.WARNING, "Cannot inspect legacy registrations in " + entry.getName(), e);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Cannot inspect legacy event registrations", e);
        }
        return index;
    }

    public static void readClass(byte[] bytes, LegacyEventIndex index) throws AnalyzerException {
        String constants = new String(bytes, StandardCharsets.ISO_8859_1);
        if (!constants.contains("registerEvent") || !constants.contains("ch/njol/skript/Skript")) return;
        ClassNode type = new ClassNode();
        new ClassReader(bytes).accept(type, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        for (MethodNode method : type.methods) {
            boolean hasRegistration = false;
            for (AbstractInsnNode instruction : method.instructions)
                if (isRegistration(instruction)) { hasRegistration = true; break; }
            if (!hasRegistration) continue;
            Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter(Opcodes.ASM9) {
                @Override
                public SourceValue copyOperation(AbstractInsnNode instruction, SourceValue value) { return value; }
            });
            Frame<SourceValue>[] frames = analyzer.analyze(type.name, method);
            for (int i = 0; i < method.instructions.size(); i++) {
                if (!isRegistration(method.instructions.get(i)) || frames[i] == null) continue;
                Frame<SourceValue> frame = frames[i];
                int start = frame.getStackSize() - 4;
                if (start < 0) continue;
                Object name = constant(frame.getStack(start));
                Object syntax = constant(frame.getStack(start + 1));
                Object singleEvent = constant(frame.getStack(start + 2));
                List<Object> eventTypes = singleEvent instanceof Type ? List.of(singleEvent)
                    : arrayValues(method, frames, i, frame.getStack(start + 2));
                List<Object> patterns = arrayValues(method, frames, i, frame.getStack(start + 3));
                if (!(name instanceof String title) || !(syntax instanceof Type syntaxType)
                    || eventTypes == null || patterns == null || eventTypes.isEmpty() || patterns.isEmpty()
                    || eventTypes.stream().anyMatch(value -> !(value instanceof Type))
                    || patterns.stream().anyMatch(value -> !(value instanceof String))) continue;
                index.add(title, syntaxType.getClassName(),
                    eventTypes.stream().map(value -> ((Type) value).getClassName()).toList(),
                    patterns.stream().map(String.class::cast).toList());
            }
        }
    }

    private static boolean isRegistration(AbstractInsnNode instruction) {
        return instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
            && call.owner.equals("ch/njol/skript/Skript") && call.name.equals("registerEvent")
            && Type.getArgumentTypes(call.desc).length == 4;
    }

    private static Object constant(SourceValue value) {
        if (value.insns.size() != 1) return null;
        AbstractInsnNode instruction = value.insns.iterator().next();
        if (instruction instanceof LdcInsnNode literal) return literal.cst;
        if (instruction instanceof IntInsnNode literal) return literal.operand;
        int opcode = instruction.getOpcode();
        return opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5 ? opcode - Opcodes.ICONST_0 : null;
    }

    private static List<Object> arrayValues(MethodNode method, Frame<SourceValue>[] frames,
                                          int callIndex, SourceValue array) {
        if (array.insns.size() != 1) return null;
        AbstractInsnNode allocation = array.insns.iterator().next();
        if (allocation.getOpcode() != Opcodes.ANEWARRAY) return null;
        int allocationIndex = method.instructions.indexOf(allocation);
        Frame<SourceValue> allocationFrame = frames[allocationIndex];
        Object length = constant(allocationFrame.getStack(allocationFrame.getStackSize() - 1));
        if (!(length instanceof Integer count) || count < 0) return null;
        Map<Integer, Object> values = new TreeMap<>();
        for (int i = allocationIndex + 1; i < callIndex; i++) {
            if (method.instructions.get(i).getOpcode() != Opcodes.AASTORE || frames[i] == null) continue;
            Frame<SourceValue> frame = frames[i];
            int top = frame.getStackSize();
            if (!frame.getStack(top - 3).insns.equals(array.insns)) continue;
            Object position = constant(frame.getStack(top - 2));
            Object value = constant(frame.getStack(top - 1));
            if (!(position instanceof Integer offset) || value == null || values.putIfAbsent(offset, value) != null)
                return null;
        }
        if (values.size() != count) return null;
        for (int i = 0; i < count; i++) if (!values.containsKey(i)) return null;
        return new ArrayList<>(values.values());
    }
}
