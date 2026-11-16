package com.maverickignite.holmes.auth;

/** Accepts {@code {bcrypt}...}/{@code {noop}...} values, or a bare password treated as plain. */
final class SecurityPasswords {

  private SecurityPasswords() {}

  static String encoded(String configured) {
    if (configured == null) {
      return "{noop}";
    }
    return configured.startsWith("{") ? configured : "{noop}" + configured;
  }
}
