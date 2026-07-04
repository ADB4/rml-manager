package com.adb4.rmlmanager.exception;

public class ResourceInUseException extends RuntimeException {
    public ResourceInUseException(String resourceName, String dependentResource) {
        super(String.format("Cannot delete %s because it has associated %s", resourceName, dependentResource));
    }
}