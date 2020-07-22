package org.e2immu.analyser.parser;

import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.FieldReference;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.FieldAccess;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.util.Lazy;
import org.e2immu.annotation.*;

import java.util.List;
import java.util.Objects;

public class E2ImmuAnnotationExpressions {

    private final TypeStore typeStore;

    public E2ImmuAnnotationExpressions(TypeStore typeStore) {
        this.typeStore = typeStore;
    }

    public final Lazy<AnnotationExpression> beforeImmutableMark = new Lazy<>(() -> create(BeforeImmutableMark.class));
    public final Lazy<AnnotationExpression> beforeNotNullMark = new Lazy<>(() -> create(BeforeNotNullMark.class));
    public final Lazy<AnnotationExpression> constant = new Lazy<>(() -> create(Constant.class));
    public final Lazy<AnnotationExpression> container = new Lazy<>(() -> create(Container.class));
    public final Lazy<AnnotationExpression> dependent = new Lazy<>(() -> create(Dependent.class));
    public final Lazy<AnnotationExpression> e1Container = new Lazy<>(() -> create(E1Container.class));
    public final Lazy<AnnotationExpression> e2Container = new Lazy<>(() -> create(E2Container.class));
    public final Lazy<AnnotationExpression> extensionClass = new Lazy<>(() -> create(ExtensionClass.class));
    public final Lazy<AnnotationExpression> e1Immutable = new Lazy<>(() -> create(E1Immutable.class));
    public final Lazy<AnnotationExpression> e2Immutable = new Lazy<>(() -> create(E2Immutable.class));
    public final Lazy<AnnotationExpression> effectivelyFinal = new Lazy<>(() -> create(Final.class));
    public final Lazy<AnnotationExpression> fluent = new Lazy<>(() -> create(Fluent.class));
    public final Lazy<AnnotationExpression> identity = new Lazy<>(() -> create(Identity.class));
    public final Lazy<AnnotationExpression> ignoreModifications = new Lazy<>(() -> create(IgnoreModifications.class));
    public final Lazy<AnnotationExpression> independent = new Lazy<>(() -> create(Independent.class));
    public final Lazy<AnnotationExpression> linked = new Lazy<>(() -> create(Linked.class));
    public final Lazy<AnnotationExpression> mark = new Lazy<>(() -> create(Mark.class));
    public final Lazy<AnnotationExpression> modified = new Lazy<>(() -> create(Modified.class));
    public final Lazy<AnnotationExpression> modifiesArguments = new Lazy<>(() -> create(ModifiesArguments.class));
    public final Lazy<AnnotationExpression> mutable = new Lazy<>(() -> create(Mutable.class));
    public final Lazy<AnnotationExpression> notModified = new Lazy<>(() -> create(NotModified.class));
    public final Lazy<AnnotationExpression> notNull = new Lazy<>(() -> create(NotNull.class));
    public final Lazy<AnnotationExpression> notNull1 = new Lazy<>(() -> create(NotNull1.class));
    public final Lazy<AnnotationExpression> notNull2 = new Lazy<>(() -> create(NotNull2.class));
    public final Lazy<AnnotationExpression> nullable = new Lazy<>(() -> create(Nullable.class));
    public final Lazy<AnnotationExpression> only = new Lazy<>(() -> create(Only.class));
    public final Lazy<AnnotationExpression> output = new Lazy<>(() -> create(Output.class));
    public final Lazy<AnnotationExpression> precondition = new Lazy<>(() -> create(Precondition.class));
    public final Lazy<AnnotationExpression> singleton = new Lazy<>(() -> create(Singleton.class));
    public final Lazy<AnnotationExpression> size = new Lazy<>(() -> create(Size.class));
    public final Lazy<AnnotationExpression> utilityClass = new Lazy<>(() -> create(UtilityClass.class));
    public final Lazy<AnnotationExpression> variableField = new Lazy<>(() -> create(Variable.class));

    /**
     * create an annotation for a given class, with a type=AnnotationType.COMPUTED parameter
     *
     * @param clazz must have a method called type of Enum type AnnotationType
     * @return an annotation expression
     */
    private AnnotationExpression create(Class<?> clazz) {
        TypeInfo annotationType = typeStore.get(AnnotationType.class.getCanonicalName());
        FieldInfo computed = Primitives.PRIMITIVES.annotationTypeComputed;
        FieldReference computedRef = new FieldReference(computed, null);
        FieldAccess computedAccess = new FieldAccess(new TypeExpression(annotationType.asParameterizedType()), computedRef);
        // NOTE: we've added an import statement in TypeInfo.imports() for this...
        return AnnotationExpression.fromAnalyserExpressions(typeStore.get(clazz.getCanonicalName()),
                List.of(new MemberValuePair("type", computedAccess)));
    }

    public TypeInfo getFullyQualified(String fqn) {
        return Objects.requireNonNull(typeStore.get(fqn));
    }
}
