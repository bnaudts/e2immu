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

package org.e2immu.analyser.bytecode.asm;

import org.e2immu.analyser.annotationxml.AnnotationStore;
import org.e2immu.analyser.annotationxml.model.FieldItem;
import org.e2immu.analyser.annotationxml.model.MethodItem;
import org.e2immu.analyser.annotationxml.model.TypeItem;
import org.e2immu.analyser.bytecode.ExpressionFactory;
import org.e2immu.analyser.bytecode.JetBrainsAnnotationTranslator;
import org.e2immu.analyser.bytecode.OnDemandInspection;
import org.e2immu.analyser.inspector.FieldInspectionImpl;
import org.e2immu.analyser.inspector.MethodInspectionImpl;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.StringUtil;
import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.*;
import static org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR;
import static org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR_DEBUG;
import static org.e2immu.analyser.util.Logger.log;
import static org.objectweb.asm.Opcodes.ASM8;

public class MyClassVisitor extends ClassVisitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyClassVisitor.class);
    private static final Pattern ILLEGAL_IN_FQN = Pattern.compile("[/;$]");
    private final List<TypeInfo> types;
    private final TypeContext typeContext;
    private final OnDemandInspection onDemandInspection;
    private final AnnotationStore annotationStore;
    private final JetBrainsAnnotationTranslator jetBrainsAnnotationTranslator;
    private final Stack<TypeInfo> enclosingTypes;
    private TypeInfo currentType;
    private String currentTypePath;
    private boolean currentTypeIsInterface;
    private TypeInspectionImpl.Builder typeInspectionBuilder;

    public MyClassVisitor(OnDemandInspection onDemandInspection,
                          AnnotationStore annotationStore,
                          TypeContext typeContext,
                          List<TypeInfo> types,
                          Stack<TypeInfo> enclosingTypes) {
        super(ASM8);
        this.types = types;
        this.enclosingTypes = enclosingTypes;
        this.typeContext = typeContext;
        this.onDemandInspection = onDemandInspection;
        this.annotationStore = annotationStore;
        jetBrainsAnnotationTranslator = annotationStore != null ? new JetBrainsAnnotationTranslator(typeContext.getPrimitives(),
                typeContext.typeMapBuilder.getE2ImmuAnnotationExpressions()) : null;
    }

    // return true when child = parent + $ + somethingWithoutDollars
    static boolean isDirectChildOf(String child, String parent) {
        if (!child.startsWith(parent)) return false;
        int dollar = parent.length();
        if (child.length() <= dollar + 1) return false;
        int otherDollar = child.indexOf('$', dollar + 1);
        return otherDollar < 0;
    }

    public static String pathToFqn(String path) {
        return StringUtil.stripDotClass(path).replaceAll("[/$]", ".");
    }

    private static TypeNature typeNatureFromOpCode(int opCode) {
        if ((opCode & Opcodes.ACC_ANNOTATION) != 0) return TypeNature.ANNOTATION;
        if ((opCode & Opcodes.ACC_ENUM) != 0) return TypeNature.ENUM;
        if ((opCode & Opcodes.ACC_INTERFACE) != 0) return TypeNature.INTERFACE;
        return TypeNature.CLASS;
    }

    private static String makeMethodSignature(String name, TypeInfo typeInfo, List<ParameterizedType> types) {
        String methodName = "<init>".equals(name) ? typeInfo.simpleName : name;
        return methodName + "(" +
                types.stream().map(ParameterizedType::detailedString).collect(Collectors.joining(", ")) +
                ")";
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        log(BYTECODE_INSPECTOR_DEBUG, "Visit {} {} {} {} {} {}", version, access, name, signature, superName, interfaces);
        int dollar = name.indexOf('$');
        if (dollar >= 0 && enclosingTypes.isEmpty()) {
            currentType = null;
            currentTypePath = null;
            return;
        }
        String fqName = pathToFqn(name);
        currentType = typeContext.typeMapBuilder.get(fqName);
        if (currentType == null) {
            typeInspectionBuilder = typeContext.typeMapBuilder.getOrCreateFromPathReturnInspection(name, STARTING_BYTECODE);
            currentType = typeInspectionBuilder.typeInfo();
        } else {
            TypeInspection typeInspection = typeContext.typeMapBuilder.getTypeInspection(currentType);
            if (typeInspection != null) {
                if (typeInspection.getInspectionState().ge(FINISHED_BYTECODE)) {
                    log(BYTECODE_INSPECTOR_DEBUG, "Inspection of " + fqName + " has been set already");
                    types.add(currentType);
                    currentType = null;
                    currentTypePath = null;
                    return;
                }
                typeInspectionBuilder = (TypeInspectionImpl.Builder) typeInspection;
            } else {
                typeInspectionBuilder = typeContext.typeMapBuilder.ensureTypeInspection(currentType, STARTING_BYTECODE);
            }
        }
        typeInspectionBuilder.setInspectionState(STARTING_BYTECODE);
        currentTypePath = name;

        // may be overwritten, but this is the default UNLESS it's JLO itself
        if (!Primitives.isJavaLangObject(currentType)) {
            typeInspectionBuilder.setParentClass(typeContext.getPrimitives().objectParameterizedType);
        }

        TypeNature currentTypeNature = typeNatureFromOpCode(access);
        typeInspectionBuilder.setTypeNature(currentTypeNature);
        currentTypeIsInterface = currentTypeNature == TypeNature.INTERFACE;

        checkTypeFlags(access, typeInspectionBuilder);
        if (currentTypeNature == TypeNature.CLASS) {
            if ((access & Opcodes.ACC_ABSTRACT) != 0) typeInspectionBuilder.addTypeModifier(TypeModifier.ABSTRACT);
            if ((access & Opcodes.ACC_FINAL) != 0) typeInspectionBuilder.addTypeModifier(TypeModifier.FINAL);
        }

        String parentFqName = superName == null ? null : pathToFqn(superName);
        if (signature == null) {
            if (superName != null) {
                TypeInfo typeInfo = mustFindTypeInfo(parentFqName, superName);
                if (typeInfo == null) {
                    log(BYTECODE_INSPECTOR_DEBUG, "Stop inspection of {}, parent type {} unknown",
                            currentType.fullyQualifiedName, parentFqName);
                    errorStateForType(parentFqName);
                    return;
                }
                typeInspectionBuilder.setParentClass(typeInfo.asParameterizedType(typeContext));
            } else {
                log(BYTECODE_INSPECTOR_DEBUG, "No parent name for {}", fqName);
            }
            if (interfaces != null) {
                for (String interfaceName : interfaces) {
                    String fqn = pathToFqn(interfaceName);
                    TypeInfo typeInfo = mustFindTypeInfo(fqn, interfaceName);
                    if (typeInfo == null) {
                        log(BYTECODE_INSPECTOR_DEBUG, "Stop inspection of {}, interface type {} unknown",
                                currentType.fullyQualifiedName, fqn);
                        errorStateForType(fqn);
                        return;
                    }
                    typeInspectionBuilder.addInterfaceImplemented(typeInfo.asParameterizedType(typeContext));
                }
            }
        } else {
            try {
                int pos = 0;
                if (signature.charAt(0) == '<') {
                    ParseGenerics parseGenerics = new ParseGenerics(typeContext, currentType, typeInspectionBuilder, this::mustFindTypeInfo);
                    pos = parseGenerics.parseTypeGenerics(signature) + 1;
                }
                {
                    ParameterizedTypeFactory.Result res = ParameterizedTypeFactory.from(typeContext,
                            this::mustFindTypeInfo, signature.substring(pos));
                    if (res == null) {
                        log(BYTECODE_INSPECTOR_DEBUG, "Stop inspection of {}, parent type unknown",
                                currentType.fullyQualifiedName);
                        errorStateForType(parentFqName);
                        return;
                    }
                    typeInspectionBuilder.setParentClass(res.parameterizedType);
                    pos += res.nextPos;
                }
                if (interfaces != null) {
                    for (int i = 0; i < interfaces.length; i++) {
                        ParameterizedTypeFactory.Result interFaceRes = ParameterizedTypeFactory.from(typeContext,
                                this::mustFindTypeInfo, signature.substring(pos));
                        if (interFaceRes == null) {
                            log(BYTECODE_INSPECTOR_DEBUG, "Stop inspection of {}, interface type unknown",
                                    currentType.fullyQualifiedName);
                            errorStateForType(parentFqName);
                            return;
                        }
                        if (typeContext.getPrimitives().objectTypeInfo != interFaceRes.parameterizedType.typeInfo) {
                            typeInspectionBuilder.addInterfaceImplemented(interFaceRes.parameterizedType);
                        }
                        pos += interFaceRes.nextPos;
                    }
                }
            } catch (RuntimeException e) {
                LOGGER.error("Caught exception while parsing signature " + signature);
                throw e;
            }
        }

        if (annotationStore != null) {
            TypeItem typeItem = annotationStore.typeItemsByFQName(fqName);
            if (typeItem != null && !typeItem.getAnnotations().isEmpty()) {
                jetBrainsAnnotationTranslator.mapAnnotations(typeItem.getAnnotations(), typeInspectionBuilder);
            }
        }
    }

    /**
     * Both parameters are two versions of the same type reference
     *
     * @param fqn  dot-separated
     * @param path / and $ separated
     * @return the type
     */
    private TypeInfo mustFindTypeInfo(String fqn, String path) {
        if (path.equals(currentTypePath)) return currentType;
        TypeInfo alreadyKnown = typeContext.typeMapBuilder.get(fqn);
        TypeInspection alreadyKnownInspection = alreadyKnown == null ? null : typeContext.getTypeInspection(alreadyKnown);
        if (alreadyKnownInspection != null && alreadyKnownInspection.getInspectionState().ge(STARTING_BYTECODE)) {
            return Objects.requireNonNull(alreadyKnown);
        }

        // let's look at the super-name... is it part of the same primary type?
        TypeInfo parentType = enclosingTypes.isEmpty() ? null : enclosingTypes.peek();
        if (parentType != null && parentType.fullyQualifiedName.startsWith(fqn)) {
            // the parent is in the hierarchy of objects... we should definitely NOT inspect
            TypeInfo inHierarchy = inEnclosingTypes(fqn);
            if (inHierarchy != null) return inHierarchy;
        }
        if (parentType != null && isDirectChildOf(fqn, parentType.fullyQualifiedName)) {
            onDemandInspection.inspectFromPath(path, enclosingTypes, typeContext);
        } else {
            onDemandInspection.inspectFromPath(path);
        }
        // try again... result can be null or not inspected, in case the path is not on the classpath
        return typeContext.typeMapBuilder.get(fqn);
    }

    private TypeInfo getOrCreateTypeInfo(String fqn, String path) {
        Matcher m = ILLEGAL_IN_FQN.matcher(fqn);
        if (m.find()) throw new UnsupportedOperationException("Illegal FQN: " + fqn + "; path is " + path);
        // this causes really heavy recursions: return mustFindTypeInfo(fqn, path);
        return typeContext.typeMapBuilder.getOrCreateFromPath(path, TRIGGER_BYTECODE_INSPECTION);
    }

    private TypeInfo inEnclosingTypes(String parentFqName) {
        for (TypeInfo typeInfo : enclosingTypes) {
            if (typeInfo.fullyQualifiedName.equals(parentFqName)) return typeInfo;
        }
        // Example of this situation: java.util.Comparators$NullComparator is being parsed, but Comparator itself
        // has not been seen yet.
        log(BYTECODE_INSPECTOR_DEBUG, "Could not find " + parentFqName + " in stack of enclosing types " +
                enclosingTypes.stream().map(ti -> ti.fullyQualifiedName).collect(Collectors.joining(" -> ")));
        return null;
    }


    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if (currentType == null) return null;
        boolean synthetic = (access & Opcodes.ACC_SYNTHETIC) != 0;
        log(BYTECODE_INSPECTOR_DEBUG, "Field {} {} desc='{}' sig='{}' {} synthetic? {}", access,
                name, descriptor, signature, value, synthetic);
        if (synthetic) return null;

        ParameterizedType type = ParameterizedTypeFactory.from(typeContext,
                this::getOrCreateTypeInfo,
                signature != null ? signature : descriptor).parameterizedType;

        FieldInfo fieldInfo = new FieldInfo(type, name, currentType);
        FieldInspectionImpl.Builder fieldInspectionBuilder = new FieldInspectionImpl.Builder();
        typeContext.typeMapBuilder.registerFieldInspection(fieldInfo, fieldInspectionBuilder);

        if ((access & Opcodes.ACC_STATIC) != 0) fieldInspectionBuilder.addModifier(FieldModifier.STATIC);
        if ((access & Opcodes.ACC_PUBLIC) != 0) fieldInspectionBuilder.addModifier(FieldModifier.PUBLIC);
        if ((access & Opcodes.ACC_PRIVATE) != 0) fieldInspectionBuilder.addModifier(FieldModifier.PRIVATE);
        if ((access & Opcodes.ACC_PROTECTED) != 0) fieldInspectionBuilder.addModifier(FieldModifier.PROTECTED);
        if ((access & Opcodes.ACC_FINAL) != 0) fieldInspectionBuilder.addModifier(FieldModifier.FINAL);
        if ((access & Opcodes.ACC_VOLATILE) != 0) fieldInspectionBuilder.addModifier(FieldModifier.VOLATILE);

        if (value != null) {
            Expression expression = ExpressionFactory.from(typeContext, value);
            if (expression != EmptyExpression.EMPTY_EXPRESSION) {
                fieldInspectionBuilder.setInspectedInitialiserExpression(expression);
            }
        }

        if (annotationStore != null) {
            TypeItem typeItem = annotationStore.typeItemsByFQName(currentType.fullyQualifiedName);
            if (typeItem != null) {
                FieldItem fieldItem = typeItem.getFieldItems().get(name);
                if (fieldItem != null && !fieldItem.getAnnotations().isEmpty()) {
                    jetBrainsAnnotationTranslator.mapAnnotations(fieldItem.getAnnotations(), fieldInspectionBuilder);
                }
            }
        }

        return new MyFieldVisitor(typeContext, fieldInfo, fieldInspectionBuilder, typeInspectionBuilder);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (currentType == null) return null;

        if (name.startsWith("lambda$") || name.equals("<clinit>")) {
            return null;
        }

        boolean synthetic = (access & Opcodes.ACC_SYNTHETIC) != 0;
        log(BYTECODE_INSPECTOR_DEBUG, "Method {} {} desc='{}' sig='{}' {} synthetic? {}", access, name,
                descriptor, signature, Arrays.toString(exceptions), synthetic);
        if (synthetic) return null;

        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
        MethodInspectionImpl.Builder methodInspectionBuilder;
        if ("<init>".equals(name)) {
            methodInspectionBuilder = new MethodInspectionImpl.Builder(currentType);
        } else {
            methodInspectionBuilder = new MethodInspectionImpl.Builder(currentType, name);
            methodInspectionBuilder.setStatic(isStatic);
        }
        if ((access & Opcodes.ACC_PUBLIC) != 0 && !currentTypeIsInterface) {
            methodInspectionBuilder.addModifier(MethodModifier.PUBLIC);
        }
        if ((access & Opcodes.ACC_PRIVATE) != 0) methodInspectionBuilder.addModifier(MethodModifier.PRIVATE);
        if ((access & Opcodes.ACC_PROTECTED) != 0) methodInspectionBuilder.addModifier(MethodModifier.PROTECTED);
        if ((access & Opcodes.ACC_FINAL) != 0) methodInspectionBuilder.addModifier(MethodModifier.FINAL);
        boolean isAbstract = (access & Opcodes.ACC_ABSTRACT) != 0;
        if (currentTypeIsInterface && !isAbstract) {
            methodInspectionBuilder.addModifier(MethodModifier.DEFAULT);
        }
        if (isAbstract && (!currentTypeIsInterface || isStatic)) {
            methodInspectionBuilder.addModifier(MethodModifier.ABSTRACT);
        }
        boolean lastParameterIsVarargs = (access & Opcodes.ACC_VARARGS) != 0;

        TypeContext methodContext = new TypeContext(typeContext);
        ParseGenerics parseGenerics = new ParseGenerics(methodContext, currentType, typeInspectionBuilder, this::getOrCreateTypeInfo);

        String signatureOrDescription = signature != null ? signature : descriptor;
        if (signatureOrDescription.startsWith("<")) {
            int end = parseGenerics.parseMethodGenerics(signatureOrDescription, methodInspectionBuilder, methodContext);
            if (end < 0) {
                // error state
                errorStateForType(signatureOrDescription);
                return null; // dropping the method, and the type!
            }
            signatureOrDescription = signatureOrDescription.substring(end + 1); // 1 to get rid of >
        }
        List<ParameterizedType> types = parseGenerics.parseParameterTypesOfMethod(methodContext, signatureOrDescription);
        methodInspectionBuilder.setReturnType(types.get(types.size() - 1));

        MethodItem methodItem = null;
        if (annotationStore != null) {
            TypeItem typeItem = annotationStore.typeItemsByFQName(currentType.fullyQualifiedName);
            if (typeItem != null) {
                String methodSignature = makeMethodSignature(name, currentType, types.subList(0, types.size() - 1));
                methodItem = typeItem.getMethodItems().get(methodSignature);
                if (methodItem != null && !methodItem.getAnnotations().isEmpty()) {
                    jetBrainsAnnotationTranslator.mapAnnotations(methodItem.getAnnotations(), methodInspectionBuilder);
                }
            }
        }

        return new MyMethodVisitor(methodContext, methodInspectionBuilder, typeInspectionBuilder, types,
                lastParameterIsVarargs, methodItem, jetBrainsAnnotationTranslator);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        if (currentType == null) return;

        log(BYTECODE_INSPECTOR_DEBUG, "Visit inner class {} {} {} {}", name, outerName, innerName, access);
        if (name.equals(currentTypePath)) {
            checkTypeFlags(access, typeInspectionBuilder);
        } else if (innerName != null && outerName != null) {
            String fqnOuter = pathToFqn(outerName);
            boolean stepDown = currentTypePath.equals(outerName);
            boolean stepSide = currentType.packageNameOrEnclosingType.isRight() &&
                    currentType.packageNameOrEnclosingType.getRight().fullyQualifiedName.equals(fqnOuter);
            // step down
            if (stepSide || stepDown) {
                String fqn = fqnOuter + "." + innerName;

                log(BYTECODE_INSPECTOR_DEBUG, "Processing sub-type {} of/in {}", fqn, currentType.fullyQualifiedName);

                TypeInfo subTypeInMap = typeContext.typeMapBuilder.get(fqn);
                TypeInspectionImpl.Builder subTypeInspection;
                if (subTypeInMap == null) {
                    subTypeInMap = new TypeInfo(stepDown ? currentType : currentType.packageNameOrEnclosingType.getRight(), innerName);
                    subTypeInspection = typeContext.typeMapBuilder.add(subTypeInMap, TRIGGER_BYTECODE_INSPECTION);
                } else {
                    subTypeInspection = Objects.requireNonNull((TypeInspectionImpl.Builder) typeContext.getTypeInspection(subTypeInMap)); //MUST EXIST
                }
                if (subTypeInspection.getInspectionState().lt(STARTING_BYTECODE)) {
                    checkTypeFlags(access, subTypeInspection);
                    /* seems unnecessary
                    if(subTypeInspection.needsPackageOrEnclosing()) {
                       subTypeInspection.setEnclosingType(stepDown ? currentType: typeInspectionBuilder.packageNameOrEnclosingType().getRight());
                    }
                    */
                    if (stepDown) {
                        enclosingTypes.push(currentType);
                    }
                    TypeInfo subType = onDemandInspection.inspectFromPath(name, enclosingTypes, typeContext);
                    if (stepDown) {
                        enclosingTypes.pop();
                    }
                    if (subType != null) {
                        if (stepDown) {
                            typeInspectionBuilder.addSubType(subType);
                        }
                    } else {
                        errorStateForType(name);
                    }
                } else {
                    if (stepDown) {
                        typeInspectionBuilder.addSubType(subTypeInMap);
                    }
                }

            }
        }
    }

    private void checkTypeFlags(int access, TypeInspectionImpl.Builder typeInspectionBuilder) {
        if ((access & Opcodes.ACC_STATIC) != 0) typeInspectionBuilder.addTypeModifier(TypeModifier.STATIC);
        if ((access & Opcodes.ACC_PRIVATE) != 0) typeInspectionBuilder.addTypeModifier(TypeModifier.PRIVATE);
        if ((access & Opcodes.ACC_PROTECTED) != 0) typeInspectionBuilder.addTypeModifier(TypeModifier.PROTECTED);
        if ((access & Opcodes.ACC_PUBLIC) != 0) typeInspectionBuilder.addTypeModifier(TypeModifier.PUBLIC);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (currentType == null) return null;

        log(BYTECODE_INSPECTOR_DEBUG, "Have class annotation {} {}", descriptor, visible);
        return new MyAnnotationVisitor<>(typeContext, descriptor, typeInspectionBuilder);
    }

    // not overriding visitOuterClass

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        if (currentType == null) return null;

        log(BYTECODE_INSPECTOR_DEBUG, "Type annotation {} {} {} {}", typeRef, typePath, descriptor, visible);
        return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
    }

    @Override
    public void visitEnd() {
        if (currentType != null) {
            try {
                log(BYTECODE_INSPECTOR_DEBUG, "Visit end of class " + currentType.fullyQualifiedName);
                if (typeInspectionBuilder == null)
                    throw new UnsupportedOperationException("? was expecting a type inspection builder");
                typeInspectionBuilder.setInspectionState(FINISHED_BYTECODE);
                types.add(currentType);
                currentType = null;
                typeInspectionBuilder = null;
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught runtime exception bytecode inspecting type {}", currentType.fullyQualifiedName);
                throw rte;
            }
        }
    }

    private void errorStateForType(String pathCausingFailure) {
        if (currentType == null || currentType.typeInspection.isSet()) throw new UnsupportedOperationException();
        String message = "Unable to inspect " + currentType.fullyQualifiedName + ": Cannot load " + pathCausingFailure;
        typeInspectionBuilder.setInspectionState(FINISHED_BYTECODE);
        log(BYTECODE_INSPECTOR, message);
        currentType = null;
        typeInspectionBuilder = null;
    }

}
