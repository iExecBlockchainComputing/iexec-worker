package com.iexec.worker.utils;

import java.lang.reflect.Field;

public class ReflectionUtils {
    public static <R, T> R getFieldAndSetAccessible(T object, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        final Field field = getFieldOnClassOrSuperClasses(object.getClass(), fieldName);
        field.setAccessible(true);
        return (R) field.get(object);
    }

    private static <R> R getFieldOnClassOrSuperClasses(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return (R) clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz != Object.class) {
                return getFieldOnClassOrSuperClasses(clazz.getSuperclass(), fieldName);
            }
            throw e;
        }
    }
}
