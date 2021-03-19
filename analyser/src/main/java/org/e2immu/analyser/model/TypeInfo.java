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
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.Logger;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;
import org.e2immu.support.Either;
import org.e2immu.support.SetOnce;

import java.util.*;
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
    public Analysis getAnalysis() {
        return typeAnalysis.get();
    }

    @Override
    public boolean hasBeenAnalysed() {
        return typeAnalysis.isSet();
    }

    @Override
    public String name() {
        return simpleName;
    }

    public TypeInfo(String packageName, String simpleName) {
        assert packageName != null && !packageName.isBlank();
        assert simpleName != null && !simpleName.isBlank();

        this.simpleName = Objects.requireNonNull(simpleName);
        this.packageNameOrEnclosingType = Either.left(packageName);
        if (Primitives.JAVA_PRIMITIVE.equals(packageName)) {
            this.fullyQualifiedName = simpleName;
        } else {
            this.fullyQualifiedName = packageName + "." + simpleName;
        }
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

    @Override
    public boolean hasBeenInspected() {
        return typeInspection.isSet();
    }

    public OutputBuilder output() {
        assert isPrimaryType();
        return output(null, true);
    }

    public OutputBuilder output(Qualification qualification, boolean doTypeDeclaration) {
        String typeNature;
        Set<String> imports;
        QualificationImpl insideType;
        if (isPrimaryType() && hasBeenInspected()) {
            ResultOfImportComputation res = imports(typeInspection.get());
            imports = res.imports;
            insideType = res.qualification;
        } else {
            imports = Set.of();
            insideType = hasBeenInspected() ? new QualificationImpl(qualification) : new QualificationImpl();
        }
        assert insideType != null;

        String[] typeModifiers;
        List<FieldInfo> fields;
        List<MethodInfo> constructors;
        List<MethodInfo> methods;
        List<TypeInfo> subTypes;
        List<ParameterizedType> interfaces;
        List<TypeParameter> typeParameters;
        ParameterizedType parentClass;
        boolean isInterface;

        if (hasBeenInspected()) {
            TypeInspection typeInspection = this.typeInspection.get();
            typeNature = typeInspection.typeNature().toJava();
            isInterface = typeInspection.isInterface();
            typeModifiers = TypeModifier.sort(typeInspection.modifiers());
            fields = typeInspection.fields();
            constructors = typeInspection.constructors();
            methods = typeInspection.methods();
            subTypes = typeInspection.subTypes();
            typeParameters = typeInspection.typeParameters();
            parentClass = parentIsNotJavaLangObject() ? typeInspection.parentClass() : null;
            interfaces = typeInspection.interfacesImplemented();

            // add the methods that we can call without having to qualify (method() instead of super.method())
            addMethodsToQualification(insideType);
            addThisToQualification(insideType);
        } else {
            typeNature = "class"; // we really have no idea what it is
            typeModifiers = new String[]{"abstract"};
            fields = List.of();
            constructors = List.of();
            methods = List.of();
            subTypes = List.of();
            typeParameters = List.of();
            interfaces = List.of();
            parentClass = null;
            isInterface = false;
        }

        // PACKAGE AND IMPORTS

        OutputBuilder packageAndImports = new OutputBuilder();
        if (isPrimaryType()) {
            String packageName = packageNameOrEnclosingType.getLeftOrElse("");
            if (!packageName.isEmpty()) {
                packageAndImports.add(new Text("package")).add(Space.ONE).add(new Text(packageName)).add(Symbol.SEMICOLON)
                        .add(Space.NEWLINE);
            }
            if (!imports.isEmpty()) {
                imports.stream().sorted().forEach(i ->
                        packageAndImports.add(new Text("import")).add(Space.ONE).add(new Text(i)).add(Symbol.SEMICOLON)
                                .add(Space.NEWLINE));
            }
        }

        OutputBuilder afterAnnotations = new OutputBuilder();
        if (doTypeDeclaration) {
            // the class name
            afterAnnotations
                    .add(Arrays.stream(typeModifiers).map(mod -> new OutputBuilder().add(new Text(mod)))
                            .collect(OutputBuilder.joining(Space.ONE)))
                    .add(Space.ONE).add(new Text(typeNature))
                    .add(Space.ONE).add(new Text(simpleName));

            if (!typeParameters.isEmpty()) {
                afterAnnotations.add(Symbol.LEFT_ANGLE_BRACKET);
                afterAnnotations.add(typeParameters.stream().map(tp -> tp.output(insideType))
                        .collect(OutputBuilder.joining(Symbol.COMMA)));
                afterAnnotations.add(Symbol.RIGHT_ANGLE_BRACKET);
            }
            if (parentClass != null) {
                afterAnnotations.add(Space.ONE).add(new Text("extends")).add(Space.ONE).add(parentClass.output(insideType));
            }
            if (!interfaces.isEmpty()) {
                afterAnnotations.add(Space.ONE).add(new Text(isInterface ? "extends" : "implements")).add(Space.ONE);
                afterAnnotations.add(interfaces.stream().map(pi -> pi.output(insideType)).collect(OutputBuilder.joining(Symbol.COMMA)));
            }
        }

        Guide.GuideGenerator guideGenerator = Guide.generatorForBlock();
        OutputBuilder main = Stream.concat(Stream.concat(Stream.concat(Stream.concat(
                enumConstantStream(insideType),
                fields.stream()
                        .filter(f -> !f.fieldInspection.get().isSynthetic())
                        .map(f -> f.output(insideType))),
                subTypes.stream().map(ti -> ti.output(insideType, true))),
                constructors.stream()
                        .filter(c -> !c.methodInspection.get().isSynthetic()).map(c -> c.output(insideType, guideGenerator))),
                methods.stream()
                        .filter(m -> !m.methodInspection.get().isSynthetic())
                        .map(m -> m.output(insideType, guideGenerator))).collect(OutputBuilder.joining(Space.NONE,
                Symbol.LEFT_BRACE, Symbol.RIGHT_BRACE, guideGenerator));
        afterAnnotations.add(main);

        // annotations and the rest of the type are at the same level
        Stream<OutputBuilder> annotationStream = buildAnnotationOutput(insideType);
        return packageAndImports.add(Stream.concat(annotationStream, Stream.of(afterAnnotations))
                .collect(OutputBuilder.joining(Space.ONE_REQUIRED_EASY_SPLIT,
                        Guide.generatorForAnnotationList())));
    }

    private void addThisToQualification(QualificationImpl insideType) {
        insideType.addThis(new This(InspectionProvider.DEFAULT, this));
        if (!Primitives.isJavaLangObject(typeInspection.get().parentClass())) {
            insideType.addThis(new This(InspectionProvider.DEFAULT, typeInspection.get().parentClass().typeInfo,
                    false, true));
        }
    }

    private void addMethodsToQualification(QualificationImpl qImpl) {
        TypeInspection ti = typeInspection.get("Inspection of type " + fullyQualifiedName);
        ti.methods().forEach(qImpl::addMethodUnlessOverride);
        if (!Primitives.isJavaLangObject(this)) {
            ti.parentClass().typeInfo.addMethodsToQualification(qImpl);
        }
        for (ParameterizedType interfaceType : ti.interfacesImplemented()) {
            interfaceType.typeInfo.addMethodsToQualification(qImpl);
        }
    }

    private Stream<OutputBuilder> enumConstantStream(Qualification qualification) {
        if (typeInspection.get().typeNature() == TypeNature.ENUM) {
            Guide.GuideGenerator gg = Guide.generatorForEnumDefinitions();
            OutputBuilder outputBuilder = new OutputBuilder().add(gg.start());
            boolean first = true;
            for (FieldInfo fieldInfo : typeInspection.get().fields()) {
                if (fieldInfo.fieldInspection.get().isSynthetic()) {
                    if (first) {
                        first = false;
                    } else {
                        outputBuilder.add(Symbol.COMMA).add(gg.mid());
                    }
                    outputBuilder.add(new Text(fieldInfo.name));
                    FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().getFieldInitialiser();
                    Expression initialiser = fieldInitialiser == null ? null : fieldInitialiser.initialiser();
                    if (initialiser instanceof NewObject newObject) {
                        if (!newObject.parameterExpressions().isEmpty()) {
                            Guide.GuideGenerator args = Guide.defaultGuideGenerator();
                            outputBuilder.add(Symbol.LEFT_PARENTHESIS).add(args.start());
                            boolean firstParam = true;
                            for (Expression expression : newObject.parameterExpressions()) {
                                if (firstParam) {
                                    firstParam = false;
                                } else {
                                    outputBuilder.add(Symbol.COMMA).add(args.mid());
                                }
                                outputBuilder.add(expression.output(qualification));
                            }
                            outputBuilder.add(args.end()).add(Symbol.RIGHT_PARENTHESIS);
                        }
                    } else if (initialiser != null) {
                        throw new UnsupportedOperationException("Expect initialiser to be a NewObject");
                    }
                }
            }
            outputBuilder.add(gg.end()).add(Symbol.SEMICOLON);
            return Stream.of(outputBuilder);
        }
        return Stream.of();
    }

    public Set<FieldInfo> findFields(InspectionProvider inspectionProvider, String csv) {
        if (csv.isBlank()) {
            return Set.of();
        }
        List<FieldInfo> fields = visibleFields(inspectionProvider);
        return Arrays.stream(csv.split(",")).filter(s -> !s.isBlank()).map(s ->
                fields.stream().filter(f -> f.name.equals(s))
                        .findFirst()
                        .orElseThrow(() -> new UnsupportedOperationException("Cannot find field " + s + " in type " + fullyQualifiedName)))
                .collect(Collectors.toUnmodifiableSet());
    }

    private record ResultOfImportComputation(Set<String> imports, QualificationImpl qualification) {
    }

    private static class PerPackage {
        List<TypeInfo> types = new LinkedList<>();
        boolean allowStar = true;
    }

    private ResultOfImportComputation imports(TypeInspection typeInspection) {
        Set<TypeInfo> typesReferenced = typeInspection.typesReferenced().stream().filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .filter(Primitives::allowInImport)
                .collect(Collectors.toSet());
        Map<String, PerPackage> typesPerPackage = new HashMap<>();
        String myPackage = packageName();
        QualificationImpl qualification = new QualificationImpl();
        typesReferenced.forEach(typeInfo -> {
            String packageName = typeInfo.packageName();
            if (packageName != null && !myPackage.equals(packageName)) {
                boolean doImport = qualification.addTypeReturnImport(typeInfo);
                PerPackage perPackage = typesPerPackage.computeIfAbsent(packageName, p -> new PerPackage());
                if (doImport) {
                    perPackage.types.add(typeInfo);
                } else {
                    perPackage.allowStar = false; // because we don't want to play with complicated ordering
                }
            }
        });
        // IMPROVE static fields and methods
        Set<String> imports = new TreeSet<>();
        for (Map.Entry<String, PerPackage> e : typesPerPackage.entrySet()) {
            PerPackage perPackage = e.getValue();
            if (perPackage.types.size() >= 4 && perPackage.allowStar) {
                imports.add(e.getKey() + ".*");
            } else {
                for (TypeInfo typeInfo : perPackage.types) {
                    imports.add(typeInfo.fullyQualifiedName);
                }
            }
        }
        return new ResultOfImportComputation(imports, qualification);
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
        return complain ? result.orElseThrow(() -> new IllegalArgumentException("No field known with name " + name)) :
                result.orElse(null);
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
        TypeInfo typeInfo = new TypeInfo(enclosingType, expressionContext.anonymousTypeCounters.newIndex(expressionContext.primaryType));
        TypeInspectionImpl.Builder builder = new TypeInspectionImpl.Builder(typeInfo, BY_HAND);
        builder.setTypeNature(TypeNature.CLASS);
        builder.addInterfaceImplemented(functionalInterfaceType);
        builder.setParentClass(expressionContext.typeContext.getPrimitives().objectParameterizedType);

        // there are no extra type parameters; only those of the enclosing type(s) can be in 'type'

        MethodInspectionImpl.Builder methodBuilder = method.buildCopy(typeInfo);

        // compose the content of the method...
        MethodInspection methodReferenceInspection = expressionContext.typeContext.getMethodInspection(methodReference.methodInfo);
        Expression newReturnExpression;
        if (methodReferenceInspection.isStatic() || !(methodReference.scope instanceof TypeExpression)) {
            newReturnExpression = methodCallCopyAllParameters(methodReference.scope, methodReferenceInspection, methodBuilder);
        } else {
            if (methodBuilder.getParameters().size() != 1)
                throw new UnsupportedOperationException("Referenced method has multiple parameters");
            newReturnExpression = methodCallNoParameters(methodBuilder.getParameters().get(0), methodReferenceInspection);
        }
        Statement statement;
        if (methodBuilder.isVoid()) {
            statement = new ExpressionAsStatement(newReturnExpression);
        } else {
            statement = new ReturnStatement(newReturnExpression);
        }
        Block block = new Block.BlockBuilder().addStatement(statement).build();

        if (Logger.isLogEnabled(LAMBDA)) {
            log(LAMBDA, "Result of translating block: {}", block.output(Qualification.FULLY_QUALIFIED_NAME, null));
        }
        methodBuilder.setInspectedBlock(block).build(expressionContext.typeContext);
        MethodInfo methodInfo = methodBuilder.getMethodInfo();
        builder.addMethod(methodInfo);
        typeInfo.typeInspection.set(builder.build());
        expressionContext.addNewlyCreatedType(typeInfo);
        return methodInfo;
    }


    private Expression methodCallNoParameters(ParameterInfo firstParameter, MethodInspection concreteMethod) {
        Expression newScope = new VariableExpression(firstParameter);
        return new MethodCall(newScope, concreteMethod.getMethodInfo(), List.of(), ObjectFlow.NO_FLOW);
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
        // FIXME concreteTypes should be used somehow
        return new MethodCall(scope, concreteMethod.getMethodInfo(), parameterExpressions, ObjectFlow.NO_FLOW);
    }

    public boolean isNestedType() {
        return packageNameOrEnclosingType.isRight();
    }

    public boolean isPrivate() {
        return typeInspection.get().modifiers().contains(TypeModifier.PRIVATE);
    }

    public boolean isEnclosedIn(TypeInfo typeInfo) {
        if (typeInfo == this) return true;
        if (packageNameOrEnclosingType.isLeft()) return false;
        return packageNameOrEnclosingType.getRight().isEnclosedIn(typeInfo);
    }

    public boolean isPrivateNested() {
        return isNestedType() && isPrivate();
    }

    public boolean isPrivateOrEnclosingIsPrivate() {
        if (isPrivate()) return true;
        if (packageNameOrEnclosingType.isLeft()) return false;
        return packageNameOrEnclosingType.getRight().isPrivateOrEnclosingIsPrivate();
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


    public MethodInfo findConstructor(InspectionProvider inspectionProvider, List<Expression> parameterExpressions) {
        return findConstructor(inspectionProvider, parameterExpressions, true);
    }

    private MethodInfo findConstructor(InspectionProvider inspectionProvider,
                                       List<Expression> parameterExpressions,
                                       boolean allowPrivate) {
        MethodInfo constructor = typeInspection.get().constructors().stream()
                .filter(mi -> allowPrivate || !mi.isPrivate(inspectionProvider))
                .filter(mi -> compatibleParameters(inspectionProvider, parameterExpressions, mi))
                .findFirst().orElse(null);
        if (constructor != null) return constructor;
        TypeInspection inspection = inspectionProvider.getTypeInspection(this);
        if (inspection.parentClass() != null) {
            return inspection.parentClass().typeInfo.findConstructor(inspectionProvider, parameterExpressions, false);
        }
        throw new UnsupportedOperationException("Could not find correct constructor");
    }

    private boolean compatibleParameters(InspectionProvider inspectionProvider,
                                         List<Expression> parameterExpressions,
                                         MethodInfo methodInfo) {
        MethodInspection inspection = inspectionProvider.getMethodInspection(methodInfo);
        int nFormal = inspection.getParameters().size();
        if (nFormal == parameterExpressions.size() || nFormal > 0 && nFormal < parameterExpressions.size()
                && inspection.getParameters().get(nFormal - 1).parameterInspection.get().isVarArgs()) {
            int i = 0;
            for (ParameterInfo parameterInfo : inspection.getParameters()) {
                Expression expression = parameterExpressions.get(i);
                ParameterizedType formalType = parameterInfo.parameterizedType;
                ParameterizedType concreteType = expression.returnType();
                if (!formalType.isAssignableFrom(inspectionProvider, concreteType)) {
                    return false;
                }
                i++;
            }
            return true;
        }
        return false;
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
        if (!typeInspection.isSet()) return UpgradableBooleanMap.of(); // dangerous?
        return typeInspection.get("type inspection of " + fullyQualifiedName).typesReferenced();
    }

    public Map<NamedType, ParameterizedType> mapInTermsOfParametersOfSuperType(InspectionProvider inspectionProvider, TypeInfo superType) {
        assert superType != this;
        TypeInspection ti = inspectionProvider.getTypeInspection(this);
        if (ti.parentClass() != null) {
            if (ti.parentClass().typeInfo == superType) {
                return ti.parentClass().initialTypeParameterMap(inspectionProvider);
            }
            Map<NamedType, ParameterizedType> map = ti.parentClass().typeInfo.mapInTermsOfParametersOfSuperType(inspectionProvider, superType);
            if (map != null) {
                return combineMaps(ti.parentClass().initialTypeParameterMap(inspectionProvider), map);
            }
        }
        for (ParameterizedType implementedInterface : ti.interfacesImplemented()) {
            if (implementedInterface.typeInfo == superType) {
                return implementedInterface.initialTypeParameterMap(inspectionProvider);
            }
            Map<NamedType, ParameterizedType> map = implementedInterface.typeInfo.mapInTermsOfParametersOfSuperType(inspectionProvider, superType);
            if (map != null) {
                return combineMaps(implementedInterface.initialTypeParameterMap(inspectionProvider), map);
            }
        }
        return null; // not in this branch of the recursion
    }

    // practically the duplicate of the previous, except that we should parameterize initialTypeParameterMap as well to collapse them
    public Map<NamedType, ParameterizedType> mapInTermsOfParametersOfSubType(InspectionProvider inspectionProvider, TypeInfo superType) {
        assert superType != this;
        TypeInspection ti = inspectionProvider.getTypeInspection(this);
        if (ti.parentClass() != null) {
            if (ti.parentClass().typeInfo == superType) {
                return ti.parentClass().forwardTypeParameterMap(inspectionProvider);
            }
            Map<NamedType, ParameterizedType> map = ti.parentClass().typeInfo.mapInTermsOfParametersOfSubType(inspectionProvider, superType);
            if (map != null) {
                return combineMaps(map, ti.parentClass().forwardTypeParameterMap(inspectionProvider));
            }
        }
        for (ParameterizedType implementedInterface : ti.interfacesImplemented()) {
            if (implementedInterface.typeInfo == superType) {
                return implementedInterface.forwardTypeParameterMap(inspectionProvider);
            }
            Map<NamedType, ParameterizedType> map = implementedInterface.typeInfo.mapInTermsOfParametersOfSubType(inspectionProvider, superType);
            if (map != null) {
                return combineMaps(map, implementedInterface.forwardTypeParameterMap(inspectionProvider));
            }
        }
        return null; // not in this branch of the recursion
    }

    /*
    StringMap<V> -> HashMap<K,V> -> Map<K, V>

    M2: K(map) -> K(hashmap), M1: K(hashmap) -> String
     */
    public static Map<NamedType, ParameterizedType> combineMaps(Map<NamedType, ParameterizedType> m1, Map<NamedType, ParameterizedType> m2) {
        assert m1 != null;
        // if (m2.isEmpty()) return m1;
        // if (m1.isEmpty()) return m2;
        return m2.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> e.getValue().isTypeParameter() ? m1.getOrDefault(e.getValue().typeParameter, e.getValue()) : e.getValue(),
                (v1, v2) -> {
                    throw new UnsupportedOperationException();
                }, LinkedHashMap::new));
    }

    public int countEnumConstants() {
        assert typeInspection.get().typeNature() == TypeNature.ENUM;
        return (int) typeInspection.get().fields().stream().filter(fieldInfo -> fieldInfo.fieldInspection.get().isSynthetic()).count();
    }

    public String fromPrimaryTypeDownwards() {
        if (packageNameOrEnclosingType.isLeft()) {
            return simpleName;
        }
        return packageNameOrEnclosingType.getRight().fromPrimaryTypeDownwards() + "." + simpleName;
    }

    public List<FieldInfo> visibleFields(InspectionProvider inspectionProvider) {
        TypeInspection inspection = inspectionProvider.getTypeInspection(this);
        List<FieldInfo> locally = inspection.fields();
        List<FieldInfo> fromParent = Primitives.isJavaLangObject(this) ? List.of() :
                inspection.parentClass().typeInfo.visibleFields(inspectionProvider);
        List<FieldInfo> fromInterfaces = inspection.interfacesImplemented().stream()
                .flatMap(i -> i.typeInfo.visibleFields(inspectionProvider).stream()).collect(Collectors.toUnmodifiableList());
        return ListUtil.immutableConcat(locally, fromParent, fromInterfaces);
    }
}
