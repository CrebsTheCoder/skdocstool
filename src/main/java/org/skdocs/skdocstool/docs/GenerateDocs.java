package org.skdocs.skdocstool.docs;

import ch.njol.skript.classes.Changer;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.doc.*;
import ch.njol.skript.lang.function.Function;
import ch.njol.skript.lang.function.Functions;
import ch.njol.skript.lang.function.Signature;
import ch.njol.skript.registrations.Classes;
import com.github.shanebeee.skr.Documentation;
import com.github.shanebeee.skr.Registration;
import com.google.gson.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;
import org.skriptlang.skript.common.function.DefaultFunction;
import org.skriptlang.skript.common.function.Parameter;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import org.skriptlang.skript.Skript;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

public class GenerateDocs {

    record RegistrationDoc(String name, String[] description, String[] examples,
                           String[] since, String[] keywords, boolean noDoc) {}

    private static final Set<String> REGISTRATION_METHODS =
            Set.of("getConditions", "getEffects", "getExpressions", "getEvents");

    private static Registration findSkrRegistration(Plugin plugin) {
        if (plugin == null) return null;
        try {
            Class.forName("com.github.shanebeee.skr.Registration");
        } catch (ClassNotFoundException e) {
            return null;
        }
        return findFieldOfType(plugin, Registration.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> T findFieldOfType(Object obj, Class<T> target) {
        if (obj == null) return null;
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val == null) continue;
                    if (target.isInstance(val)) return (T) val;
                    for (Class<?> c2 = val.getClass(); c2 != null && c2 != Object.class; c2 = c2.getSuperclass()) {
                        for (Field f2 : c2.getDeclaredFields()) {
                            try {
                                f2.setAccessible(true);
                                Object val2 = f2.get(val);
                                if (target.isInstance(val2)) return (T) val2;
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static Map<Class<?>, RegistrationDoc> buildSkrDocMap(Registration reg) {
        Map<Class<?>, RegistrationDoc> map = new HashMap<>();
        if (reg == null) return map;

        String[][] entries = {
                {"getEffects",     "effect"},
                {"getConditions",  "condition"},
                {"getExpressions", "expressionClass"},
                {"getSections",    "section"},
                {"getStructures",  "structureClass"},
                {"getTypes",       null},
                {"getFunctions",   null},
                {"getEvents",      null},
        };

        for (String[] entry : entries) {
            String getter = entry[0];
            String fieldName = entry[1];
            try {
                Method listMethod = reg.getClass().getMethod(getter);
                Collection<?> registrars = (Collection<?>) listMethod.invoke(reg);
                for (Object r : registrars) {
                    if (r == null) continue;
                    Documentation doc = skrGetDocumentation(r);
                    if (doc == null) continue;
                    RegistrationDoc rd = skrDocToRegistrationDoc(doc);

                    if (fieldName != null) {
                        Class<?> cls = skrGetClassField(r, fieldName);
                        putSkrDoc(map, cls, rd);
                    } else if ("getTypes".equals(getter)) {
                        Object classInfo = skrGetField(r, "classInfo");
                        if (classInfo != null) {
                            Method getC = findMethod(classInfo.getClass(), "getC");
                            if (getC != null) {
                                getC.setAccessible(true);
                                Object cls = getC.invoke(classInfo);
                                if (cls instanceof Class<?> c) putSkrDoc(map, c, rd);
                            }
                        }
                    } else if ("getFunctions".equals(getter)) {
                        Object fn = skrGetField(r, "function");
                        if (fn != null) putSkrDoc(map, fn.getClass(), rd);
                    } else if ("getEvents".equals(getter)) {
                        Class<?> skriptCls = skrGetClassField(r, "skriptEventClass");
                        putSkrDoc(map, skriptCls, rd);
                        Object evClassesRaw = skrGetField(r, "eventClasses");
                        if (evClassesRaw instanceof Class<?>[] evClasses) {
                            for (Class<?> ec : evClasses) putSkrDoc(map, ec, rd);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        return map;
    }

    private static Documentation skrGetDocumentation(Object registrar) {
        try {
            Method m = findMethod(registrar.getClass(), "getDocumentation");
            if (m == null) return null;
            m.setAccessible(true);
            Object result = m.invoke(registrar);
            if (result instanceof Documentation doc) return doc;
        } catch (Exception ignored) {}
        return null;
    }

    private static Class<?> skrGetClassField(Object obj, String fieldName) {
        Object val = skrGetField(obj, fieldName);
        return val instanceof Class<?> c ? c : null;
    }

    private static Object skrGetField(Object obj, String fieldName) {
        if (obj == null) return null;
        Field f = findField(obj.getClass(), fieldName);
        if (f == null) return null;
        try {
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception ignored) { return null; }
    }

    private static void putSkrDoc(Map<Class<?>, RegistrationDoc> map, Class<?> cls, RegistrationDoc rd) {
        if (cls == null || rd == null) return;
        map.put(cls, rd);
    }

    private static RegistrationDoc skrDocToRegistrationDoc(Documentation doc) {
        if (doc == null) return null;
        String name     = doc.getName();
        boolean noDoc   = doc.isNoDoc();
        String[] desc     = skrInvokeStrArr(doc, "getDescription");
        String[] examples = skrInvokeStrArr(doc, "getExamples");
        String[] since    = skrInvokeStrArr(doc, "getSince");
        String[] keywords = skrInvokeStrArr(doc, "getKeywords");
        return new RegistrationDoc(name, desc, examples, since, keywords, noDoc);
    }

    private static String[] skrInvokeStrArr(Object obj, String methodName) {
        try {
            Method m = obj.getClass().getDeclaredMethod(methodName);
            m.setAccessible(true);
            Object val = m.invoke(obj);
            if (val instanceof String[] arr) return arr;
        } catch (Exception ignored) {}
        return null;
    }

    private static void mergeSkrDocs(Map<Class<?>, RegistrationDoc> primary,
                                     Map<Class<?>, RegistrationDoc> skr) {
        for (Map.Entry<Class<?>, RegistrationDoc> entry : skr.entrySet()) {
            Class<?> cls = entry.getKey();
            RegistrationDoc skrDoc = entry.getValue();
            RegistrationDoc existing = primary.get(cls);
            if (existing == null) {
                primary.put(cls, skrDoc);
            } else {
                String name        = existing.name()        != null && !existing.name().isBlank()               ? existing.name()        : skrDoc.name();
                String[] desc      = hasContent(existing.description())  ? existing.description()  : skrDoc.description();
                String[] examples  = hasContent(existing.examples())     ? existing.examples()     : skrDoc.examples();
                String[] since     = hasContent(existing.since())        ? existing.since()        : skrDoc.since();
                String[] keywords  = hasContent(existing.keywords())     ? existing.keywords()     : skrDoc.keywords();
                boolean noDoc      = existing.noDoc() || skrDoc.noDoc();
                primary.put(cls, new RegistrationDoc(name, desc, examples, since, keywords, noDoc));
            }
        }
    }

    private static boolean hasContent(String[] arr) {
        return arr != null && arr.length > 0;
    }

    private static Object findRegistrationObject(Plugin plugin) {
        for (Class<?> c = plugin.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(plugin);
                    if (val == null) continue;
                    if (isRegistrationLike(val.getClass())) return val;
                    for (Class<?> c2 = val.getClass(); c2 != null && c2 != Object.class; c2 = c2.getSuperclass()) {
                        for (Field f2 : c2.getDeclaredFields()) {
                            try {
                                f2.setAccessible(true);
                                Object val2 = f2.get(val);
                                if (val2 != null && isRegistrationLike(val2.getClass())) return val2;
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static boolean isRegistrationLike(Class<?> cls) {
        Set<String> methods = new HashSet<>();
        for (Method m : cls.getMethods()) methods.add(m.getName());
        int matches = 0;
        for (String name : REGISTRATION_METHODS) if (methods.contains(name)) matches++;
        return matches >= 3;
    }

    private static Map<Class<?>, RegistrationDoc> buildRegistrationDocMap(Plugin plugin) {
        Map<Class<?>, RegistrationDoc> map = new HashMap<>();

        if (plugin != null) {
            try {
                Object registration = findRegistrationObject(plugin);
                if (registration != null) {
                    Class<?> regClass = registration.getClass();

                    for (String getter : new String[]{
                            "getConditions", "getEffects", "getExpressions",
                            "getEvents", "getSections", "getStructures",
                            "getTypes", "getFunctions"}) {
                        try {
                            Method listMethod = regClass.getMethod(getter);
                            Collection<?> registrars = (Collection<?>) listMethod.invoke(registration);
                            for (Object registrar : registrars) {
                                if (registrar == null) continue;
                                RegistrationDoc doc = extractRegistrationDoc(registrar);
                                if (doc == null) continue;

                                if ("getEvents".equals(getter)) {
                                    RegistrationDoc finalDoc = doc;
                                    try {
                                        Field f = findField(registrar.getClass(), "eventClasses");
                                        if (f != null) {
                                            f.setAccessible(true);
                                            if (f.get(registrar) instanceof Class<?>[] classes) {
                                                for (Class<?> cls : classes) map.put(cls, finalDoc);
                                            }
                                        } else {
                                            for (Field field : registrar.getClass().getDeclaredFields()) {
                                                Class<?> type = field.getType();
                                                if (type.isArray()) {
                                                    Class<?> comp = type.getComponentType();
                                                    if (comp != null && comp.getPackageName().startsWith("org.bukkit.event")) {
                                                        field.setAccessible(true);
                                                        if (field.get(registrar) instanceof Class<?>[] classes) {
                                                            for (Class<?> cls : classes) map.put(cls, finalDoc);
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                } else {
                                    Class<?> syntaxClass = resolveRegistrarClass(registrar);
                                    if (syntaxClass != null) map.put(syntaxClass, doc);
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }

        Registration skrReg = findSkrRegistration(plugin);
        if (skrReg != null) {
            Map<Class<?>, RegistrationDoc> skrDocs = buildSkrDocMap(skrReg);
            mergeSkrDocs(map, skrDocs);
        }

        return map;
    }

    private static Class<?> resolveRegistrarClass(Object registrar) {
        for (String fn : new String[]{
                "condition", "effect", "expressionClass", "skriptEventClass",
                "structureClass", "section", "type", "functionClass"}) {
            try {
                Field f = findField(registrar.getClass(), fn);
                if (f == null) continue;
                f.setAccessible(true);
                Object val = f.get(registrar);
                if (val instanceof Class<?> cls) return cls;
            } catch (Exception ignored) {}
        }

        try {
            Field f = findField(registrar.getClass(), "classInfo");
            if (f != null) {
                f.setAccessible(true);
                Object ci = f.get(registrar);
                if (ci != null) {
                    Method getC = findMethod(ci.getClass(), "getC");
                    if (getC != null) {
                        getC.setAccessible(true);
                        Object cls = getC.invoke(ci);
                        if (cls instanceof Class<?> c) return c;
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(name)) return f;
            }
        }
        return null;
    }

    private static RegistrationDoc extractRegistrationDoc(Object registrar) {
        try {
            Method docMethod = findMethod(registrar.getClass(), "getDocumentation");
            if (docMethod == null) return null;
            docMethod.setAccessible(true);
            Object doc = docMethod.invoke(registrar);
            if (doc == null) return null;

            try {
                Class<?> skrDocClass = Class.forName("com.github.shanebeee.skr.Documentation");
                if (skrDocClass.isInstance(doc)) {
                    return skrDocToRegistrationDoc((Documentation) doc);
                }
            } catch (ClassNotFoundException ignored) {}

            String name = invokeStr(doc, "getName");
            String[] desc = invokeStrArr(doc, "getDescription");
            String[] examples = invokeStrArr(doc, "getExamples");
            String[] since = invokeStrArr(doc, "getSince");
            String[] keywords = invokeStrArr(doc, "getKeywords");
            boolean noDoc = invokeBoolean(doc, "isNoDoc");

            return new RegistrationDoc(name, desc, examples, since, keywords, noDoc);
        } catch (Exception ignored) {}
        return null;
    }

    private static RegistrationDoc docFromSyntaxInfo(SyntaxInfo<?> info) {
        String name = invokeStr(info, "name");
        String[] desc = invokeStrArr(info, "description");
        String[] examples = invokeStrArr(info, "examples");
        String[] since = invokeStrArr(info, "since");
        String[] keywords = invokeStrArr(info, "keywords");
        if (name == null && desc == null && examples == null && since == null) return null;
        return new RegistrationDoc(name, desc, examples, since, keywords, false);
    }

    private static Object invokeMethod(Object obj, String method) {
        try {
            Method m = findMethod(obj.getClass(), method);
            if (m == null) return null;
            m.setAccessible(true);
            return m.invoke(obj);
        } catch (Exception ignored) { return null; }
    }

    @SuppressWarnings("all")
    private static String invokeStr(Object obj, String method) {
        Object val = invokeMethod(obj, method);
        return val instanceof String ? (String) val : null;
    }

    private static String[] invokeStrArr(Object obj, String method) {
        Object val = invokeMethod(obj, method);
        if (val instanceof String[] arr) return arr;
        if (val instanceof Collection<?> col) return col.stream().map(Object::toString).toArray(String[]::new);
        if (val instanceof String s) return new String[]{s};
        return null;
    }

    @SuppressWarnings("all")
    private static boolean invokeBoolean(Object obj, String method) {
        Object val = invokeMethod(obj, method);
        return Boolean.TRUE.equals(val);
    }

    private static Method findMethod(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) return m;
            }
        }
        return null;
    }

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    public static void generate(CommandSender sender, JavaPlugin plugin) {
        Logger log = plugin.getLogger();
        Skript skript = ch.njol.skript.Skript.instance();

        List<SkriptAddon> addons = new ArrayList<>(skript.addons());
        if (addons.stream().noneMatch(a -> a.name().equalsIgnoreCase("Skript"))) {
            addons.add(skript);
        }
        addons.sort(Comparator.comparing(a -> a.name().toLowerCase(Locale.ROOT)));

        Path outDir = plugin.getDataFolder().toPath().resolve("documentation");
        try {
            Files.createDirectories(outDir);
        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed to create output directory: " + e.getMessage(), NamedTextColor.RED));
            log.severe("Failed to create output directory: " + e.getMessage());
            return;
        }

        int written = 0;
        for (SkriptAddon addon : addons) {
            String safe = sanitize(addon.name());
            try {
                JsonObject skdocs = buildAddonDocs(addon);
                Files.writeString(outDir.resolve("skdocs-" + safe + ".json"), GSON.toJson(skdocs));

                JsonObject skriptHub = buildSkriptHubDocs(addon);
                Files.writeString(outDir.resolve("skripthub-" + safe + ".json"), GSON.toJson(skriptHub));

                written++;
                log.info("Generated docs for " + addon.name());
            } catch (Exception e) {
                sender.sendMessage(Component.text("Failed for " + addon.name() + ": " + e.getMessage(), NamedTextColor.RED));
                log.severe("Failed generating docs for " + addon.name() + ": " + e.getMessage());
            }
        }

        if (written == 0) {
            sender.sendMessage(Component.text("No docs were generated.", NamedTextColor.RED));
        } else {
            sender.sendMessage(Component.text(
                    "Generated docs for " + written + " addon(s) in " + outDir, NamedTextColor.GREEN));
        }
    }

    private static Plugin findPluginForAddon(SkriptAddon addon) {
        Plugin p = Bukkit.getPluginManager().getPlugin(addon.name());
        if (p != null) return p;

        ClassLoader syntaxLoader = resolveLoaderFromSyntax(addon);
        if (syntaxLoader != null) {
            for (Plugin pl : Bukkit.getPluginManager().getPlugins()) {
                if (pl.getClass().getClassLoader() == syntaxLoader) return pl;
            }
        }

        ClassLoader sourceLoader = addon.source().getClassLoader();
        for (Plugin pl : Bukkit.getPluginManager().getPlugins()) {
            if (pl.getClass().getClassLoader() == sourceLoader) return pl;
        }

        String addonNameLower = addon.name().toLowerCase(Locale.ROOT);
        for (Plugin pl : Bukkit.getPluginManager().getPlugins()) {
            if (pl.getPluginMeta().getName().toLowerCase(Locale.ROOT).equals(addonNameLower)) return pl;
        }

        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ClassLoader resolveLoaderFromSyntax(SkriptAddon addon) {
        SyntaxRegistry registry = addon.syntaxRegistry();
        for (SyntaxRegistry.Key key : new SyntaxRegistry.Key[]{
                SyntaxRegistry.CONDITION, SyntaxRegistry.EFFECT,
                SyntaxRegistry.EXPRESSION, SyntaxRegistry.SECTION,
                SyntaxRegistry.STRUCTURE}) {
            for (Object raw : registry.syntaxes(key)) {
                if (raw instanceof SyntaxInfo<?> info) {
                    ClassLoader cl = info.type().getClassLoader();
                    if (cl != null && isPluginClassLoader(cl)) return cl;
                }
            }
        }
        return null;
    }

    private static ClassLoader resolveAddonClassLoader(SkriptAddon addon) {
        Plugin owningPlugin = findPluginForAddon(addon);
        if (owningPlugin != null) return owningPlugin.getClass().getClassLoader();
        return addon.source().getClassLoader();
    }

    private static JsonObject buildAddonDocs(SkriptAddon addon) {
        SyntaxRegistry registry = addon.syntaxRegistry();
        ClassLoader loader = resolveAddonClassLoader(addon);
        Map<Class<?>, RegistrationDoc> regDocs = buildRegistrationDocMap(findPluginForAddon(addon));

        JsonArray events = eventsArray(registry, loader, regDocs, false, addon.name());
        JsonArray conditions = syntaxArray(registry, SyntaxRegistry.CONDITION, loader, regDocs);
        JsonArray effects = syntaxArray(registry, SyntaxRegistry.EFFECT, loader, regDocs);
        JsonArray expressions = syntaxArray(registry, SyntaxRegistry.EXPRESSION, loader, regDocs);
        JsonArray sections = syntaxArray(registry, SyntaxRegistry.SECTION, loader, regDocs);
        JsonArray structures = syntaxArray(registry, SyntaxRegistry.STRUCTURE, loader, regDocs);
        JsonArray types = typesArray(loader, regDocs);
        JsonArray functions = functionsArray(addon, loader);

        Set<String> idSet = new LinkedHashSet<>();
        resolveCollisions(events, idSet, addon.name());
        resolveCollisions(conditions, idSet, addon.name());
        resolveCollisions(effects, idSet, addon.name());
        resolveCollisions(expressions, idSet, addon.name());
        resolveCollisions(types, idSet, addon.name());
        resolveCollisions(functions, idSet, addon.name());
        resolveCollisions(sections, idSet, addon.name());
        resolveCollisions(structures, idSet, addon.name());

        assignDocIds(addon.name(), events, conditions, effects, expressions,
                types, functions, sections, structures);

        JsonObject root = new JsonObject();
        root.addProperty("addon", addon.name());
        root.add("conditions", conditions);
        root.add("effects", effects);
        root.add("expressions", expressions);
        root.add("events", events);
        root.add("sections", sections);
        root.add("structures", structures);
        root.add("types", types);
        root.add("functions", functions);
        return root;
    }

    private static JsonObject buildSkriptHubDocs(SkriptAddon addon) {
        SyntaxRegistry registry = addon.syntaxRegistry();
        ClassLoader loader = resolveAddonClassLoader(addon);
        Map<Class<?>, RegistrationDoc> regDocs = buildRegistrationDocMap(findPluginForAddon(addon));

        JsonArray events = shEventsArray(registry, loader, regDocs, addon.name());
        JsonArray conditions = shSyntaxArray(registry, SyntaxRegistry.CONDITION, loader, regDocs);
        JsonArray effects = shSyntaxArray(registry, SyntaxRegistry.EFFECT, loader, regDocs);
        JsonArray expressions = shSyntaxArray(registry, SyntaxRegistry.EXPRESSION, loader, regDocs);
        JsonArray types = shTypesArray(loader, regDocs);
        JsonArray functions = shFunctionsArray(addon, loader);
        JsonArray sections = shSyntaxArray(registry, SyntaxRegistry.SECTION, loader, regDocs);
        JsonArray structures = shSyntaxArray(registry, SyntaxRegistry.STRUCTURE, loader, regDocs);

        Set<String> idSet = new LinkedHashSet<>();
        resolveCollisions(events, idSet, addon.name());
        resolveCollisions(conditions, idSet, addon.name());
        resolveCollisions(effects, idSet, addon.name());
        resolveCollisions(expressions, idSet, addon.name());
        resolveCollisions(types, idSet, addon.name());
        resolveCollisions(functions, idSet, addon.name());
        resolveCollisions(sections, idSet, addon.name());
        resolveCollisions(structures, idSet, addon.name());

        JsonObject root = new JsonObject();

        JsonObject metadata = new JsonObject();
        metadata.addProperty("version", resolveVersion(addon));
        root.add("metadata", metadata);

        shAddIfNotEmpty(root, "events", events);
        shAddIfNotEmpty(root, "conditions", conditions);
        shAddIfNotEmpty(root, "effects", effects);
        shAddIfNotEmpty(root, "expressions", expressions);
        shAddIfNotEmpty(root, "types", types);
        shAddIfNotEmpty(root, "functions", functions);
        shAddIfNotEmpty(root, "sections", sections);
        shAddIfNotEmpty(root, "structures", structures);
        return root;
    }

    private static void shAddIfNotEmpty(JsonObject root, String key, JsonArray arr) {
        if (!arr.isEmpty()) root.add(key, arr);
    }

    private static <I extends SyntaxInfo<?>> JsonArray shSyntaxArray(
            SyntaxRegistry registry, SyntaxRegistry.Key<I> key, ClassLoader loader,
            Map<Class<?>, RegistrationDoc> regDocs) {
        return buildSyntaxArray(registry, key, loader, regDocs, GenerateDocs::shSyntaxEntry);
    }

    private static <I extends SyntaxInfo<?>> JsonArray syntaxArray(
            SyntaxRegistry registry, SyntaxRegistry.Key<I> key, ClassLoader loader,
            Map<Class<?>, RegistrationDoc> regDocs) {
        return buildSyntaxArray(registry, key, loader, regDocs, GenerateDocs::syntaxEntry);
    }

    @FunctionalInterface
    private interface SyntaxEntryBuilder {
        JsonObject build(String id, Class<?> type, Collection<String> patterns, RegistrationDoc rd);
    }

    private static <I extends SyntaxInfo<?>> JsonArray buildSyntaxArray(
            SyntaxRegistry registry, SyntaxRegistry.Key<I> key, ClassLoader loader,
            Map<Class<?>, RegistrationDoc> regDocs, SyntaxEntryBuilder builder) {
        JsonArray arr = new JsonArray();
        for (I info : registry.syntaxes(key)) {
            Class<?> type = info.type();
            if (!isOwned(type, loader)) continue;
            RegistrationDoc rd = regDocs.get(type);
            if (rd != null && rd.noDoc()) continue;
            if (rd == null && isNoDoc(type)) continue;
            if (rd == null) rd = docFromSyntaxInfo(info);
            JsonObject obj = builder.build(DocumentationIdProvider.getId(info), type, info.patterns(), rd);
            if (obj != null) arr.add(obj);
        }
        return arr;
    }

    private static JsonArray shEventsArray(SyntaxRegistry registry, ClassLoader loader,
                                           Map<Class<?>, RegistrationDoc> regDocs, String addonName) {
        return eventsArray(registry, loader, regDocs, true, addonName);
    }

    private static String[] mergeArr(String[] primary, String[] fallback) {
        if (primary != null && primary.length > 0) return primary;
        return fallback;
    }

    private static JsonArray shTypesArray(ClassLoader loader, Map<Class<?>, RegistrationDoc> regDocs) {
        return typesArray(loader, true, regDocs);
    }

    private static JsonArray typesArray(ClassLoader loader, Map<Class<?>, RegistrationDoc> regDocs) {
        return typesArray(loader, false, regDocs);
    }

    private static JsonArray typesArray(ClassLoader loader, boolean skriptHub, Map<Class<?>, RegistrationDoc> regDocs) {
        JsonArray arr = new JsonArray();
        for (ClassInfo<?> info : Classes.getClassInfos()) {
            if (!isOwnedType(info, loader)) continue;
            if (ClassInfo.NO_DOC.equals(info.getDocName())) continue;

            String id = DocumentationIdProvider.getId(info);
            if (id == null || id.isBlank()) continue;

            RegistrationDoc rd = regDocs != null ? regDocs.get(info.getC()) : null;
            if (rd != null && rd.noDoc()) continue;

            String ciDocName = info.getDocName();
            String name;
            if (ciDocName != null && !ciDocName.isBlank() && !ciDocName.equals(info.getCodeName())) {
                name = ciDocName;
            } else if (rd != null && rd.name() != null && !rd.name().isBlank()) {
                name = rd.name();
            } else {
                name = ciDocName != null ? ciDocName : info.getCodeName();
            }

            String[] description = mergeArr(cleanHtml(info.getDescription()), rd != null ? rd.description() : null);
            String[] examples    = mergeArr(cleanHtml(info.getExamples()),    rd != null ? rd.examples()     : null);
            String   since       = info.getSince();
            if ((since == null || since.isBlank()) && rd != null && hasContent(rd.since())) {
                since = String.join(", ", rd.since());
            }

            JsonObject obj = new JsonObject();
            obj.addProperty("id", id);
            obj.addProperty("name", name);

            String[] usage = extractValidEnumValues(info);
            if (usage != null && usage.length > 0) {
                JsonArray usageArr = new JsonArray();
                for (String u : usage) {
                    if (!u.isBlank()) usageArr.add(u);
                }
                if (!usageArr.isEmpty()) obj.add("usage", usageArr);
            }

            if (skriptHub) {
                shAddStrings(obj, "description", description);
                shAddStrings(obj, "examples", examples);
                if (since != null && !since.isBlank()) {
                    JsonArray sa = new JsonArray();
                    sa.add(since);
                    obj.add("since", sa);
                }
                JsonArray patterns = typePatterns(info);
                if (!patterns.isEmpty()) obj.add("patterns", patterns);
                JsonArray changers = typeChangers(info);
                if (!changers.isEmpty()) obj.add("changers", changers);
            } else {
                obj.addProperty("codeName", info.getCodeName());
                obj.add("description", strArray(description));
                obj.add("examples", strArray(examples));
                obj.addProperty("since", since);
                obj.add("requiredPlugins", strArray(info.getRequiredPlugins()));
                obj.addProperty("source", typeSource(info));
            }
            arr.add(obj);
        }
        return arr;
    }

    private static JsonArray shFunctionsArray(SkriptAddon addon, ClassLoader loader) {
        return functionsArray(addon, loader, true);
    }

    private static JsonArray functionsArray(SkriptAddon addon, ClassLoader loader) {
        return functionsArray(addon, loader, false);
    }

    private static JsonArray functionsArray(SkriptAddon addon, ClassLoader loader, boolean skriptHub) {
        JsonArray arr = new JsonArray();
        for (Function<?> function : Functions.getFunctions()) {
            if (!isOwnedFunction(function, addon, loader)) continue;
            String id = DocumentationIdProvider.getId(function);
            if (id == null || id.isBlank()) continue;

            Signature<?> sig = function.getSignature();
            Class<?> implType = function.getClass();

            String[] description = functionDescriptionOf(function, implType);
            String[] examples = functionExamplesOf(function, implType);
            String[] since = sinceOf(implType);

            JsonObject obj = new JsonObject();
            obj.addProperty("id", id);
            obj.addProperty("name", function.getName());

            if (skriptHub) {
                shAddStrings(obj, "description", description);
                shAddStrings(obj, "examples", combineExamples(examples));
                shAddStrings(obj, "since", since);
                JsonArray pArr = new JsonArray();
                pArr.add(buildFunctionPattern(function.getName(), sig));
                obj.add("patterns", pArr);
                String returnType = returnTypeName(function.getReturnType());
                if (returnType != null) obj.addProperty("return type", returnType);
            } else {
                obj.add("description", strArray(description));
                obj.add("examples", strArray(combineExamples(examples)));
                obj.add("since", strArray(since));
                obj.add("parameters", parametersArray(sig));
                obj.addProperty("pattern", buildFunctionPattern(function.getName(), sig));
                obj.addProperty("returnType", returnTypeName(function.getReturnType()));
                obj.addProperty("returnSingle", function.isSingle());
                obj.addProperty("source", implType.getName());
            }
            arr.add(obj);
        }
        return arr;
    }

    private static JsonObject shSyntaxEntry(String id, Class<?> type, Collection<String> patterns,
                                            RegistrationDoc rd) {
        if (id == null || id.isBlank()) return null;

        String name = (rd != null && rd.name() != null && !rd.name().isBlank()) ? rd.name() : nameOf(type);
        if (name == null || name.isBlank()) return null;

        String[] desc = mergeArr(rd != null ? rd.description() : null, descriptionOf(type));
        String[] examples = combineExamples(mergeArr(rd != null ? rd.examples() : null, examplesOf(type)));
        String[] since = mergeArr(rd != null ? rd.since() : null, sinceOf(type));
        String[] keywords = mergeArr(rd != null ? rd.keywords() : null, keywordsOf(type));

        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("name", name);

        shAddStrings(obj, "description", desc);
        shAddStrings(obj, "examples", examples);
        shAddStrings(obj, "since", since);
        shAddStrings(obj, "required plugins", requiredPluginsOf(type));
        shAddStrings(obj, "keywords", keywords);

        if (patterns != null && !patterns.isEmpty()) {
            JsonArray pArr = new JsonArray();
            cleanPatterns(patterns).forEach(pArr::add);
            obj.add("patterns", pArr);
        }
        return obj;
    }

    private static void shAddStrings(JsonObject obj, String key, String[] values) {
        if (values == null || values.length == 0) return;
        JsonArray arr = new JsonArray();
        for (String v : values) if (v != null && !v.isBlank()) arr.add(v);
        if (!arr.isEmpty()) obj.add(key, arr);
    }

    private static JsonArray typeChangers(ClassInfo<?> info) {
        JsonArray arr = new JsonArray();
        Changer<?> changer = info.getChanger();
        if (changer == null) return arr;
        for (Changer.ChangeMode mode : Changer.ChangeMode.values()) {
            try {
                if (changer.acceptChange(mode) != null) {
                    arr.add(mode.name().toLowerCase(Locale.ROOT).replace('_', ' '));
                }
            } catch (Exception ignored) {}
        }
        return arr;
    }

    private static JsonArray typePatterns(ClassInfo<?> info) {
        JsonArray arr = new JsonArray();
        java.util.regex.Pattern[] userPatterns = info.getUserInputPatterns();
        if (userPatterns != null && userPatterns.length > 0) {
            for (java.util.regex.Pattern p : userPatterns) {
                String readable = p.pattern()
                        .replaceAll("\\\\([()\\[\\]])", "$1")
                        .replaceAll("\\(([^)]+)\\)\\?", "[$1]")
                        .replaceAll("(.)\\?", "[$1]")
                        .replaceAll("\\s+", " ").trim();
                arr.add(readable);
            }
        } else {
            arr.add(info.getCodeName());
        }
        return arr;
    }

    private static JsonArray eventsArray(SyntaxRegistry registry, ClassLoader loader,
                                         Map<Class<?>, RegistrationDoc> regDocs, boolean skriptHub, String addonName) {
        JsonArray arr = new JsonArray();
        for (BukkitSyntaxInfos.Event<?> info : registry.syntaxes(BukkitSyntaxInfos.Event.KEY)) {
            Class<?> type = info.type();

            boolean ownedByOrigin = addonName != null && info.origin().name().equalsIgnoreCase(addonName);
            boolean ownedByLoader = isOwned(type, loader);
            if (!ownedByOrigin && !ownedByLoader) continue;

            RegistrationDoc rd = null;
            for (Class<? extends org.bukkit.event.Event> eventClass : info.events()) {
                rd = regDocs.get(eventClass);
                if (rd != null) break;
            }
            if (rd == null) rd = regDocs.get(type);

            if (rd != null && rd.noDoc()) continue;
            if (rd == null && isNoDoc(type)) continue;

            String id = DocumentationIdProvider.getId(info);
            if (id == null || id.isBlank()) continue;

            JsonObject obj = new JsonObject();
            obj.addProperty("id", id);
            obj.addProperty("name", rd != null && rd.name() != null && !rd.name().isBlank() ? rd.name() : info.name());

            String[] desc = rd != null && rd.description() != null && rd.description().length > 0 ? rd.description() : info.description().toArray(new String[0]);
            String[] examples = combineExamples(rd != null && rd.examples() != null && rd.examples().length > 0 ? rd.examples() : info.examples().toArray(new String[0]));
            String[] since = rd != null && rd.since() != null && rd.since().length > 0 ? rd.since() : info.since().toArray(new String[0]);
            String[] keywords = rd != null ? rd.keywords() : null;

            if (!info.patterns().isEmpty()) {
                JsonArray pArr = new JsonArray();
                cleanPatterns(info.patterns()).forEach(p -> pArr.add("[on] " + p));
                obj.add("patterns", pArr);
            }

            if (skriptHub) {
                shAddStrings(obj, "description", desc);
                shAddStrings(obj, "examples", examples);
                shAddStrings(obj, "since", since);
                shAddStrings(obj, "required plugins", info.requiredPlugins().toArray(new String[0]));
                shAddStrings(obj, "keywords", keywords);
            } else {
                obj.add("description", strArray(desc));
                obj.add("examples", strArray(examples));
                obj.add("since", strArray(since));
                obj.add("requiredPlugins", strArray(info.requiredPlugins()));
                obj.add("keywords", strArray(mergeArr(keywords, info.keywords().toArray(new String[0]))));
                obj.addProperty("source", type.getName());
            }

            String[] eventValues = collectEventValues(info.events());
            if (eventValues != null && eventValues.length > 0) {
                if (skriptHub) {
                    shAddStrings(obj, "event values", eventValues);
                } else {
                    obj.add("event-values", strArray(eventValues));
                }
            }

            arr.add(obj);
        }
        return arr;
    }

    private static String buildFunctionPattern(String name, Signature<?> sig) {
        StringJoiner params = new StringJoiner(", ");
        for (Parameter<?> param : sig.parameters().all()) {
            String typeName = typeCodeName(param.type());
            String part = param.name() + ": " + (param.isSingle() ? typeName : typeName + "s");
            if (param.hasModifier(Parameter.Modifier.OPTIONAL)) part = "[" + part + "]";
            params.add(part);
        }
        String returnType = returnTypeName(sig.getReturnType());
        String signature = name + "(" + params + ")";
        if (returnType != null) signature += " :: " + (sig.isSingle() ? returnType : returnType + "s");
        return signature;
    }

    private static JsonArray parametersArray(Signature<?> sig) {
        JsonArray arr = new JsonArray();
        for (Parameter<?> param : sig.parameters().all()) {
            JsonObject p = new JsonObject();
            p.addProperty("name", param.name());
            p.addProperty("type", typeCodeName(param.type()));
            p.addProperty("single", param.isSingle());
            p.addProperty("optional", param.hasModifier(Parameter.Modifier.OPTIONAL));
            arr.add(p);
        }
        return arr;
    }

    private static String returnTypeName(ClassInfo<?> returnType) {
        return returnType != null ? returnType.getCodeName() : null;
    }

    private static String typeCodeName(Class<?> type) {
        if (type == null) return null;
        ClassInfo<?> info = Classes.getSuperClassInfo(type);
        return info.getCodeName();
    }

    private static JsonObject syntaxEntry(String id, Class<?> type, Collection<String> patterns,
                                          RegistrationDoc rd) {
        String name = (rd != null && rd.name() != null && !rd.name().isBlank()) ? rd.name() : nameOf(type);

        String[] desc = mergeArr(rd != null ? rd.description() : null, descriptionOf(type));
        String[] examples = combineExamples(mergeArr(rd != null ? rd.examples() : null, examplesOf(type)));
        String[] since = mergeArr(rd != null ? rd.since() : null, sinceOf(type));
        String[] keywords = mergeArr(rd != null ? rd.keywords() : null, keywordsOf(type));

        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("name", name);
        obj.add("patterns", strArray(cleanPatterns(patterns)));
        obj.add("description", strArray(desc));
        obj.add("examples", strArray(examples));
        obj.add("since", strArray(since));
        obj.add("requiredPlugins", strArray(requiredPluginsOf(type)));
        obj.add("keywords", strArray(keywords));
        obj.addProperty("source", type != null ? type.getName() : null);
        return obj;
    }

    @SuppressWarnings("unchecked")
    private static String[] collectEventValues(Collection<Class<? extends org.bukkit.event.Event>> eventClasses) {
        if (eventClasses == null || eventClasses.isEmpty()) return null;
        Class<? extends org.bukkit.event.Event>[] evArr = eventClasses.toArray(new Class[0]);
        try {
            EventValueRegistry registry = ch.njol.skript.Skript.instance()
                    .registry(EventValueRegistry.class);
            if (registry != null) {
                Set<String> values = new TreeSet<>();
                for (EventValue<?, ?> ev : registry.elements()) {
                    Class<?> evClass = ev.eventClass();
                    Class<?> valClass = ev.valueClass();
                    boolean applicable = false;
                    for (Class<?> eventClass : evArr) {
                        if (evClass.isAssignableFrom(eventClass) || eventClass.isAssignableFrom(evClass)) {
                            applicable = true;
                            break;
                        }
                    }
                    if (!applicable) continue;

                    ClassInfo<?> ci = Classes.getSuperClassInfo(valClass);
                    String codeName = ci.getCodeName();

                    String prefix = switch (ev.time().toString()) {
                        case "PAST" -> "past event-";
                        case "FUTURE" -> "future event-";
                        default -> "event-";
                    };
                    values.add(prefix + codeName);
                }
                if (!values.isEmpty()) return values.toArray(new String[0]);
            }
        } catch (Exception ignored) {}

        try {
            Class<?> legacyClass = Class.forName("ch.njol.skript.registrations.EventValues");
            try {
                @SuppressWarnings("all")
                java.lang.reflect.Method getList = legacyClass.getMethod("getEventValuesList", int.class);
                String[] prefixes = {"past event-", "event-", "future event-"};
                Set<String> values = new TreeSet<>();

                for (int i = 0; i < 3; i++) {
                    Collection<?> list = (Collection<?>) getList.invoke(null, i - 1);
                    if (list == null) continue;
                    for (Object evi : list) {
                        Class<?> evtClass = getInternalField(evi, "eventClass", "eClass", "e");
                        Class<?> valClass = getInternalField(evi, "valueClass", "vClass", "c");
                        if (evtClass == null || valClass == null) continue;
                        for (Class<?> eventClass : evArr) {
                            if (evtClass.isAssignableFrom(eventClass) || eventClass.isAssignableFrom(evtClass)) {
                                ClassInfo<?> ci = Classes.getSuperClassInfo(valClass);
                                String codeName = ci.getCodeName();
                                values.add(prefixes[i] + codeName);
                                break;
                            }
                        }
                    }
                }
                if (!values.isEmpty()) return values.toArray(new String[0]);
            } catch (NoSuchMethodException ignored) {}
        } catch (Exception ignored) {}

        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> getInternalField(Object obj, String... names) {
        for (String name : names) {
            for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (!f.getName().equals(name)) continue;
                    try {
                        f.setAccessible(true);
                        Object val = f.get(obj);
                        if (val instanceof Class<?>) return (Class<T>) val;
                    } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }

    private static JsonArray strArray(Object values) {
        JsonArray arr = new JsonArray();
        if (values instanceof String[] arr2) {
            for (String v : arr2) arr.add(v);
        } else if (values instanceof Collection<?> col) {
            for (Object v : col) arr.add(v.toString());
        }
        return arr;
    }

    private static String cleanHtml(String s) {
        if (s == null) return null;
        return s.replaceAll("<[^>]+>([^<]*)</[^>]+>", "$1")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").trim();
    }

    private static String[] cleanHtml(String[] values) {
        if (values == null) return null;
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) out[i] = cleanHtml(values[i]);
        return out;
    }

    private static String[] extractValidEnumValues(ClassInfo<?> info) {
        try {
            Object supplierObj = info.getSupplier();
            if (supplierObj != null) {
                List<String> results = new ArrayList<>();
                try {
                    Method getMethod = findMethod(supplierObj.getClass(), "get");
                    if (getMethod != null) {
                        getMethod.setAccessible(true);
                        Object iterator = getMethod.invoke(supplierObj);

                        if (iterator instanceof Iterator<?> iter) {
                            while (iter.hasNext()) {
                                Object value = iter.next();
                                if (value == null) continue;

                                String stringValue = null;

                                try {
                                    Method getKeyMethod = value.getClass().getMethod("getKey");
                                    getKeyMethod.setAccessible(true);
                                    Object key = getKeyMethod.invoke(value);
                                    if (key != null) stringValue = key.toString();
                                } catch (Exception ignored) {}

                                if (stringValue == null || stringValue.isBlank()) {
                                    Object parser = info.getParser();
                                    if (parser != null) {
                                        try {
                                            for (Method method : parser.getClass().getMethods()) {
                                                if (method.getName().equals("toString") && method.getParameterCount() == 2) {
                                                    try {
                                                        Object result = method.invoke(parser, value, 0);
                                                        if (result instanceof String) {
                                                            stringValue = (String) result;
                                                            break;
                                                        }
                                                    } catch (Exception ignored1) {}
                                                }
                                            }
                                        } catch (Exception ignored) {}
                                    }
                                }

                                if (stringValue == null || stringValue.isBlank()) {
                                    if (value instanceof Enum<?>) {
                                        stringValue = ((Enum<?>) value).name();
                                    } else {
                                        try {
                                            Method getNameMethod = value.getClass().getMethod("getName");
                                            stringValue = (String) getNameMethod.invoke(value);
                                        } catch (Exception ignored) {
                                            stringValue = value.toString();
                                        }
                                    }
                                }

                                if (stringValue != null && !stringValue.isBlank()) {
                                    String cleaned = cleanTypeUsageValue(stringValue);
                                    if (!cleaned.isBlank()) results.add(cleaned);
                                }
                            }

                            if (!results.isEmpty()) return results.toArray(new String[0]);
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        try {
            Class<?> c = info.getC();
            if (c != null && c.isEnum()) {
                Object[] constants = c.getEnumConstants();
                List<String> values = new ArrayList<>();
                for (Object constant : constants) {
                    if (constant instanceof Enum<?>) {
                        values.add(((Enum<?>) constant).name().toLowerCase(Locale.ROOT));
                    }
                }
                if (!values.isEmpty()) return values.toArray(new String[0]);
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static String cleanTypeUsageValue(String value) {
        if (value == null || value.isBlank()) return value;

        String clean = value.trim();

        if (clean.startsWith("itemstack{") && clean.endsWith("}")) {
            clean = clean.substring(10, clean.length() - 1);
        }

        clean = clean.replaceAll("\\s+x\\s+\\d+$", "");

        if (clean.contains("minecraft:")) {
            int idx = clean.indexOf("minecraft:");
            return clean.substring(idx).replaceAll("[^a-zA-Z0-9:_/].*", "").trim().toLowerCase(Locale.ROOT);
        } else if (clean.contains("{") && clean.contains("}")) {
            int openBrace = clean.indexOf('{');
            int closeBrace = clean.lastIndexOf('}');
            if (openBrace >= 0 && closeBrace > openBrace) {
                clean = clean.substring(openBrace + 1, closeBrace);
            }
        }

        return clean
                .replaceAll("\\s+x\\s+\\d+$", "")
                .replaceAll("@[0-9a-f]+", "")
                .replaceAll("[{}=\\[\\]()@]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace("_", " ");
    }

    private static String typeSource(ClassInfo<?> info) {
        for (Object candidate : new Object[]{
                info.getParser(), info.getSerializer(), info.getChanger(),
                info.getCloner(), info.getSupplier()}) {
            if (candidate == null) continue;
            String pkg = candidate.getClass().getPackageName();
            if (pkg.startsWith("org.bukkit") || pkg.startsWith("io.papermc") || pkg.startsWith("net.minecraft")) continue;
            return candidate.getClass().getName();
        }
        return info.getC() != null ? info.getC().getName() : null;
    }

    private static String resolveVersion(SkriptAddon addon) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(addon.name());
        if (plugin != null) return plugin.getPluginMeta().getVersion();
        if (addon.name().equalsIgnoreCase("Skript")) {
            return ch.njol.skript.Skript.getVersion().toString();
        }
        return "unknown";
    }

    private static String nameOf(Class<?> type) {
        if (type == null) return "Unknown";

        Name ann = type.getAnnotation(Name.class);
        if (ann != null && !ann.value().isBlank()) return ann.value();

        String simpleName = type.getSimpleName();
        String category = extractCategory(simpleName);
        String name = simpleName.replaceFirst("^(Cond|Eff|Expr|Struct|Lit|Sec|On|Simple|Effect|Condition|Expression|Section|Structure)(?=[A-Z])", "");
        name = name.replaceAll("([a-z])([A-Z])", "$1 $2");

        if (!name.isEmpty() && Character.isLowerCase(name.charAt(0))) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }

        if (!category.isEmpty()) name = category + " - " + name;
        return name;
    }

    private static String nameFromPatterns(Collection<String> patterns, Class<?> type) {
        if (patterns != null && !patterns.isEmpty()) {
            String pattern = patterns.iterator().next();
            String extracted = extractNameFromPattern(pattern);
            if (extracted != null && !extracted.isBlank() && extracted.contains(" - ")) return extracted;
        }
        return nameOf(type);
    }

    private static String extractNameFromPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) return null;

        java.util.regex.Pattern typePattern = java.util.regex.Pattern.compile("%([a-zA-Z]+)%");
        java.util.regex.Matcher matcher = typePattern.matcher(pattern);

        String firstType = null;
        if (matcher.find()) firstType = matcher.group(1);

        String descriptive = cleanPattern(pattern);
        descriptive = descriptive.replaceAll("%[a-zA-Z]+%", "");
        descriptive = descriptive.replaceAll("[\\[\\]()<>|]", " ");
        descriptive = descriptive.replaceAll("\\s+", " ");
        descriptive = descriptive.replaceAll("[.+^$*?]", " ");
        descriptive = descriptive.replaceAll("\\s+", " ");
        descriptive = descriptive.trim();

        if (descriptive.isBlank()) return null;

        String[] words = descriptive.split("\\s+");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (!word.isBlank() && !isCommonModifier(word) && word.length() > 1) {
                if (!formatted.isEmpty()) formatted.append(" ");
                formatted.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase());
            }
        }

        if (firstType != null) {
            String typeLabel = capitalizeFirst(firstType);
            return typeLabel + " - " + formatted;
        } else if (!formatted.isEmpty()) {
            return formatted.toString();
        }

        return null;
    }

    private static boolean isCommonModifier(String word) {
        String lower = word.toLowerCase();
        return lower.equals("is") || lower.equals("are") || lower.equals("can") ||
                lower.equals("a") || lower.equals("an") || lower.equals("the") ||
                lower.equals("local") || lower.equals("and") || lower.equals("or") ||
                lower.equals("not") || lower.equals("dont") || lower.equals("can't") ||
                lower.equals("of") || lower.equals("from") || lower.equals("using") ||
                lower.equals("to") || lower.equals("in") || lower.equals("with") ||
                lower.equals("for") || lower.equals("by") || lower.equals("on") ||
                lower.equals("at") || lower.equals("as") || lower.equals("be");
    }

    private static String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1).toLowerCase();
    }

    private static String extractCategory(String className) {
        if (className.startsWith("Cond")) return "Condition";
        if (className.startsWith("Eff")) return "Effect";
        if (className.startsWith("Expr")) return "Expression";
        if (className.startsWith("Struct")) return "Structure";
        if (className.startsWith("Lit")) return "Literal";
        if (className.startsWith("Sec")) return "Section";
        if (className.startsWith("On") || className.startsWith("Simple")) return "Event";
        return "";
    }

    private static String[] getAnnotationStrings(Class<?> type, Class<? extends java.lang.annotation.Annotation> annClass) {
        if (type == null) return new String[0];
        java.lang.annotation.Annotation ann = type.getAnnotation(annClass);
        if (ann == null) return new String[0];
        try {
            Method m = findMethod(ann.getClass(), "value");
            if (m == null) return new String[0];
            m.setAccessible(true);
            Object val = m.invoke(ann);
            if (val instanceof String[] arr) return arr;
            if (val instanceof Collection<?> col) return col.stream().map(Object::toString).toArray(String[]::new);
        } catch (Exception ignored) {}
        return new String[0];
    }

    private static String[] functionDescriptionOf(Function<?> function, Class<?> type) {
        String[] result = null;
        try {
            Object desc = invokeMethod(function, "description");
            if (desc instanceof java.util.List<?> list) {
                result = list.stream().map(Object::toString).toArray(String[]::new);
            } else if (desc instanceof String[]) {
                result = (String[]) desc;
            }
        } catch (Exception ignored) {}

        if (result != null && result.length > 0) return result;
        return descriptionOf(type);
    }

    private static String[] functionExamplesOf(Function<?> function, Class<?> type) {
        String[] result = null;
        try {
            Object examples = invokeMethod(function, "examples");
            if (examples instanceof java.util.List<?> list) {
                result = list.stream().map(Object::toString).toArray(String[]::new);
            } else if (examples instanceof String[]) {
                result = (String[]) examples;
            }
        } catch (Exception ignored) {}

        if (result != null && result.length > 0) return result;
        return examplesOf(type);
    }

    private static String[] descriptionOf(Class<?> type) {
        return getAnnotationStrings(type, Description.class);
    }

    private static String[] examplesOf(Class<?> type) {
        if (type == null) return new String[0];
        Examples legacy = type.getAnnotation(Examples.class);
        if (legacy != null) return legacy.value();
        Example.Examples container = type.getAnnotation(Example.Examples.class);
        if (container != null) {
            return Arrays.stream(container.value()).map(Example::value).toArray(String[]::new);
        }
        Example single = type.getAnnotation(Example.class);
        return single != null ? new String[]{single.value()} : new String[0];
    }

    private static String[] sinceOf(Class<?> type) {
        return getAnnotationStrings(type, Since.class);
    }

    private static String[] requiredPluginsOf(Class<?> type) {
        return getAnnotationStrings(type, RequiredPlugins.class);
    }

    private static String[] keywordsOf(Class<?> type) {
        return getAnnotationStrings(type, Keywords.class);
    }

    private static boolean isNoDoc(Class<?> type) {
        return type != null && type.isAnnotationPresent(NoDoc.class);
    }

    private static boolean isOwned(Class<?> type, ClassLoader loader) {
        return type != null && type.getClassLoader() == loader;
    }

    private static boolean isOwnedType(ClassInfo<?> info, ClassLoader loader) {
        Object[] handlers = {
                info.getParser(), info.getSerializer(), info.getChanger(),
                info.getCloner(), info.getSupplier()
        };
        for (Object h : handlers) {
            if (h == null) continue;
            ClassLoader hLoader = h.getClass().getClassLoader();
            if (hLoader != null && hLoader != loader && isPluginClassLoader(hLoader)) return false;
        }
        if (isOwned(info.getC(), loader)) return true;
        return hasLoader(info.getParser(), loader)
                || hasLoader(info.getDefaultExpression(), loader)
                || hasLoader(info.getChanger(), loader)
                || hasLoader(info.getSerializer(), loader)
                || hasLoader(info.getCloner(), loader)
                || hasLoader(info.getSupplier(), loader);
    }

    private static boolean isPluginClassLoader(ClassLoader loader) {
        return loader.getClass().getSimpleName().contains("PluginClassLoader");
    }

    private static boolean isOwnedFunction(Function<?> function, SkriptAddon addon, ClassLoader loader) {
        if (function instanceof DefaultFunction<?> df) return isSameAddon(df.source(), addon);
        return isOwned(function.getClass(), loader);
    }

    private static boolean isSameAddon(SkriptAddon a, SkriptAddon b) {
        if (a == b) return true;
        if (a.name().equalsIgnoreCase(b.name())) return true;
        return a.source() != null && a.source().equals(b.source());
    }

    private static boolean hasLoader(Object obj, ClassLoader loader) {
        return obj != null && obj.getClass().getClassLoader() == loader;
    }

    private static void resolveCollisions(JsonArray arr, Set<String> globalIds, String addonName) {
        List<String> collidingIds = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonElement idEl = el.getAsJsonObject().get("id");
            if (idEl == null || idEl.isJsonNull()) continue;
            String id = idEl.getAsString();
            if (!globalIds.add(id)) {
                if (!collidingIds.contains(id)) collidingIds.add(id);
            }
        }
        for (String id : collidingIds) attemptMerge(arr, id, addonName);
    }

    private static void attemptMerge(JsonArray arr, String id, String addonName) {
        List<JsonObject> collisions = new ArrayList<>();
        for (int i = arr.size() - 1; i >= 0; i--) {
            JsonElement el = arr.get(i);
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            JsonElement idEl = obj.get("id");
            if (idEl != null && !idEl.isJsonNull() && id.equals(idEl.getAsString())) {
                collisions.add(obj);
                arr.remove(i);
            }
        }

        if (collisions.size() < 2) {
            for (JsonObject obj : collisions) arr.add(obj);
            return;
        }

        JsonObject first = collisions.getFirst();
        int merged = 0;
        for (int i = 1; i < collisions.size(); i++) {
            JsonObject other = collisions.get(i);
            if (canMerge(first, other)) {
                JsonArray firstPatterns = first.getAsJsonArray("patterns");
                JsonArray otherPatterns = other.getAsJsonArray("patterns");
                if (firstPatterns == null) { firstPatterns = new JsonArray(); first.add("patterns", firstPatterns); }
                if (otherPatterns != null) for (JsonElement p : otherPatterns) firstPatterns.add(p);
                merged++;
            } else {
                arr.add(other);
            }
        }
        arr.add(first);

        if (merged == 0) {
            Logger.getLogger("skdocstool").warning(
                    "[" + addonName + "] Unable to merge " + collisions.size() + " instances of id: " + id);
        } else {
            Logger.getLogger("skdocstool").info(
                    "[" + addonName + "] Merged " + (merged + 1) + "/" + collisions.size() + " instances of id: " + id);
        }
    }

    private static boolean canMerge(JsonObject a, JsonObject b) {
        JsonElement nameA = a.get("name"), nameB = b.get("name");
        if (nameA == null || nameB == null) return false;
        if (!nameA.equals(nameB)) return false;
        JsonElement descA = a.get("description"), descB = b.get("description");
        if (descA == null && descB == null) return true;
        if (descA == null || descB == null) return false;
        return descA.equals(descB);
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase(Locale.ROOT);
    }

    private static String cleanPattern(String pattern) {
        if (pattern == null) return null;
        return pattern
                .replaceAll("\\d+¦", "")
                .replaceAll("\\d+:", "")
                .replaceAll("\\w+:", "")
                .replaceAll(":(?=[A-Z(])", "")
                .replaceAll("\\[:", "[")
                .replaceAll(":([a-z])", "$1")
                .replaceAll("\\|:", "|")
                .replaceAll("\\(\\(([^)]*)\\)\\)", "($1)")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Collection<String> cleanPatterns(Collection<String> patterns) {
        if (patterns == null) return null;
        return patterns.stream().map(GenerateDocs::cleanPattern).toList();
    }

    private static void assignDocIds(String addonName, JsonArray... arrays) {
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (JsonArray arr : arrays) {
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                JsonElement idEl = obj.get("id");
                if (idEl == null || idEl.isJsonNull()) continue;
                String base = addonName + "-" + idEl.getAsString();
                int count = seen.merge(base, 1, Integer::sum);
                obj.addProperty("doc-id", count == 1 ? base : base + "-" + count);
            }
        }
    }

    private static String[] combineExamples(String[] examples) {
        return examples;
    }
}