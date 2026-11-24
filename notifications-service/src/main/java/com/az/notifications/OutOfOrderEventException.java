package com.az.notifications;

public class OutOfOrderEventException extends RuntimeException {
  public OutOfOrderEventException(String message) {
    super(message);
  }
}
