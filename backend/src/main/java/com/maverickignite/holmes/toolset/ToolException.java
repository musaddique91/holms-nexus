package com.maverickignite.holmes.toolset;

/** An error whose message is safe and useful to show the model (bad argument, not found...). */
public class ToolException extends RuntimeException {

  public ToolException(String message) {
    super(message);
  }
}
