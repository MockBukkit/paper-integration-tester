package org.mockbukkit.integrationtester.codegen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;
import com.palantir.javapoet.*;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class CodeGenerator {

    private final String targetPackageName;
    private final Map<Class<?>, ClassName> classNames = new HashMap<>();
    private final Set<Class<?>> alreadyScanned = new HashSet<>();
    private final static Pattern PACKAGE_NAME = Pattern.compile("^(.+)\\.[A-Z]");
    private static final ClassName MIRROR_HANDLER = ClassName.get("org.mockbukkit.integrationtester.testclient", "MirrorHandler");

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.print("Usage: <target folder>");
            return;
        }
        File outputFolder = new File(args[0]);
        File classFolder = new File(outputFolder, "java");
        File resourceFolder = new File(outputFolder, "resources");
        CodeGenerator codeGenerator = new CodeGenerator("org.mockbukkit.integrationtester");
        List<ClassInfo> outerClasses = new ArrayList<>();
        for (String operationIncludedPackage : operationIncludedPackages()) {
            outerClasses.addAll(codeGenerator.findClassesInPackage(operationIncludedPackage));
        }
        outerClasses.forEach(classInfo -> {
            Pair<TypeSpec, TypeSpec> typeSpec = codeGenerator.createTypeSpec(classInfo);
            JavaFile javaFile = JavaFile.builder(codeGenerator.classNames.get(classInfo.loadClass()).packageName(), typeSpec.t1()).build();
            try {
                if (!classFolder.exists() && !classFolder.mkdirs()) {
                    throw new IOException("Could not generate directory, possible permission issue");
                }
                javaFile.writeTo(classFolder);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (typeSpec.t2() != null) {
                JavaFile javaFile2 = JavaFile.builder(codeGenerator.classNames.get(classInfo.loadClass()).packageName(), typeSpec.t2()).build();
                try {
                    if (!classFolder.exists() && !classFolder.mkdirs()) {
                        throw new IOException("Could not generate directory, possible permission issue");
                    }
                    javaFile2.writeTo(classFolder);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        JsonObject mirrorClassRemapping = new JsonObject();
        for (Map.Entry<Class<?>, ClassName> classEntry : codeGenerator.classNames.entrySet()) {
            mirrorClassRemapping.add(classEntry.getKey().getName(), new JsonPrimitive(classEntry.getValue().canonicalName()));
        }
        if(!resourceFolder.exists() && !resourceFolder.mkdirs()) {
            throw new IOException("Could not generate directory, possible permission issue");
        }
        File classRemappingFile = new File(resourceFolder, "classRemapping.json");
        if(!classRemappingFile.exists() && !classRemappingFile.createNewFile()) {
            throw new IOException("Could not create file, possible permission issue");
        }
        try (PrintWriter writer = new PrintWriter(classRemappingFile, StandardCharsets.UTF_8)) {
            JsonWriter jsonWriter = new JsonWriter(writer);
            jsonWriter.setIndent("  ");
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(mirrorClassRemapping, jsonWriter);
            writer.print("\n");
        }
    }

    public CodeGenerator(String targetPackageName) {
        this.targetPackageName = targetPackageName;
    }

    private List<ClassInfo> findClassesInPackage(String packageName) {
        try (ScanResult scanResult = new ClassGraph().enableAllInfo().acceptPackages(packageName).scan()) {
            ClassInfoList classInfoList = scanResult.getAllClasses();
            List<ClassInfo> classInfos = classInfoList.stream().filter(classInfo -> (classInfo.isPublic() || classInfo.isPackageVisible()) && !classInfo.isInnerClass()).toList();
            classNames.putAll(determineClassNames(classInfos, ""));
            return classInfos;
        }
    }

    private Map<Class<?>, ClassName> determineClassNames(List<ClassInfo> classInfos, String packageSuffix) {
        Map<Class<?>, ClassName> output = new HashMap<>();
        for (ClassInfo classInfo : classInfos) {
            if (alreadyScanned.contains(classInfo.loadClass())) {
                continue;
            }
            alreadyScanned.add(classInfo.loadClass());
            Map<Class<?>, ClassName> classNameMap = determineClassNames(classInfo.getInnerClasses(), packageSuffix + "." + modifyClassName(classInfo));
            output.putAll(classNameMap);
            String modifiedPackage = getModifiedPackage(classInfo.loadClass());
            if (modifiedPackage != null) {
                output.put(classInfo.loadClass(), ClassName.get(modifiedPackage + packageSuffix, modifyClassName(classInfo)));
            }
        }
        return output;
    }

    private String modifyClassName(ClassInfo classInfo) {
        return classInfo.getSimpleName().endsWith("Impl") ? classInfo.getSimpleName() + "0" : classInfo.getSimpleName();
    }

    private Pair<TypeSpec, @Nullable TypeSpec> createTypeSpec(ClassInfo classInfo) {
        Class<?> classToReplicate = classInfo.loadClass();
        List<TypeSpec> children = classInfo.getInnerClasses().stream()
                .filter(classInfo1 -> (classInfo1.isPublic() || classInfo1.isPackageVisible()) && !classInfo1.isAnonymousInnerClass())
                .filter(classInfo2 -> alreadyScanned.remove(classInfo2.loadClass()))
                .map(this::createTypeSpec)
                .flatMap(pair -> Stream.of(pair.t1(), pair.t2()))
                .filter(Objects::nonNull).toList();
        TypeSpec apiElement = generateApiElement(classToReplicate, children);
        TypeSpec implementation = null;
        if (classInfo.isInterface()) {
            implementation = generateImplementation(classToReplicate);
        }
        return new Pair<>(apiElement, implementation);
    }

    private TypeSpec generateImplementation(Class<?> classToReplicate) {
        ClassName className = classNames.get(classToReplicate);
        String simplifiedName = className.simpleName() + "Impl";
        TypeSpec.Builder mirror = TypeSpec.classBuilder(simplifiedName);
        Map<Class<?>, Map<String, String>> redefinitions = Util.compileTypeVariableConversions(classToReplicate, classNames, new HashMap<>());
        if (classToReplicate.getTypeParameters().length > 0) {
            mirror.addSuperinterface(ParameterizedTypeName.get(classNames.get(classToReplicate), Util.getTypeNames(classToReplicate.getTypeParameters(), new HashMap<>(), classNames, true)));
            for (TypeVariable<?> typeVariable : classToReplicate.getTypeParameters()) {
                mirror.addTypeVariable(Util.getTypeVariableName(typeVariable, classNames, redefinitions.getOrDefault(classToReplicate, new HashMap<>())));
            }
        } else {
            mirror.addSuperinterface(classNames.get(classToReplicate));
        }
        List<MethodSpec> methodSpecs = generateMethods(classToReplicate, true, simplifiedName, redefinitions);
        mirror.addMethods(methodSpecs);
        List<FieldSpec> fieldSpecList = generateFields(mirror, classToReplicate, redefinitions);
        mirror.addFields(fieldSpecList);
        if (java.lang.reflect.Modifier.isStatic(classToReplicate.getModifiers())) {
            mirror.addModifiers(Modifier.STATIC);
        }
        if (java.lang.reflect.Modifier.isPublic(classToReplicate.getModifiers())) {
            mirror.addModifiers(Modifier.PUBLIC);
        }
        return mirror.build();
    }

    private TypeSpec generateApiElement(Class<?> classToReplicate, Collection<TypeSpec> children) {
        boolean canHaveSuperClass = false;
        TypeSpec.Builder typeSpecBuilder;
        ClassName className = classNames.get(classToReplicate);
        if (className == null) {
            throw new NullPointerException(classToReplicate.getName());
        }
        Map<Class<?>, Map<String, String>> redefinitions = Util.compileTypeVariableConversions(classToReplicate, classNames, new HashMap<>());
        if (classToReplicate.isAnnotation()) {
            typeSpecBuilder = TypeSpec.annotationBuilder(className).addMethods(generateMethods(classToReplicate, false, className.simpleName(), redefinitions));
        } else if (classToReplicate.isInterface()) {
            typeSpecBuilder = TypeSpec.interfaceBuilder(className).addMethods(generateMethods(classToReplicate, false, className.simpleName(), redefinitions));
        } else if (classToReplicate.isRecord()) {
            typeSpecBuilder = TypeSpec.recordBuilder(className).addMethods(generateMethods(classToReplicate, false, className.simpleName(), redefinitions));
        } else if (classToReplicate.isEnum()) {
            typeSpecBuilder = TypeSpec.enumBuilder(className).addMethods(generateMethods(classToReplicate, false, className.simpleName(), redefinitions));
        } else {
            typeSpecBuilder = TypeSpec.classBuilder(className).addMethods(generateMethods(classToReplicate, false, className.simpleName(), redefinitions));
            if (java.lang.reflect.Modifier.isAbstract(classToReplicate.getModifiers())) {
                typeSpecBuilder.addModifiers(Modifier.ABSTRACT);
            }
            canHaveSuperClass = true;
        }
        List<FieldSpec> fieldSpecList = generateFields(typeSpecBuilder, classToReplicate, redefinitions);
        typeSpecBuilder.addFields(fieldSpecList);
        if (!classToReplicate.isAnnotation()) {
            typeSpecBuilder.addSuperinterfaces(Arrays.stream(classToReplicate.getGenericInterfaces()).map(typeName -> Util.getTypeName(typeName, redefinitions.getOrDefault(classToReplicate, new HashMap<>()), classNames)).toList());
        }
        if (canHaveSuperClass && classToReplicate.getSuperclass() != Object.class) {
            typeSpecBuilder.superclass(Util.getTypeName(classToReplicate.getGenericSuperclass(), new HashMap<>(), classNames));
        }
        for (@NotNull TypeVariable<? extends Class<?>> typeParameter : classToReplicate.getTypeParameters()) {
            typeSpecBuilder.addTypeVariable(Util.getTypeVariableName(typeParameter, classNames, new HashMap<>()));
        }
        for (TypeSpec typeSpec : children) {
            typeSpecBuilder.addType(typeSpec);
        }
        if (java.lang.reflect.Modifier.isStatic(classToReplicate.getModifiers())) {
            typeSpecBuilder.addModifiers(Modifier.STATIC);
        }
        typeSpecBuilder.addModifiers(Modifier.PUBLIC);
        if (java.lang.reflect.Modifier.isAbstract(classToReplicate.getModifiers()) && !classToReplicate.isInterface() && !classToReplicate.isEnum() && !classToReplicate.isRecord()) {
            typeSpecBuilder.addModifiers(Modifier.ABSTRACT);
        }
        if (!classToReplicate.isRecord() && !classToReplicate.isInterface()) {
            typeSpecBuilder.addMethods(generateConstructors(classToReplicate, redefinitions.getOrDefault(classToReplicate, new HashMap<>())));
        }
        return typeSpecBuilder.build();
    }

    private List<MethodSpec> generateConstructors(Class<?> classToReplicate, Map<String, String> redefinitions) {
        List<MethodSpec> output = new ArrayList<>();
        boolean hasGeneratedEmptyConstructor = classToReplicate.isEnum();
        for (Constructor<?> constructor : classToReplicate.getDeclaredConstructors()) {
            if (constructor.isSynthetic()) {
                continue;
            }
            if (constructor.getParameterCount() == 0) {
                hasGeneratedEmptyConstructor = true;
            }
            output.add(generateConstructor(classToReplicate, constructor, redefinitions));
            if (classToReplicate.isEnum()) {
                return output;
            }
        }
        if (!hasGeneratedEmptyConstructor) {
            ClassName mirrorHandler = ClassName.get("org.mockbukkit.integrationtester.testclient", "MirrorHandler");
            CodeBlock.Builder codeBlockBuilder = CodeBlock.builder();
            addSuperConstructorCall(classToReplicate, codeBlockBuilder, null);
            fillFields(classToReplicate, codeBlockBuilder);
            codeBlockBuilder.addStatement("$T.trackNew(this, $S)", mirrorHandler, classToReplicate);
            MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PROTECTED)
                    .addCode(codeBlockBuilder.build());
            if (classToReplicate.getSuperclass() != Object.class) {
                constructorBuilder.addExceptions(Arrays.stream(getSuperConstructor(classToReplicate).getGenericExceptionTypes())
                        .map(exception -> Util.getTypeName(exception, redefinitions, classNames))
                        .toList());
            }
            output.add(constructorBuilder.build());
        }
        return output;
    }

    private MethodSpec generateConstructor(Class<?> classToReplicate, Constructor<?> constructor, Map<String, String> redefinitions) {
        CodeBlock.Builder codeBlockBuilder = CodeBlock.builder();
        addSuperConstructorCall(classToReplicate, codeBlockBuilder, redefinitions);
        codeBlockBuilder.addStatement("$T.trackNew(this, $S$L)", MIRROR_HANDLER, classToReplicate, generateParameterString(constructor));
        fillFields(classToReplicate, codeBlockBuilder);

        MethodSpec.Builder builder = MethodSpec.constructorBuilder().addCode(codeBlockBuilder.build())
                .addModifiers(classToReplicate.isEnum() ? Modifier.PRIVATE : Modifier.PUBLIC);
        if (classToReplicate.isEnum()) {
            return builder.build();
        }
        builder.addParameters(Arrays.stream(ParameterData.from(constructor, classNames, redefinitions))
                .map(ParameterData::toParameterSpec)
                .toList());
        builder.addExceptions(Arrays.stream(constructor.getGenericExceptionTypes())
                .map(exception -> Util.getTypeName(exception, redefinitions, classNames))
                .toList());
        return builder.build();
    }

    private void fillFields(Class<?> classToReplicate, CodeBlock.Builder codeBlockBuilder) {
        for (Field field : classToReplicate.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || !java.lang.reflect.Modifier.isPublic(field.getModifiers())) {
                continue;
            }
            codeBlockBuilder.addStatement("this.$L = $T.handleField($S, $S, this)", field.getName(), MIRROR_HANDLER, field.getName(), classToReplicate);
        }
    }

    private String generateParameterString(Constructor<?> constructor) {
        if (constructor.getParameters().length == 0 || constructor.getDeclaringClass().isEnum()) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (Parameter parameter : constructor.getParameters()) {
            stringBuilder.append(", ");
            stringBuilder.append(parameter.getName());
        }
        return stringBuilder.toString();
    }

    private void addSuperConstructorCall(Class<?> clazz, CodeBlock.Builder codeBlock, Map<String, String> redefinitions) {
        if (clazz.getSuperclass() == null || clazz.getSuperclass() == Object.class || clazz.getSuperclass() == Enum.class) {
            return;
        }
        if (classNames.containsKey(clazz.getSuperclass())) {
            codeBlock.addStatement("super()");
            return;
        }
        Constructor<?> chosenConstructor = getSuperConstructor(clazz);
        if (chosenConstructor != null) {
            generateSuperParametersString(chosenConstructor, redefinitions, codeBlock);
        }
    }

    private static @Nullable Constructor<?> getSuperConstructor(Class<?> clazz) {
        Constructor<?>[] constructors = clazz.getSuperclass().getDeclaredConstructors();
        Constructor<?> chosenConstructor = null;
        int parameterCount = Integer.MAX_VALUE;
        for (Constructor<?> constructor : constructors) {
            if (java.lang.reflect.Modifier.isPrivate(constructor.getModifiers())) {
                continue;
            }
            if (constructor.getParameterCount() < parameterCount) {
                chosenConstructor = constructor;
                parameterCount = constructor.getParameterCount();
            }
        }
        return chosenConstructor;
    }

    private void generateSuperParametersString(Constructor<?> superConstructor, Map<String, String> redefinitions, CodeBlock.Builder codeBlock) {
        if (superConstructor.getParameterCount() == 0) {
            codeBlock.addStatement("super()");
            return;
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (Parameter ignored : superConstructor.getParameters()) {
            stringBuilder.append("$T.mock($T.class), ");
        }
        stringBuilder.delete(stringBuilder.length() - 2, stringBuilder.length());
        ClassName mockito = ClassName.get("org.mockito", "Mockito");
        codeBlock.addStatement("super(" + stringBuilder + ")", Arrays.stream(superConstructor.getParameters())
                .flatMap(parameter -> Stream.of(mockito, Util.getTypeName(parameter.getType(), redefinitions, classNames))).toArray());
    }

    private List<MethodSpec> generateMethods(Class<?> classToReplicate, boolean isImplementation, String className, Map<Class<?>, Map<String, String>> redefinitions) {
        Method[] methods = !isImplementation ? classToReplicate.getDeclaredMethods() : Stream.concat(Arrays.stream(findNecessaryMethods(classToReplicate)), Arrays.stream(classToReplicate.getMethods())).toArray(Method[]::new);
        List<Pair<MethodData, Method>> methodData = new ArrayList<>();
        for (Method method : methods) {
            if (isMethodBanned(method, classToReplicate)) {
                continue;
            }
            if (java.lang.reflect.Modifier.isPrivate(method.getModifiers())) {
                continue;
            }
            if (isImplementation && method.isDefault()) {
                continue;
            }
            if (method.isBridge()) {
                continue;
            }
            insertMethodData(method, MethodData.from(method, classNames, isImplementation, classToReplicate.isEnum(), redefinitions.getOrDefault(method.getDeclaringClass(), new HashMap<>())), methodData);
        }
        return methodData.stream().map(Pair::t1).map(methodData1 -> methodData1.toMethodSpec(classToReplicate)).toList();
    }

    private Method[] findNecessaryMethods(Class<?> clazz) {
        Method[] declaredMethods = clazz.getDeclaredMethods();
        if (clazz.getSuperclass() == Object.class || clazz.getSuperclass() == null) {
            return declaredMethods;
        }
        return Stream.concat(Arrays.stream(declaredMethods), Arrays.stream(findNecessaryMethods(clazz.getSuperclass()))).toArray(Method[]::new);
    }

    private void insertMethodData(Method method, MethodData methodData1, List<Pair<MethodData, Method>> methodData) {
        for (Pair<MethodData, Method> methodData2 : methodData) {
            if (method.equals(methodData2.t2())) {
                return;
            }
            if (Arrays.equals(method.getParameterTypes(), methodData2.t2().getParameterTypes()) && method.getName().equals(methodData2.t2().getName())) {
                return;
            }
        }
        methodData.add(new Pair<>(methodData1, method));
    }

    private List<FieldSpec> generateFields(TypeSpec.Builder typeSpec, Class<?> clazz, Map<Class<?>, Map<String, String>> redefinitions) {
        List<FieldSpec> fields = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            FieldSpec.Builder fieldSpec = FieldSpec.builder(Util.getTypeName(field.getGenericType(), redefinitions.getOrDefault(clazz, new HashMap<>()), classNames), field.getName());
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                fieldSpec.addModifiers(Modifier.STATIC);
            }
            if (!java.lang.reflect.Modifier.isPublic(field.getModifiers())) {
                continue;
            }
            if (java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                fieldSpec.addModifiers(Modifier.FINAL);
            }
            if (field.isEnumConstant()) {
                typeSpec.addEnumConstant(field.getName());
                continue;
            }
            if (field.getType().isPrimitive() && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                try {
                    Object value = field.get(null);
                    fieldSpec.initializer(CodeBlock.builder().addStatement("$L", value).build());
                    continue;
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            fieldSpec.addModifiers(Modifier.PUBLIC);
            fieldSpec.addAnnotations(Util.getAnnotationTypeNames(field, classNames).stream().map(ClassName::bestGuess).map(AnnotationSpec::builder).map(AnnotationSpec.Builder::build).toList());
            if (java.lang.reflect.Modifier.isFinal(field.getModifiers()) && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                ClassName mirrorHandler = ClassName.get("org.mockbukkit.integrationtester.testclient", "MirrorHandler");
                fieldSpec.initializer(CodeBlock.builder()
                        .addStatement("$T.handleStaticField($S, $S)", mirrorHandler, field.getName(), clazz)
                        .build());
            }
            fields.add(fieldSpec.build());
        }
        return fields;
    }

    private boolean isMethodBanned(Method method, Class<?> classToReplicate) {
        if (classToReplicate.isInterface()) {
            // Handled anyhow
            return false;
        }
        Map<String, List<List<Class<?>>>> banned = new HashMap();
        banned.put("getClass", List.of(List.of()));
        banned.put("notify", List.of(List.of()));
        banned.put("notifyAll", List.of(List.of()));
        banned.put("wait", List.of(List.of(), List.of(long.class), List.of(long.class, int.class)));
        if (classToReplicate.isEnum()) {
            banned.put("valueOf", List.of(List.of(Class.class, String.class), List.of(String.class)));
            banned.put("compareTo", List.of(List.of(Object.class), List.of(Enum.class)));
            banned.put("hashCode", List.of(List.of()));
            banned.put("name", List.of(List.of()));
            banned.put("equals", List.of(List.of(Object.class)));
            banned.put("describeConstable", List.of(List.of()));
            banned.put("getDeclaringClass", List.of(List.of()));
            banned.put("ordinal", List.of(List.of()));
            banned.put("values", List.of(List.of()));
        } else if (classToReplicate.isRecord()) {
            banned.put("equals", List.of(List.of(Object.class)));
        }
        List<List<Class<?>>> bannedParameters = banned.get(method.getName());
        if (bannedParameters == null) {
            return false;
        }
        for (List<Class<?>> parameters : bannedParameters) {
            if (parameters.size() == method.getParameterCount() && parametersMatches(parameters, method)) {
                return true;
            }
        }
        return false;
    }

    private boolean parametersMatches(List<Class<?>> matcher, Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        for (int i = 0; i < matcher.size(); i++) {
            if (parameters[i] != matcher.get(i)) {
                return false;
            }
        }
        return true;
    }

    private TypeName sanitizeClass(Class<?> inputClass) {
        if (classNames.containsKey(inputClass)) {
            return classNames.get(inputClass);
        }
        return TypeName.get(inputClass);
    }

    private @Nullable String getModifiedPackage(Class<?> inputClass) {
        String packageName = inputClass.getPackageName();
        for (String operationIncluded : operationIncludedPackages()) {
            if (packageName.contains(operationIncluded)) {
                String toRemove = operationIncluded.substring(0, operationIncluded.lastIndexOf('.'));
                return packageName.replaceFirst(toRemove, targetPackageName);
            }
        }
        return null;
    }

    private static List<String> operationIncludedPackages() {
        return List.of("co.aikar", "com.destroystokyo.paper", "io.papermc.paper", "org.bukkit", "org.spigotmc", "net.kyori");
    }
}

