package io.helios.repository;

public class StoreException extends RuntimeException {
    public static final String NOT_FOUND = "not found";
    public static final String CONFLICT = "conflict";
    public static final String INVALID = "invalid";
    public static final String INVALID_REFERENCE = "invalid reference";

    private final String code;

    public StoreException(String code, String message) {
        super(message);
        this.code = code;
    }

    public StoreException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean isNotFound() {
        return NOT_FOUND.equals(code);
    }

    public boolean isConflict() {
        return CONFLICT.equals(code);
    }

    public boolean isInvalid() {
        return INVALID.equals(code);
    }

    public static StoreException notFound() {
        return new StoreException(NOT_FOUND, NOT_FOUND);
    }

    public static StoreException conflict(Throwable cause) {
        return new StoreException(CONFLICT, CONFLICT, cause);
    }

    public static StoreException invalid(String detail) {
        return new StoreException(INVALID, INVALID + ": " + detail);
    }
}
