package org.mockbukkit.integrationtester.codegen;

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
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class CodeGenerator {

    private final String targetPackageName;
    private final Map<Class<?>, ClassName> classNames = new HashMap<>();
    private final static Pattern PACKAGE_NAME = Pattern.compile("^(.+)\\.[A-Z]");

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.print("Usage: <target folder>");
            return;
        }
        File outputFolder = new File(args[0]);
        CodeGenerator codeGenerator = new CodeGenerator("org.mockbukkit.integrationtester");
        List<ClassInfo> outerClasses = new ArrayList<>();
        for (String operationIncludedPackage : operationIncludedPackages()) {
            outerClasses.addAll(codeGenerator.findClassesInPackage(operationIncludedPackage));
        }
        outerClasses.forEach(classInfo -> {
            Pair<TypeSpec, TypeSpec> typeSpec = codeGenerator.createTypeSpec(classInfo);
            JavaFile javaFile = JavaFile.builder(codeGenerator.classNames.get(classInfo.loadClass()).packageName(), typeSpec.t1()).build();
            try {
                if (!outputFolder.exists() && !outputFolder.mkdirs()) {
                    throw new IOException("Could not generate directory, possible permission issue");
                }
                javaFile.writeTo(outputFolder);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (typeSpec.t2() != null) {
                JavaFile javaFile2 = JavaFile.builder(codeGenerator.classNames.get(classInfo.loadClass()).packageName(), typeSpec.t2()).build();
                try {
                    if (!outputFolder.exists() && !outputFolder.mkdirs()) {
                        throw new IOException("Could not generate directory, possible permission issue");
                    }
                    javaFile2.writeTo(outputFolder);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public CodeGenerator(String targetPackageName) {
        this.targetPackageName = targetPackageName;
    }

    private List<ClassInfo> findClassesInPackage(String packageName) {
        try (ScanResult scanResult = new ClassGraph()
                .enableAllInfo()
                .acceptPackages(packageName)
                .scan()) {
            ClassInfoList classInfoList = scanResult.getAllClasses();
            List<ClassInfo> classInfos = classInfoList.stream()
                    .filter(classInfo -> (classInfo.isPublic() || classInfo.isPackageVisible()) && !classInfo.isInnerClass())
                    .toList();
            classNames.putAll(determineClassNames(classInfos, ""));
            return classInfos;
        }
    }

    private Map<Class<?>, ClassName> determineClassNames(List<ClassInfo> classInfos, String packageSuffix) {
        Map<Class<?>, ClassName> output = new HashMap<>();
        for (ClassInfo classInfo : classInfos) {
            Map<Class<?>, ClassName> classNameMap = determineClassNames(classInfo.getInnerClasses(), packageSuffix + "." + classInfo.getSimpleName());
            output.putAll(classNameMap);
            String modifiedPackage = getModifiedPackage(classInfo.loadClass());
            if (modifiedPackage != null) {
                output.put(classInfo.loadClass(), ClassName.get(modifiedPackage + packageSuffix, classInfo.getSimpleName().endsWith("Impl") ? classInfo.getSimpleName() + "0" : classInfo.getSimpleName()));
            }
        }
        return output;
    }

    private Pair<TypeSpec, @Nullable TypeSpec> createTypeSpec(ClassInfo classInfo) {
        Class<?> classToReplicate = classInfo.loadClass();
        List<TypeSpec> children = classInfo.getInnerClasses().stream()
                .filter(classInfo1 -> (classInfo1.isPublic() || classInfo1.isPackageVisible()) && !classInfo1.isAnonymousInnerClass())
                .map(this::createTypeSpec)
                .flatMap(pair -> Stream.of(pair.t1(), pair.t2()))
                .filter(Objects::nonNull)
                .toList();
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
        Arrays.stream(classToReplicate.getGenericInterfaces())
                .map(interfaceInstance -> Util.getTypeName(interfaceInstance, redefinitions.getOrDefault(classToReplicate, new HashMap<>()), classNames))
                .forEach(mirror::addSuperinterface);
        if (classToReplicate.getSuperclass() != Object.class && classToReplicate.getSuperclass() != null) {
            mirror.superclass(Util.getTypeName(classToReplicate.getSuperclass(), redefinitions.get(classToReplicate), classNames));
        }
        List<MethodSpec> methodSpecs = generateMethods(classToReplicate, true, simplifiedName, redefinitions);
        mirror.addMethods(methodSpecs);
        for (@NotNull TypeVariable<? extends Class<?>> typeParameter : classToReplicate.getTypeParameters()) {
            mirror.addTypeVariable(Util.getTypeVariableName(typeParameter, classNames, new HashMap<>()));
        }
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
            typeSpecBuilder = TypeSpec.annotationBuilder(className)
                    .addMethods(generateMethods(classToReplicate, false, className.simpleName(), redefinitions));
        } else if (classToReplicate.isInterface()) {
            typeSpecBuilder = TypeSpec.interfaceBuilder(className)
                    .addMethods(generateMethods(classToReplicate, false, className.simpleName(), redefinitions));
        } else if (classToReplicate.isRecord()) {
            typeSpecBuilder = TypeSpec.recordBuilder(className)
                    .addMethods(generateMethods(classToReplicate, false, className.simpleName(), redefinitions));
        } else if (classToReplicate.isEnum()) {
            typeSpecBuilder = TypeSpec.enumBuilder(className)
                    .addMethods(generateMethods(classToReplicate, false, className.simpleName(), redefinitions));
        } else {
            typeSpecBuilder = TypeSpec.classBuilder(className)
                    .addMethods(generateMethods(classToReplicate, false, className.simpleName(), redefinitions));
            canHaveSuperClass = true;
        }
        if (!classToReplicate.isAnnotation()) {
            typeSpecBuilder
                    .addSuperinterfaces(
                            Arrays.stream(classToReplicate.getGenericInterfaces())
                                    .map(typeName -> Util.getTypeName(typeName, redefinitions.getOrDefault(classToReplicate, new HashMap<>()), classNames))
                                    .toList()
                    );
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
        if (java.lang.reflect.Modifier.isPublic(classToReplicate.getModifiers())) {
            typeSpecBuilder.addModifiers(Modifier.PUBLIC);
        }
        if (java.lang.reflect.Modifier.isAbstract(classToReplicate.getModifiers()) && !classToReplicate.isInterface() && !classToReplicate.isEnum() && !classToReplicate.isRecord()) {
            typeSpecBuilder.addModifiers(Modifier.ABSTRACT);
        }
        return typeSpecBuilder.build();
    }

    private List<MethodSpec> generateMethods(Class<?> classToReplicate, boolean isImplementation, String className, Map<Class<?>, Map<String, String>> redefinitions) {
        Method[] methods = !isImplementation ? classToReplicate.getDeclaredMethods() : Stream
                .concat(Arrays.stream(findNecessaryMethods(classToReplicate)), Arrays.stream(classToReplicate.getMethods())).toArray(Method[]::new);
        List<Pair<MethodData, Method>> methodData = new ArrayList<>();
        for (Method method : methods) {
            if (isMethodBanned(method, classToReplicate)) {
                continue;
            }
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers()) && !java.lang.reflect.Modifier.isProtected(method.getModifiers()) && !java.lang.reflect.Modifier.isAbstract(method.getModifiers())) {
                continue;
            }
            if (isImplementation && method.isDefault()) {
                continue;
            }
            if (method.isBridge()) {
                continue;
            }
            insertMethodData(method, MethodData.from(method, classNames, isImplementation, redefinitions.getOrDefault(method.getDeclaringClass(), new HashMap<>())), methodData);
        }
        return methodData.stream()
                .map(Pair::t1)
                .map(methodData1 -> methodData1.toMethodSpec(className)).toList();
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

    private boolean isChildMethod(Method method1, Method method2) {
        Class<?>[] methodData1 = Stream.concat(Stream.of(method1.getReturnType()), Arrays.stream(method1.getParameterTypes())).toArray(Class[]::new);
        Class<?>[] methodData2 = Stream.concat(Stream.of(method2.getReturnType()), Arrays.stream(method2.getParameterTypes())).toArray(Class[]::new);
        if (methodData1.length != methodData2.length) {
            return false;
        }
        if (!method1.getName().equals(method2.getName())) {
            return false;
        }
        for (int i = 0; i < methodData1.length; i++) {
            if (!methodData2[i].isAssignableFrom(methodData1[i])) {
                return false;
            }
        }
        return true;
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

