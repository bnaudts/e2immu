package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.AnnotationType;
import org.e2immu.annotation.Size;

public class CheckSize {
    public static void checkSizeForMethods(TypeContext typeContext, MethodInfo methodInfo) {
        int size = methodInfo.methodAnalysis.get().getProperty(VariableProperty.SIZE);
        int sizeCopy = methodInfo.methodAnalysis.get().getProperty(VariableProperty.SIZE_COPY);
        AnnotationExpression optionalAnnotationExpression = methodInfo.hasTestAnnotation(Size.class).orElse(null);
        checkSize(typeContext, size, sizeCopy, optionalAnnotationExpression, new Location(methodInfo));
    }

    public static void checkSizeForFields(TypeContext typeContext, FieldInfo fieldInfo) {
        int size = fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.SIZE);
        AnnotationExpression optionalAnnotationExpression = fieldInfo.hasTestAnnotation(Size.class).orElse(null);
        checkSize(typeContext, size, -1, optionalAnnotationExpression, new Location(fieldInfo));
    }

    public static void checkSizeForParameters(TypeContext typeContext, ParameterInfo parameterInfo) {
        int size = parameterInfo.parameterAnalysis.get().getProperty(VariableProperty.SIZE);
        AnnotationExpression optionalAnnotationExpression = parameterInfo.hasTestAnnotation(Size.class).orElse(null);
        checkSize(typeContext, size, -1, optionalAnnotationExpression, new Location(parameterInfo));
    }

    private static void checkSize(TypeContext typeContext, int size, int sizeCopy, AnnotationExpression annotationExpression, Location where) {
        if (annotationExpression == null) return; // nothing to verify
        AnnotationType annotationType = annotationExpression.extract("type", null);
        boolean mustBeAbsent = annotationType == AnnotationType.VERIFY_ABSENT;

        if (mustBeAbsent && size != Level.DELAY) {
            typeContext.addMessage(Message.newMessage(where, Message.ANNOTATION_UNEXPECTEDLY_PRESENT, "Size"));
            return;
        }
        boolean copy = annotationExpression.extract("copy", false);
        if (copy) {
            if (sizeCopy != Level.TRUE_LEVEL_1) {
                typeContext.addMessage(Message.newMessage(where, Message.SIZE_COPY_MISSING));
            }
            return;
        }
        boolean copyMin = annotationExpression.extract("copyMin", false);
        if (copyMin) {
            if (sizeCopy != Level.TRUE) {
                typeContext.addMessage(Message.newMessage(where, Message.SIZE_COPY_MIN_MISSING));
            }
            return;
        }
        int sizeMin = annotationExpression.extract("min", -1);
        if (sizeMin >= 0) {
            if (!Analysis.haveEquals(size)) {
                int haveMin = Analysis.decodeSizeMin(size);
                if (haveMin != sizeMin) {
                    typeContext.addMessage(Message.newMessage(where, Message.SIZE_WRONG_MIN_VALUE,
                            "have " + haveMin + ", requires " + sizeMin));
                }
            } else {
                typeContext.addMessage(Message.newMessage(where, Message.SIZE_MIN_MISSING));
            }
            return;
        }
        int sizeEquals = annotationExpression.extract("equals", 0);
        if (Analysis.haveEquals(size)) {
            int haveEquals = Analysis.decodeSizeEquals(size);
            if (haveEquals != sizeEquals) {
                typeContext.addMessage(Message.newMessage(where, Message.SIZE_WRONG_EQUALS_VALUE,
                        "have " + haveEquals + ", requires " + sizeEquals));
            }
        } else {
            typeContext.addMessage(Message.newMessage(where, Message.SIZE_EQUALS_MISSING));
        }
    }
}
