package com.az.limits;

public record CheckResponse(boolean allowed, String reason) {

    public static CheckResponse approve() {
        return new CheckResponse(true, null);
    }

    public static CheckResponse deny(String reason) {
        return new CheckResponse(false, reason);
    }
}