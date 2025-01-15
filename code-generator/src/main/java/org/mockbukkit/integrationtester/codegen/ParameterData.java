package org.mockbukkit.integrationtester.codegen;

import com.palantir.javapoet.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ParameterData {

    private final String name;
    final TypeName type;
    private final List<String> annotations;

    private ParameterData(String name, TypeName type, List<String> annotations) {
        this.name = name;
        this.type = type;
        this.annotations = annotations;
    }

    public String getName() {
        return name;
    }

    public static ParameterData[] from(Method method, Map<Class<?>, ClassName> classNames, Map<String, String> redefinitions) {
        List<ParameterData> output = new ArrayList<>();
        Type[] genericParameterInfo = method.getGenericParameterTypes();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            output.add(new ParameterData(parameters[i].getName(),
                    Util.getTypeName(genericParameterInfo[i], redefinitions, classNames),
                    Util.getAnnotationTypeNames(parameters[i], classNames, redefinitions)));
        }
        return output.toArray(ParameterData[]::new);
    }

    public ParameterSpec toParameterSpec() {
        return ParameterSpec.builder(type, name)
                .addAnnotations(annotations.stream()
                        .map(string -> AnnotationSpec.builder(ClassName.bestGuess(string)).build())
                        .toList())
                .build();
    }

    @Override
    public int hashCode() {
        return rawTypeString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ParameterData other)) {
            return false;
        }
        return rawTypeString().equals(other.rawTypeString());
    }

    public String rawTypeString() {
        if (type instanceof ParameterizedTypeName parameterizedTypeName) {
            return parameterizedTypeName.rawType().canonicalName();
        }
        return type.toString();
    }
}
