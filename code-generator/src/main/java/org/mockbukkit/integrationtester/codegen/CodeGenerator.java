package org.mockbukkit.integrationtester.codegen;

import com.google.common.base.Preconditions;
import com.palantir.javapoet.*;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import org.jetbrains.annotations.Nullable;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeGenerator {

    private final File outputFolder;
    private final String targetPackageName;
    private final Map<Class<?>, ClassName> classNames = new HashMap<>();
    private final static Pattern PACKAGE_NAME = Pattern.compile("^(.+)\\.[A-Z]");

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.print("Usage: <target folder>");
            return;
        }
        File outputFolder = new File(args[0]);
        CodeGenerator codeGenerator = new CodeGenerator(outputFolder, "org.mockbukkit.integrationtester");
        List<ClassInfo> outerClasses = new ArrayList<>();
        for (String operationIncludedPackage : operationIncludedPackages()) {
            outerClasses.addAll(codeGenerator.fillClassesInPackage(operationIncludedPackage));
        }
        outerClasses.forEach(codeGenerator::fillClass);
    }


    public CodeGenerator(File outputFolder, String targetPackageName) {
        this.outputFolder = outputFolder;
        this.targetPackageName = targetPackageName;
    }

    private List<ClassInfo> fillClassesInPackage(String packageName) {
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
            output.putAll(determineClassNames(classInfo.getInnerClasses(), packageSuffix + "." + "Internal" + modifySimpleClassName(classInfo.loadClass())));
            String modifiedPackage = getModifiedPackage(classInfo.loadClass());
            if (modifiedPackage != null) {
                output.put(classInfo.loadClass(), ClassName.get(modifiedPackage + packageSuffix, modifySimpleClassName(classInfo.loadClass())));
            }
        }
        return output;
    }

    private void fillClass(ClassInfo classInfo) {
        Class<?> classToReplicate = classInfo.loadClass();
        classInfo.getInnerClasses().stream()
                .filter(classInfo1 -> (classInfo1.isPublic() || classInfo1.isPackageVisible()) && !classInfo1.isAnonymousInnerClass())
                .forEach(this::fillClass);
        boolean needsImplementation = generateInterface(classToReplicate);
        if (needsImplementation) {
            generateImplementation(classToReplicate);
        }
    }

    private void generateImplementation(Class<?> classToReplicate) {
        ClassName className = classNames.get(classToReplicate);
        String simplifiedName = className.simpleName() + "Impl";
        List<MethodSpec> methodSpecs = generateMethods(classToReplicate, false, simplifiedName);
        TypeSpec mirror = TypeSpec.classBuilder(simplifiedName)
                .addMethods(methodSpecs)
                .addSuperinterface(className)
                .build();
        JavaFile javaFile = JavaFile.builder(className.packageName(), mirror).build();

        try {
            if (!outputFolder.exists() && !outputFolder.mkdirs()) {
                throw new IOException("Could not generate directory, possible permission issue");
            }
            javaFile.writeTo(outputFolder);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean generateInterface(Class<?> classToReplicate) {
        boolean needsImplementation = false;
        boolean canHaveSuperClass = false;
        TypeSpec.Builder typeSpecBuilder;
        ClassName className = classNames.get(classToReplicate);
        if(className == null) {
            throw new NullPointerException(classToReplicate.getName());
        }
        if (classToReplicate.isInterface()) {
            typeSpecBuilder = TypeSpec.interfaceBuilder(className)
                    .addMethods(generateMethods(classToReplicate, true, className.simpleName()));
            needsImplementation = true;
        } else if (classToReplicate.isRecord()) {
            typeSpecBuilder = TypeSpec.recordBuilder(className)
                    .addMethods(generateMethods(classToReplicate, false, className.simpleName()));
        } else if (classToReplicate.isEnum()) {
            typeSpecBuilder = TypeSpec.enumBuilder(className)
                    .addMethods(generateMethods(classToReplicate, false, className.simpleName()));
        } else {
            typeSpecBuilder = TypeSpec.classBuilder(className)
                    .addMethods(generateMethods(classToReplicate, false, className.simpleName()));
            canHaveSuperClass = true;
        }

        TypeSpec.Builder mirror = typeSpecBuilder
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterfaces(
                        Arrays.stream(classToReplicate.getInterfaces()).map(this::sanitizeClass).toList());
        if (canHaveSuperClass && classToReplicate.getSuperclass() != Object.class) {
            for (String operationIncluded : operationIncludedPackages()) {
                if (classToReplicate.getSuperclass().getPackageName().startsWith(operationIncluded)) {
                    mirror.superclass(sanitizeClass(classToReplicate.getSuperclass()));
                    break;
                }
            }
        }
        JavaFile javaFile = JavaFile.builder(className.packageName(), mirror.build()).build();

        try {
            if (!outputFolder.exists() && !outputFolder.mkdirs()) {
                throw new IOException("Could not generate directory, possible permission issue");
            }
            javaFile.writeTo(outputFolder);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return needsImplementation;
    }

    private List<MethodSpec> generateMethods(Class<?> classToReplicate, boolean isAbstract, String className) {
        List<MethodSpec> methodSpecs = new ArrayList<>();
        Method[] methods = isAbstract ? classToReplicate.getDeclaredMethods() : classToReplicate.getMethods();
        for (Method method : methods) {
            if (isMethodBanned(method, classToReplicate) || ignoreMethod(method, methods)) {
                continue;
            }
            MethodSpec.Builder methodSpec = MethodSpec.methodBuilder(method.getName())
                    .addAnnotations(getAnnotationSpec(method))
                    .addParameters(getParameterSpec(method))
                    .returns(sanitizeClass(method.getReturnType()));
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                String parameterString = className + ".class" + generateParameterString(method);
                ClassName mirrorHandler = ClassName.get("org.mockbukkit.integrationtester.testclient", "MirrorHandler");
                methodSpec.addStatement((method.getReturnType() != void.class ? "return " : "") + "$T.handle($S, $L)", mirrorHandler, method.getName(), parameterString);
                methodSpec.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
            } else if (isAbstract) {
                methodSpec.addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);
            } else {
                String parameterString = "this" + generateParameterString(method);
                methodSpec.addModifiers(Modifier.PUBLIC);
                ClassName mirrorHandler = ClassName.get("org.mockbukkit.integrationtester.testclient", "MirrorHandler");
                methodSpec.addStatement((method.getReturnType() != void.class ? "return " : "") + "$T.handle($S, $L)", mirrorHandler, method.getName(), parameterString);
            }

            methodSpecs.add(methodSpec.build());
        }
        return methodSpecs;
    }

    private boolean ignoreMethod(Method method, Method[] methods) {
        int methodPos = Integer.MAX_VALUE;
        for (int i = 0; i < methods.length; i++) {
            Method otherMethod = methods[i];
            if (method == otherMethod) {
                methodPos = Math.min(i, methodPos);
                continue;
            }
            if (!method.getName().equals(otherMethod.getName()) || !Arrays.equals(method.getParameterTypes(), otherMethod.getParameterTypes())) {
                continue;
            }
            methodPos = Math.min(i, methodPos);
            if (otherMethod.getReturnType().isAssignableFrom(method.getReturnType()) && (!method.getReturnType().isAssignableFrom(otherMethod.getReturnType()) || i >= methodPos)) {
                return true;
            }

        }
        return false;
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

    private String generateParameterString(Method method) {
        StringBuilder stringBuilder = new StringBuilder();
        for (Parameter parameter : method.getParameters()) {
            stringBuilder.append(", ").append(parameter.getName());
        }
        return stringBuilder.toString();
    }

    private List<AnnotationSpec> getAnnotationSpec(AnnotatedElement annotatedElement) {
        List<AnnotationSpec> output = new ArrayList<>();
        for (Annotation annotationInfo : annotatedElement.getAnnotations()) {
            if (annotationInfo.annotationType().getPackageName().startsWith("jdk.internal")) {
                continue;
            }
            if (!annotationInfo.annotationType().accessFlags().contains(AccessFlag.PUBLIC)) {
                continue;
            }
            output.add(AnnotationSpec.builder(annotationInfo.annotationType()).build());
        }
        return output;
    }

    private List<ParameterSpec> getParameterSpec(Method methodInfo) {
        List<ParameterSpec> output = new ArrayList<>();
        for (Parameter parameterInfo : methodInfo.getParameters()) {
            TypeName typeName = sanitizeClass(parameterInfo.getType());
            ParameterSpec parameterSpec = ParameterSpec.builder(typeName, parameterInfo.getName())
                    .addAnnotations(getAnnotationSpec(parameterInfo))
                    .build();
            output.add(parameterSpec);
        }
        return output;
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

    private String modifySimpleClassName(Class<?> inputClass) {
        Matcher matcher = Pattern.compile("\\[.*$").matcher(inputClass.getSimpleName());
        if (matcher.find()) {
            String brackets = matcher.group();
            return matcher.replaceAll("") + "Mirror" + brackets;
        } else {
            return inputClass.getSimpleName() + "Mirror";
        }
    }

    private static List<String> operationIncludedPackages() {
        return List.of("co.aikar", "com.destroystokyo.paper", "io.papermc.paper", "org.bukkit", "org.spigotmc", "net.kyori.adventure");
    }
}

