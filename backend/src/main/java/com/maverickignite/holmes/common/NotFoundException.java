package com.maverickignite.holmes.common;

public class NotFoundException extends RuntimeException {

  public NotFoundException(String what, Object id) {
    super(what + " " + id + " not found");
  }
}
