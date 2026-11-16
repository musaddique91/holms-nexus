package com.maverickignite.holmes.notification;

import com.maverickignite.holmes.notification.NotificationSettingsService.Config;

public interface Notifier {

  String channel();

  /** Why this channel cannot deliver with {@code config}, or null when it can (ignores the on/off switch). */
  String missing(Config config);

  /** Whether the channel is switched on in {@code config}. */
  boolean switchedOn(Config config);

  void send(NotificationMessage message, Config config) throws Exception;
}
