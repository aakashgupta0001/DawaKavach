package com.dawacheck.service;

/** Becomes a 400 with the message shown to the user as written. */
public class BadRequestException extends RuntimeException {
  public BadRequestException(String message) {
    super(message);
  }
}
