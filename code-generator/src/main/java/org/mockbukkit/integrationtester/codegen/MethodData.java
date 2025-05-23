package org.mockbukkit.integrationtester.codegen;

import com.google.common.base.Objects;
import com.palantir.javapoet.*;
import org.jetbrains.annotations.NotNull;

import javax.lang.model.element.Modifier;
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MethodData {
    private final ParameterData[] parameterData;
    private final String methodName;
    private final TypeName methodReturnType;
    private final String[] methodAnnotations;
    private final TypeVariableName[] methodGenerics;
    private final boolean isStatic;
    private final boolean isAbstract;
    private final boolean isDefault;
    private final boolean isPublic;
    private final boolean varArgs;

    private MethodData(String methodName, ParameterData[] parameterData, TypeName methodReturnType, String[] methodAnnotations, TypeVariableName[] methodGenerics, boolean isStatic, boolean isAbstract, boolean isDefault, boolean isPublic, boolean varArgs) {
        this.methodName = methodName;
        this.parameterData = parameterData;
        this.methodReturnType = methodReturnType;
        this.methodAnnotations = methodAnnotations;
        this.methodGenerics = methodGenerics;
        this.isStatic = isStatic;
        this.isAbstract = isAbstract;
        this.isDefault = isDefault;
        this.isPublic = isPublic;
        this.varArgs = varArgs;
    }

    public static MethodData from(Method method, Map<Class<?>, ClassName> classNames, boolean isImplementation, boolean isEnum, Map<String, String> typeConversions) {
        List<TypeVariableName> methodGenerics = new ArrayList<>();
        for (@NotNull TypeVariable<Method> typeParameter : method.getTypeParameters()) {
            methodGenerics.add(Util.getTypeVariableName(typeParameter, classNames, typeConversions));
        }
        return new MethodData(
                method.getName(),
                ParameterData.from(method, classNames, typeConversions),
                Util.getTypeName(method.getGenericReturnType(), typeConversions, classNames),
                Util.getAnnotationTypeNames(method, classNames).toArray(String[]::new),
                methodGenerics.toArray(TypeVariableName[]::new),
                java.lang.reflect.Modifier.isStatic(method.getModifiers()),
                java.lang.reflect.Modifier.isAbstract(method.getModifiers()) && !isImplementation && !isEnum,
                method.isDefault(),
                java.lang.reflect.Modifier.isPublic(method.getModifiers()),
                method.isVarArgs()
        );
    }

    public MethodSpec toMethodSpec(Class<?> clazz) {
        MethodSpec.Builder methodSpec = MethodSpec.methodBuilder(methodName)
                .addAnnotations(Arrays.stream(methodAnnotations).map(name -> AnnotationSpec.builder(ClassName.bestGuess(name)).build()).toList())
                .addParameters(Arrays.stream(parameterData).map(ParameterData::toParameterSpec).toList())
                .returns(methodReturnType);
        for (TypeVariableName typeParameter : methodGenerics) {
            methodSpec.addTypeVariable(typeParameter);
        }
        if (isStatic) {
            ClassName mirrorHandler = ClassName.get("org.mockbukkit.integrationtester.testclient", "MirrorHandler");
            methodSpec.addStatement((!methodReturnType.toString().equals("void") ? "return " : "") + "$T.handleStatic($S, $S$L)", mirrorHandler, methodName, clazz, generateParameterString());
            methodSpec.addModifiers(isPublic ? javax.lang.model.element.Modifier.PUBLIC : javax.lang.model.element.Modifier.PROTECTED, javax.lang.model.element.Modifier.STATIC);
        } else if (isAbstract && !isDefault) {
            methodSpec.addModifiers(isPublic ? javax.lang.model.element.Modifier.PUBLIC : javax.lang.model.element.Modifier.PROTECTED, javax.lang.model.element.Modifier.ABSTRACT);
        } else {
            methodSpec.addModifiers(isPublic ? javax.lang.model.element.Modifier.PUBLIC : javax.lang.model.element.Modifier.PROTECTED);
            ClassName mirrorHandler = ClassName.get("org.mockbukkit.integrationtester.testclient", "MirrorHandler");
            methodSpec.addStatement((!methodReturnType.toString().equals("void") ? "return " : "") + "$T.handle($S, $S, this$L)", mirrorHandler, methodName, clazz, generateParameterString());
        }
        if (isDefault) {
            methodSpec.addModifiers(Modifier.DEFAULT);
        }
        methodSpec.varargs(varArgs);
        return methodSpec.build();
    }

    private String generateParameterString() {
        StringBuilder stringBuilder = new StringBuilder();
        for (ParameterData parameter : parameterData) {
            stringBuilder.append(", ").append(parameter.getName());
        }
        return stringBuilder.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(Objects.hashCode((Object[]) parameterData), methodReturnType.toString());
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MethodData methodData)) {
            return false;
        }
        return methodReturnType.toString().equals(methodData.methodReturnType.toString()) && Arrays.equals(parameterData, methodData.parameterData);
    }

    public boolean precedes(MethodData other) {
        if (other.parameterData.length != parameterData.length) {
            return false;
        }
        for (int i = 0; i < parameterData.length; i++) {
            if (!parameterData[i].precedes(other.parameterData[i])) {
                return false;
            }
        }
        return other.methodReturnType.getClass() == ClassName.class && methodReturnType.getClass() != ClassName.class;
    }
}
