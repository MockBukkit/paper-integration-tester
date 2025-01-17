package org.mockbukkit.integrationtester.codegen;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.TypeName;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
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

    public static ParameterData[] from(Executable executable, Map<Class<?>, ClassName> classNames, Map<String, String> redefinitions) {

        List<ParameterData> output = new ArrayList<>();
        Type[] genericParameterInfo = executable.getGenericParameterTypes();
        Parameter[] parameters = executable.getParameters();
        if (genericParameterInfo.length != parameters.length) {
            if (executable instanceof Constructor<?> && executable.getDeclaringClass().isEnum()) {
                parameters = Arrays.copyOfRange(parameters, 2, parameters.length);
            } else {
                throw new RuntimeException("Parsing issue for executable " + executable);
            }
        }
        for (int i = 0; i < parameters.length; i++) {
            output.add(new ParameterData(parameters[i].getName(),
                    Util.getTypeName(genericParameterInfo[i], redefinitions, classNames),
                    Util.getAnnotationTypeNames(parameters[i], classNames)));
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
        return type.toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ParameterData other)) {
            return false;
        }
        return type.toString().equals(other.type.toString());
    }

    public boolean precedes(ParameterData other) {
        return other.type.getClass() == ClassName.class && type.getClass() != ClassName.class;
    }
}
