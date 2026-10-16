package org.skdocs.skdocstool.docs.element;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.doc.Documentable;
import ch.njol.skript.doc.DocumentationIdProvider;
import ch.njol.skript.lang.function.Function;
import ch.njol.skript.lang.function.Functions;
import ch.njol.skript.lang.function.Signature;
import ch.njol.skript.registrations.Classes;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.StringJoiner;
import org.skdocs.skdocstool.docs.addon.AddonContext;
import org.skdocs.skdocstool.docs.addon.SyntaxOwnership;
import org.skdocs.skdocstool.docs.registration.RegistrationMetadata;
import org.skdocs.skdocstool.docs.builder.DocumentationFormat;
import org.skdocs.skdocstool.docs.builder.DocumentationText;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.common.function.Parameter;

import static org.skdocs.skdocstool.docs.addon.AddonResolver.isOwnedFunction;
import static org.skdocs.skdocstool.docs.builder.DocumentationJson.addStrings;
import static org.skdocs.skdocstool.docs.builder.DocumentationJson.strings;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.descriptionOf;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.examplesOf;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.isNoDoc;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.prefer;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.sinceOf;

public final class FunctionDocumentation implements DocumentationElement {
    @Override
    public String key() { return "functions"; }

    @Override
    public JsonArray collect(AddonContext addon, SyntaxOwnership ownership, DocumentationFormat format) {
        return functionsArray(addon, format == DocumentationFormat.SKRIPT_HUB);
    }

    private static JsonArray functionsArray(AddonContext context, boolean skriptHub) {
        SkriptAddon addon = context.addon();
        ClassLoader loader = context.loader();
        JsonArray arr = new JsonArray();
        for (Function<?> function : Functions.getFunctions()) {
            if (!isOwnedFunction(function, addon, loader)) continue;
            RegistrationMetadata rd = context.registrationDocs().get(function);
            if ((rd != null && rd.noDoc()) || isNoDoc(function.getClass())) continue;
            String id = rd != null && rd.id() != null ? rd.id() : DocumentationIdProvider.getId(function);
            if (id == null || id.isBlank()) continue;

            Signature<?> sig = function.getSignature();
            Class<?> implType = function.getClass();

            String[] description = prefer(rd != null ? rd.description() : null, functionDescriptionOf(function, implType));
            String[] examples = prefer(rd != null ? rd.examples() : null, functionExamplesOf(function, implType));
            String[] since = prefer(rd != null ? rd.since() : null,
                function instanceof Documentable doc ? doc.since().toArray(String[]::new) : sinceOf(implType));
            String[] keywords = prefer(rd != null ? rd.keywords() : null,
                function instanceof Documentable doc ? doc.keywords().toArray(String[]::new) : DocumentationText.keywordsOf(implType));
            String[] requires = function instanceof Documentable doc
                ? doc.requires().toArray(String[]::new) : DocumentationText.requiredPluginsOf(implType);

            JsonObject obj = new JsonObject();
            obj.addProperty("id", id);
            obj.addProperty("name", rd != null && rd.name() != null ? rd.name() : function.getName());

            if (skriptHub) {
                addStrings(obj, "description", description);
                addStrings(obj, "examples", examples);
                addStrings(obj, "since", since);
                addStrings(obj, "keywords", keywords);
                addStrings(obj, "required plugins", requires);
                JsonArray pArr = new JsonArray();
                pArr.add(buildFunctionPattern(function.getName(), sig));
                obj.add("patterns", pArr);
                String returnType = returnTypeName(function.getReturnType());
                if (returnType != null) obj.addProperty("return type", returnType);
            } else {
                obj.add("description", strings(description));
                obj.add("examples", strings(examples));
                obj.add("since", strings(since));
                obj.add("keywords", strings(keywords));
                obj.add("requiredPlugins", strings(requires));
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

    private static String[] functionDescriptionOf(Function<?> function, Class<?> type) {
        if (function instanceof Documentable modern && !modern.description().isEmpty())
            return modern.description().toArray(String[]::new);
        return descriptionOf(type);
    }

    private static String[] functionExamplesOf(Function<?> function, Class<?> type) {
        if (function instanceof Documentable modern && !modern.examples().isEmpty())
            return modern.examples().toArray(String[]::new);
        return examplesOf(type);
    }
}
