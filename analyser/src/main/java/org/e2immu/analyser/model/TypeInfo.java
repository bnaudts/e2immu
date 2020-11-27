/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.analyser.inspector.MethodInspectionImpl;
import org.e2immu.analyser.inspector.MethodTypeParameterMap;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.MethodReference;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.*;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.BY_HAND;
import static org.e2immu.analyser.util.Logger.LogTarget.LAMBDA;
import static org.e2immu.analyser.util.Logger.log;

public class TypeInfo implements NamedType, WithInspectionAndAnalysis {

    @NotNull
    public final String simpleName;
    @NotNull
    public final String fullyQualifiedName;

    // when this type is an inner or nested class of an enclosing class
    public final Either<String, TypeInfo> packageNameOrEnclosingType;

    //@Immutable(after="this.inspect()")
    public final SetOnce<TypeInspection> typeInspection = new SetOnce<>();
    public final SetOnce<TypeResolution> typeResolution = new SetOnce<>();
    public final SetOnce<TypeAnalysis> typeAnalysis = new SetOnce<>();

    // creates an anonymous version of the parent type parameterizedType
    public TypeInfo(TypeInfo enclosingType, int number) {
        this(enclosingType, "$" + number);
    }

    @Override
    public void setAnalysis(Analysis analysis) {
        typeAnalysis.set((TypeAnalysis) analysis);
    }

    @Override
    public String name() {
        return simpleName;
    }

    public TypeInfo(String packageName, String simpleName) {
        this.simpleName = Objects.requireNonNull(simpleName);
        this.packageNameOrEnclosingType = Either.left(packageName);
        this.fullyQualifiedName = packageName + "." + simpleName;
    }

    public TypeInfo(TypeInfo enclosingType, String simpleName) {
        this.simpleName = Objects.requireNonNull(simpleName);
        this.packageNameOrEnclosingType = Either.right(enclosingType);
        this.fullyQualifiedName = enclosingType.fullyQualifiedName + "." + simpleName;
    }

    @Override
    public Inspection getInspection() {
        return typeInspection.get();
    }

    @Override
    public String fullyQualifiedName() {
        return fullyQualifiedName;
    }

    public boolean hasBeenInspected() {
        return typeInspection.isSet();
    }

    public String stream() {
        return stream(0);
    }

    public String stream(int indent) {
        return stream(indent, true);
    }

    public String stream(int indent, boolean doTypeDeclaration) {
        boolean isSubType = indent > 0;
        String typeNature;
        Set<AnnotationExpression> annotations = new HashSet<>();
        Set<String> imports = isSubType ? Collections.emptySet() : imports(typeInspection.get());
        Stream<String> typeModifiers;
        List<FieldInfo> fields;
        List<MethodInfo> constructors;
        List<MethodInfo> methods;
        List<TypeInfo> subTypes;
        String interfacesCsv = "";
        String typeParametersCsv = "";
        String parentClass = "";

        if (hasBeenInspected()) {
            TypeInspection typeInspection = this.typeInspection.get();
            typeNature = typeInspection.typeNature().toJava();
            typeModifiers = typeInspection.modifiers().stream().map(TypeModifier::toJava);
            annotations.addAll(typeInspection.getAnnotations());
            fields = typeInspection.fields();
            constructors = typeInspection.constructors();
            methods = typeInspection.methods();
            subTypes = typeInspection.subTypes();
            parentClass = parentIsNotJavaLangObject() ? typeInspection.parentClass().print() : "";
            interfacesCsv = typeInspection.interfacesImplemented().stream()
                    .map(ParameterizedType::print).collect(Collectors.joining(", "));
            typeParametersCsv = typeInspection.typeParameters().stream()
                    .map(TypeParameter::print).collect(Collectors.joining(", "));
        } else {
            typeNature = "class"; // we really have no idea what it is
            typeModifiers = List.of("abstract").stream();
            fields = List.of();
            constructors = List.of();
            methods = List.of();
            subTypes = List.of();
        }

        Stream<String> fieldsStream = fields.stream().map(field -> field.stream(indent + 4));
        Stream<String> constructorsStream = constructors.stream().map(method -> method.stream(indent + 4));
        Stream<String> methodsStream = methods.stream().map(method -> method.stream(indent + 4));
        Stream<String> subTypesStream = subTypes.stream().map(subType -> subType.stream(indent + 4));

        boolean isMainType = indent == 0;

        StringBuilder sb = new StringBuilder();
        if (isMainType) {
            String packageName = packageNameOrEnclosingType.getLeftOrElse("");
            if (!packageName.isEmpty()) {
                sb.append("package ");
                sb.append(packageName);
                sb.append(";\n\n");
            }
            if (!imports.isEmpty()) {
                imports.stream().sorted().forEach(i ->
                        sb.append("import ")
                                .append(i)
                                .append(";\n"));
                sb.append("\n");
            }
        }
        Set<TypeInfo> annotationsSeen = new HashSet<>();
        for (AnnotationExpression annotation : annotations) {
            StringUtil.indent(sb, indent);
            sb.append(annotation.stream());
            if (typeAnalysis.isSet()) {
                typeAnalysis.get().peekIntoAnnotations(annotation, annotationsSeen, sb);
            }
            sb.append("\n");
        }
        if (typeAnalysis.isSet()) {
            typeAnalysis.get().getAnnotationStream().forEach(entry -> {
                boolean present = entry.getValue();
                AnnotationExpression annotation = entry.getKey();
                if (present && !annotationsSeen.contains(annotation.typeInfo())) {
                    StringUtil.indent(sb, indent);
                    sb.append(annotation.stream());
                    sb.append("\n");
                }
            });
        }
        if (doTypeDeclaration) {
            // the class name
            StringUtil.indent(sb, indent);
            sb.append(typeModifiers.map(s -> s + " ").collect(Collectors.joining()));
            sb.append(typeNature);
            sb.append(" ");
            sb.append(simpleName);
            if (!typeParametersCsv.isEmpty()) {
                sb.append("<");
                sb.append(typeParametersCsv);
                sb.append(">");
            }
            if (!parentClass.isEmpty()) {
                sb.append(" extends ");
                sb.append(parentClass);
            }
            if (!interfacesCsv.isEmpty()) {
                sb.append(" implements ");
                sb.append(interfacesCsv);
            }
        }
        sb.append(" {\n\n");

        // these already have indentation built in
        niceStream(sb, subTypesStream, "\n\n", "");
        niceStream(sb, fieldsStream, "\n", "\n");
        niceStream(sb, constructorsStream, "\n\n", "\n");
        niceStream(sb, methodsStream, "\n\n", "\n");

        StringUtil.indent(sb, indent);
        sb.append("}\n");
        return sb.toString();
    }

    private Set<String> imports(TypeInspection typeInspection) {
        Set<TypeInfo> typesReferenced = typeInspection.typesReferenced().stream().filter(Map.Entry::getValue)
                .filter(e -> !e.getKey().isJavaLang())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        Map<String, List<TypeInfo>> perPackage = new HashMap<>();
        String myPackage = packageName();
        typesReferenced.forEach(typeInfo -> {
            String packageName = typeInfo.packageName();
            if (packageName != null && !myPackage.equals(packageName)) {
                SMapList.add(perPackage, packageName, typeInfo);
            }
        });
        Set<String> result = new TreeSet<>();
        for (Map.Entry<String, List<TypeInfo>> e : perPackage.entrySet()) {
            List<TypeInfo> list = e.getValue();
            if (list.size() >= 4) {
                result.add(e.getKey() + ".*");
            } else {
                for (TypeInfo typeInfo : list) {
                    result.add(typeInfo.fullyQualifiedName);
                }
            }
        }
        return result;
    }

    private static void niceStream(StringBuilder sb, Stream<String> stream, String separator, String suffix) {
        AtomicInteger cnt = new AtomicInteger();
        stream.forEach(s -> {
            sb.append(s);
            sb.append(separator);
            cnt.incrementAndGet();
        });
        if (cnt.get() > 0)
            sb.append(suffix);
    }

    public boolean isJavaLang() {
        if (Primitives.isPrimitiveExcludingVoid(this)) return true;
        return Primitives.JAVA_LANG.equals(packageName());
    }

    @Override
    public String simpleName() {
        return simpleName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeInfo typeInfo = (TypeInfo) o;
        return fullyQualifiedName.equals(typeInfo.fullyQualifiedName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullyQualifiedName);
    }

    @Override
    public Optional<AnnotationExpression> hasInspectedAnnotation(Class<?> annotation) {
        if (!typeInspection.isSet()) return Optional.empty();
        String annotationFQN = annotation.getName();
        Optional<AnnotationExpression> fromType = (getInspection().getAnnotations().stream()
                .filter(ae -> ae.typeInfo().fullyQualifiedName.equals(annotationFQN)))
                .findFirst();
        if (fromType.isPresent()) return fromType;
        if (parentIsNotJavaLangObject()) {
            Optional<AnnotationExpression> fromParent = Objects.requireNonNull(typeInspection.get().parentClass().typeInfo)
                    .hasInspectedAnnotation(annotation);
            if (fromParent.isPresent()) return fromParent;
        }
        return Optional.empty();
    }

    public Optional<TypeInfo> inTypeInnerOuterHierarchy(TypeInfo typeInfo) {
        return inTypeInnerOuterHierarchy(typeInfo, new HashSet<>());
    }

    public boolean parentIsNotJavaLangObject() {
        return !Primitives.isJavaLangObject(typeInspection.get().parentClass());
    }

    private Optional<TypeInfo> inTypeInnerOuterHierarchy(TypeInfo typeInfo, Set<TypeInfo> visited) {
        if (typeInfo == this) return Optional.of(this);
        if (visited.contains(this)) return Optional.empty();
        visited.add(this);
        if (packageNameOrEnclosingType.isRight()) {
            TypeInfo parentClass = packageNameOrEnclosingType.getRight();
            Optional<TypeInfo> viaParent = parentClass.inTypeInnerOuterHierarchy(typeInfo, visited);
            if (viaParent.isPresent()) return viaParent;
        }
        for (TypeInfo subType : typeInspection.get().subTypes()) {
            Optional<TypeInfo> viaSubType = subType.inTypeInnerOuterHierarchy(typeInfo, visited);
            if (viaSubType.isPresent()) return viaSubType;
        }
        return Optional.empty();
    }

    public ParameterizedType asParameterizedType(InspectionProvider inspectionProvider) {
        return new ParameterizedType(this, inspectionProvider.getTypeInspection(this).typeParameters()
                .stream().map(tp -> new ParameterizedType(tp, 0, ParameterizedType.WildCard.NONE)).collect(Collectors.toList()));
    }

    // to be called before type inspection has been built
    public ParameterizedType asSimpleParameterizedType() {
        return new ParameterizedType(this, List.of());
    }

    public boolean isStatic() {
        assert typeInspection.isSet();
        return typeInspection.get().isStatic();
    }


    @Override
    public String toString() {
        return fullyQualifiedName;
    }

    public FieldInfo getFieldByName(String name, boolean complain) {
        Optional<FieldInfo> result = typeInspection.get().fields().stream().filter(fieldInfo -> fieldInfo.name.equals(name)).findFirst();
        return complain ? result.orElseThrow() : result.orElse(null);
    }

    public TypeInfo primaryType() {
        if (packageNameOrEnclosingType.isLeft()) return this;
        return packageNameOrEnclosingType.getRight().primaryType();
    }

    /*

    Function<String, Integer> f = Type::someFunction;

    gets converted into

    Function<String, Integer> f2 = new Function<String, Integer>() {
        @Override
        public Integer apply(String s) {
            return Type.someFunction(s);
        }
    };

     */

    public MethodInfo convertMethodReferenceIntoLambda(ParameterizedType functionalInterfaceType,
                                                       TypeInfo enclosingType,
                                                       MethodReference methodReference,
                                                       ExpressionContext expressionContext) {
        MethodTypeParameterMap method = functionalInterfaceType.findSingleAbstractMethodOfInterface(expressionContext.typeContext);
        TypeInfo typeInfo = new TypeInfo(enclosingType, expressionContext.topLevel.newIndex(enclosingType));
        TypeInspectionImpl.Builder builder = new TypeInspectionImpl.Builder(typeInfo, BY_HAND);
        builder.setTypeNature(TypeNature.CLASS);
        builder.addInterfaceImplemented(functionalInterfaceType);
        builder.setParentClass(expressionContext.typeContext.getPrimitives().objectParameterizedType);

        // there are no extra type parameters; only those of the enclosing type(s) can be in 'type'

        MethodInspectionImpl.Builder methodBuilder = method.buildCopy(typeInfo);
        MethodInfo methodInfo = methodBuilder.getMethodInfo();
        builder.addMethod(methodInfo);

        // compose the content of the method...
        MethodInspection methodReferenceInspection = expressionContext.typeContext.getMethodInspection(methodReference.methodInfo);
        Expression newReturnExpression;
        if (methodReferenceInspection.isStatic() || !(methodReference.scope instanceof TypeExpression)) {
            newReturnExpression = methodCallCopyAllParameters(methodReference.scope, methodReferenceInspection, methodBuilder);
        } else {
            if (methodBuilder.getParameters().size() != 1)
                throw new UnsupportedOperationException("Referenced method has multiple parameters");
            newReturnExpression = methodCallNoParameters(methodInfo, methodReferenceInspection);
        }
        Statement statement;
        if (methodInfo.isVoid()) {
            statement = new ExpressionAsStatement(newReturnExpression);
        } else {
            statement = new ReturnStatement(false, newReturnExpression);
        }
        Block block = new Block.BlockBuilder().addStatement(statement).build();

        log(LAMBDA, "Result of translating block: {}", block.statementString(0, null));
        methodBuilder.setInspectedBlock(block).build(expressionContext.typeContext);
        typeInfo.typeInspection.set(builder.build());
        expressionContext.addNewlyCreatedType(typeInfo);
        return methodInfo;
    }


    private Expression methodCallNoParameters(MethodInfo interfaceMethod, MethodInspection concreteMethod) {
        Expression newScope = new VariableExpression(interfaceMethod.methodInspection.get().getParameters().get(0));
        MethodTypeParameterMap methodTypeParameterMap = new MethodTypeParameterMap(concreteMethod, Map.of());
        return new MethodCall(newScope, newScope, methodTypeParameterMap, List.of());
    }

    private Expression methodCallCopyAllParameters(Expression scope, MethodInspection concreteMethod, MethodInspection interfaceMethod) {
        List<Expression> parameterExpressions = interfaceMethod
                .getParameters().stream().map(VariableExpression::new).collect(Collectors.toList());
        Map<NamedType, ParameterizedType> concreteTypes = new HashMap<>();
        int i = 0;
        for (ParameterInfo parameterInfo : concreteMethod.getParameters()) {
            ParameterInfo interfaceParameter = interfaceMethod.getParameters().get(i);
            if (interfaceParameter.parameterizedType.isTypeParameter()) {
                concreteTypes.put(interfaceParameter.parameterizedType.typeParameter, parameterInfo.parameterizedType);
            }
            i++;
        }
        MethodTypeParameterMap methodTypeParameterMap = new MethodTypeParameterMap(concreteMethod, concreteTypes);
        return new MethodCall(scope, scope, methodTypeParameterMap, parameterExpressions);
    }

    public boolean isNestedType() {
        return packageNameOrEnclosingType.isRight();
    }

    public boolean isPrivate() {
        return typeInspection.get().modifiers().contains(TypeModifier.PRIVATE);
    }

    public boolean isAnEnclosingTypeOf(TypeInfo typeInfo) {
        if (typeInfo == this) return true;
        if (packageNameOrEnclosingType.isLeft()) return false;
        return isAnEnclosingTypeOf(typeInfo.packageNameOrEnclosingType.getRight());
    }

    public boolean isRecord() {
        return isNestedType() && isPrivate();
    }

    public boolean isInterface() {
        return typeInspection.get().typeNature() == TypeNature.INTERFACE;
    }

    public Set<ObjectFlow> objectFlows(AnalysisProvider analysisProvider) {
        Set<ObjectFlow> result = new HashSet<>(analysisProvider.getTypeAnalysis(this).getConstantObjectFlows());
        for (MethodInfo methodInfo : typeInspection.get().methodsAndConstructors()) {
            // set, because the returned object flow could equal either one of the non-returned, or parameter flows
            for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().getParameters()) {
                result.add(analysisProvider.getParameterAnalysis(parameterInfo).getObjectFlow());
            }
            MethodAnalysis methodAnalysis = analysisProvider.getMethodAnalysis(methodInfo);
            result.addAll(methodAnalysis.getInternalObjectFlows());

            if (!methodInfo.isConstructor && !methodInfo.isVoid()) {
                result.add(methodAnalysis.getObjectFlow());
            }
        }
        // for fields we only add those owned by the field itself (i.e. with an initialiser)
        for (FieldInfo fieldInfo : typeInspection.get().fields()) {
            FieldAnalysis fieldAnalysis = analysisProvider.getFieldAnalysis(fieldInfo);
            ObjectFlow objectFlow = fieldAnalysis.getObjectFlow();
            if (objectFlow != null && objectFlow.location.info == fieldInfo) {
                result.add(objectFlow);
            }
            result.addAll(fieldAnalysis.getInternalObjectFlows());
        }
        for (TypeInfo subType : typeInspection.get().subTypes()) {
            result.addAll(subType.objectFlows(analysisProvider));
        }
        return result;
    }

    public boolean isEventual() {
        return typeAnalysis.get().isEventual();
    }

    public MethodInfo findUniqueMethod(String methodName, int parameters) {
        return typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM).
                filter(m -> m.name.equals(methodName) && m.methodInspection.get().getParameters().size() == parameters)
                .findAny().orElseThrow();
    }

    public Set<ParameterizedType> typesOfMethodsAndConstructors() {
        Set<ParameterizedType> result = new HashSet<>();
        for (MethodInfo methodInfo : typeInspection.get().methodsAndConstructors()) {
            if (!methodInfo.isConstructor && !methodInfo.isVoid()) {
                result.add(methodInfo.returnType());
            }
            for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().getParameters()) {
                result.add(parameterInfo.parameterizedType);
            }
        }
        return result;
    }

    public String packageName() {
        if (packageNameOrEnclosingType.isLeft())
            return packageNameOrEnclosingType.getLeft();
        return packageNameOrEnclosingType.getRight().packageName();
    }

    // this type implements a functional interface, and we need to find the single abstract method
    public MethodInfo findOverriddenSingleAbstractMethod() {
        return typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .map(mi -> mi.methodInspection.get())
                .filter(mi -> !mi.isDefault() && !mi.isStatic())
                .findFirst().orElseThrow().getMethodInfo();
    }

    public MethodInfo findConstructor(int parameters) {
        return typeInspection.get().constructors().stream()
                .filter(c -> c.methodInspection.get().getParameters().size() == parameters)
                .findFirst().orElseThrow();
    }

    public boolean isPrimaryType() {
        return packageNameOrEnclosingType.isLeft();
    }

    /**
     * Analysis
     *
     * @return true when we can completely bypass the analysers using the "copyAnnotationsIntoTypeAnalysisProperties" method
     */
    public boolean shallowAnalysis() {
        if (!typeInspection.isSet()) throw new UnsupportedOperationException();
        TypeInspection inspection = typeInspection.get();
        // we don't analyse annotations at the moment
        if (inspection.typeNature() == TypeNature.ANNOTATION) return true;
        return inspection.methodsAndConstructors(TypeInspection.Methods.INCLUDE_SUBTYPES).allMatch(MethodInfo::shallowAnalysis);
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return typeInspection.get().typesReferenced();
    }
}
