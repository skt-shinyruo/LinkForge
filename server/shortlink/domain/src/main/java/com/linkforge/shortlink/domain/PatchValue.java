package com.linkforge.shortlink.domain;

import java.util.Objects;

/** 表达部分更新字段的未提供、设置和清空三种状态。 */
public record PatchValue<T>(Operation operation, T value) {

    public enum Operation {
        UNCHANGED,
        SET,
        CLEAR
    }

    public PatchValue {
        operation = Objects.requireNonNull(operation, "operation");
        if (operation == Operation.SET) {
            Objects.requireNonNull(value, "SET patch value");
        } else if (value != null) {
            throw new IllegalArgumentException(operation + " patch cannot carry a value");
        }
    }

    public static <T> PatchValue<T> unchanged() {
        return new PatchValue<>(Operation.UNCHANGED, null);
    }

    public static <T> PatchValue<T> set(T value) {
        return new PatchValue<>(Operation.SET, value);
    }

    public static <T> PatchValue<T> clear() {
        return new PatchValue<>(Operation.CLEAR, null);
    }

    public boolean isUnchanged() {
        return operation == Operation.UNCHANGED;
    }

    public boolean isSet() {
        return operation == Operation.SET;
    }

    public boolean isClear() {
        return operation == Operation.CLEAR;
    }
}
