package org.skdocs.skdocstool.docs.registration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;

public final class SkrRegistrationReader {
    private SkrRegistrationReader() {}

    public static RegistrationIndex read(Plugin plugin) {
        RegistrationIndex docs = new RegistrationIndex();
        if (plugin == null) return docs;
        for (Object registration : findRegistrations(plugin)) {
            Map<String, String> categories = Map.of(
                "getConditions", "condition", "getEffects", "effect",
                "getExpressions", "expressionClass", "getSections", "section",
                "getStructures", "structureClass", "getEvents", "skriptEventClass",
                "getTypes", "classInfo", "getFunctions", "function");
            for (var category : categories.entrySet()) {
                try {
                    Object entries = registration.getClass().getMethod(category.getKey()).invoke(registration);
                    if (!(entries instanceof Collection<?> registrars)) continue;
                    for (Object registrar : registrars) {
                        if (!Boolean.TRUE.equals(registrar.getClass().getMethod("isRegistered").invoke(registrar))) continue;
                        Object metadata = registrar.getClass().getMethod("getDocumentation").invoke(registrar);
                        Field field = findField(registrar.getClass(), category.getValue());
                        if (field == null || !field.trySetAccessible()) continue;
                        Object target = field.get(registrar);
                        RegistrationMetadata doc = new RegistrationMetadata(
                            (String) read(metadata, "getId"), (String) read(metadata, "getName"),
                            (String[]) read(metadata, "getDescription"),
                            (String[]) read(metadata, "getExamples"),
                            (String[]) read(metadata, "getSince"),
                            (String[]) read(metadata, "getKeywords"),
                            Boolean.TRUE.equals(read(metadata, "isNoDoc")));
                        if (target instanceof Class<?> type) {
                            Field patterns = findField(registrar.getClass(), "patterns");
                            if (patterns == null || !patterns.trySetAccessible()) continue;
                            docs.putSyntax(type, (String[]) patterns.get(registrar), doc);
                        } else if (target != null) {
                            docs.put(target, doc);
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING,
                        "Cannot read registration documentation for " + category.getKey(), e);
                }
            }
        }
        return docs;
    }

    private static List<Object> findRegistrations(Plugin plugin) {
        List<Object> found = new java.util.ArrayList<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        discover(plugin, 0, visited, found, plugin);
        return found;
    }

    private static boolean isRegistration(Class<?> type) {
        try {
            for (String name : List.of("getAddon", "getEffects", "getConditions", "getEvents", "getTypes"))
                type.getMethod(name);
            return true;
        } catch (NoSuchMethodException absent) { return false; }
    }

    private static void discover(Object owner, int depth, Set<Object> visited,
                                 List<Object> found, Plugin plugin) {
        if (owner == null || depth > 2 || !visited.add(owner)) return;
        if (isRegistration(owner.getClass())) { found.add(owner); return; }

        for (String getter : List.of("getRegistration", "getAddonLoader", "getLoader")) {
            try {
                discover(owner.getClass().getMethod(getter).invoke(owner), depth + 1, visited, found, plugin);
            } catch (NoSuchMethodException absent) {

            } catch (ReflectiveOperationException | RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Cannot read " + getter + " for SKR documentation", e);
            }
        }

        for (Class<?> type = owner.getClass(); type != null && type != org.bukkit.plugin.java.JavaPlugin.class;
             type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isSynthetic()) continue;
                Class<?> fieldType = field.getType();
                boolean holder = !Modifier.isStatic(field.getModifiers()) && fieldType.getClassLoader() == plugin.getClass().getClassLoader()
                    && fieldType.getSimpleName().endsWith("Loader");
                if (!isRegistration(fieldType) && !holder) continue;
                try {
                    if (field.trySetAccessible()) discover(field.get(owner), depth + 1, visited, found, plugin);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "Cannot read SKR field " + field.getName(), e);
                }
            }
        }
    }

    private static Object read(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(name);
        if (!method.trySetAccessible()) throw new IllegalAccessException(name);
        return method.invoke(target);
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException absent) {  }
        }
        return null;
    }
}
