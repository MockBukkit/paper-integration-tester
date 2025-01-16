package org.mockbukkit.integrationtester.codegen;

import com.palantir.javapoet.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Stream;

public class Util {

    public static TypeName getTypeName(Type type, Map<String, String> typeRedefinitions, Map<Class<?>, ClassName> classNames) {
        if (type instanceof ParameterizedType parameterizedType) {
            String name = parameterizedType.getRawType().getTypeName();
            Type[] bounds = parameterizedType.getActualTypeArguments();
            return ParameterizedTypeName.get(ClassName.bestGuess(fixTypeName(name, classNames, typeRedefinitions)), getTypeNames(bounds, typeRedefinitions, classNames, false));
        }
        if (type instanceof WildcardType wildcardType) {
            if (wildcardType.getLowerBounds().length > 0) {
                return WildcardTypeName.supertypeOf(getTypeNames(wildcardType.getLowerBounds(), typeRedefinitions, classNames, true)[0]);
            } else if (!wildcardType.getUpperBounds()[0].getTypeName().equals(Object.class.getName())) {
                return WildcardTypeName.subtypeOf(getTypeNames(wildcardType.getUpperBounds(), typeRedefinitions, classNames, true)[0]);
            }
            return WildcardTypeName.get(wildcardType);
        }
        if (type instanceof TypeVariable) {
            String redefinedType = typeRedefinitions.getOrDefault(type.getTypeName(), ((TypeVariable<?>) type).getName());
            try {
                getClass(redefinedType); // If class can be found
                return ClassName.bestGuess(redefinedType);
            } catch (ClassNotFoundException e) {
                return TypeVariableName.get(redefinedType);
            }
        }
        if (type instanceof Class<?> clazz && clazz.isArray()) {
            return ArrayTypeName.of(getTypeName(clazz.getComponentType(), typeRedefinitions, classNames));
        }
        if (type instanceof GenericArrayType genericArrayType) {
            return ArrayTypeName.of(getTypeName(genericArrayType.getGenericComponentType(), typeRedefinitions, classNames));
        }
        if (type.getTypeName().contains(".")) {
            return ClassName.bestGuess(fixTypeName(type.getTypeName(), classNames, typeRedefinitions));
        } else {
            return ClassName.get(type);
        }
    }

    public static Map<Class<?>, Map<String, String>> compileTypeVariableConversions(Class<?> owningClass, Map<Class<?>, ClassName> classNames, Map<String, String> childRedefinitions) {
        if (owningClass == Object.class) {
            return Map.of();
        }
        Map<Class<?>, Map<String, String>> output = new HashMap<>();
        Type superClassType = owningClass.getGenericSuperclass();
        if (superClassType instanceof ParameterizedType parameterizedType) {
            Class<?> superClass = owningClass.getSuperclass();
            TypeVariable<?>[] superClassTypeVariables = superClass.getTypeParameters();
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            Map<String, String> redefinitions = getRedefinitions(actualTypeArguments, superClassTypeVariables, classNames, childRedefinitions);
            Map<Class<?>, Map<String, String>> temp = compileTypeVariableConversions(superClass, classNames, redefinitions);
            temp.put(superClass, redefinitions);
            output.putAll(temp);
        }
        Type[] genericInterfaces = owningClass.getGenericInterfaces();
        Class<?>[] interfaces = owningClass.getInterfaces();
        for (int i = 0; i < interfaces.length; i++) {
            Type interfaceType = genericInterfaces[i];
            Type[] actualTypeArguments;
            if (!(interfaceType instanceof ParameterizedType parameterizedType)) {
                actualTypeArguments = new Type[0];
            } else {
                actualTypeArguments = parameterizedType.getActualTypeArguments();
            }
            TypeVariable<?>[] superClassTypeVariables = interfaces[i].getTypeParameters();
            Map<String, String> redefinitions = getRedefinitions(actualTypeArguments, superClassTypeVariables, classNames, childRedefinitions);
            Map<Class<?>, Map<String, String>> temp = compileTypeVariableConversions(interfaces[i], classNames, redefinitions);
            temp.put(interfaces[i], redefinitions);
            output.putAll(temp);
        }
        return output;
    }

    private static Map<String, String> getRedefinitions(Type[] childTypeArguments, Type[] superTypeVariables, Map<Class<?>, ClassName> classNames, Map<String, String> childRedefinitions) {
        Map<String, String> redefinitions = new HashMap<>();
        for (int i = 0; i < childTypeArguments.length; i++) {
            redefinitions.put(superTypeVariables[i].getTypeName(), getTypeName(childTypeArguments[i], childRedefinitions, classNames).toString());
        }
        for (Map.Entry<String, String> redefinition : new HashSet<>(redefinitions.entrySet())) {
            redefinitions.put(redefinition.getKey(), childRedefinitions.getOrDefault(redefinition.getKey(), redefinition.getValue()));
        }
        return redefinitions;
    }

    public static TypeVariableName getTypeVariableName(TypeVariable<?> typeVariable, Map<Class<?>, ClassName> classNames, Map<String, String> typeVariableConversions) {
        String name = typeVariable.getName();
        Type[] bounds = typeVariable.getBounds();
        return TypeVariableName.get(name, getTypeNames(bounds, typeVariableConversions, classNames, true));
    }

    private static String fixTypeName(String name, Map<Class<?>, ClassName> classNames, Map<String, String> typeVariableConversions) {
        try {
            Class<?> clazz = getClass(name);
            return Optional.ofNullable(classNames.get(clazz))
                    .map(ClassName::canonicalName)
                    .map(canonicalName -> typeVariableConversions.getOrDefault(canonicalName, canonicalName))
                    .orElse(name)
                    .replace("$", ".");
        } catch (ClassNotFoundException e) {
            return name;
        }
    }

    public static Class<?> getClass(String className) throws ClassNotFoundException {
        String[] strings = className.split("\\$");
        Class<?> clazz = null;
        String prev = "";
        for (String string : strings) {
            if (clazz == null) {
                clazz = Class.forName(string, false, ClassLoader.getSystemClassLoader());
                prev = string;
            } else {
                String finalPrev = prev;
                clazz = Stream.concat(Arrays.stream(clazz.getClasses()), Arrays.stream(clazz.getDeclaredClasses())).filter(aClass -> (finalPrev + "$" + string).contains(aClass.getName())).findFirst()
                        .orElseThrow(() -> new NoSuchElementException(className));
                prev = prev + "$" + string;
            }
        }
        return clazz;
    }

    public static TypeName[] getTypeNames(Type[] genericInfo, Map<String, String> typeVariableRedefinitions, Map<Class<?>, ClassName> classNames, boolean ignoreObject) {
        List<TypeName> typeVariableNames = new ArrayList<>();
        for (Type type : genericInfo) {
            if (type.getTypeName().equals("java.lang.Object") && ignoreObject) {
                continue;
            }
            typeVariableNames.add(getTypeName(type, typeVariableRedefinitions, classNames));
        }
        return typeVariableNames.toArray(TypeName[]::new);
    }

    public static List<String> getAnnotationTypeNames(AnnotatedElement annotatedElement, Map<Class<?>, ClassName> classNames) {
        List<String> output = new ArrayList<>();
        for (Annotation annotationInfo : annotatedElement.getAnnotations()) {
            if (annotationInfo.annotationType().getPackageName().startsWith("jdk.internal")) {
                continue;
            }
            if (!annotationInfo.annotationType().accessFlags().contains(AccessFlag.PUBLIC)) {
                continue;
            }
            output.add(Util.fixTypeName(annotationInfo.annotationType().getName(), classNames, new HashMap<>()));
        }
        return output;
    }

}
