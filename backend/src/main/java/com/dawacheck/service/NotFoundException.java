package com.dawacheck.service;

/** Becomes a 404 with the message shown to the user as written. */
public class NotFoundException extends RuntimeException {
  public NotFoundException(String message) {
    super(message);
  }
}
