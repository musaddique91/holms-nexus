package com.maverickignite.holmes.notification;

import com.maverickignite.holmes.incident.Severity;
import com.maverickignite.holmes.notification.NotificationSettingsService.Config;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Fans a message out to every channel switched on in Settings; one failing channel does not block
 * the others. Incidents below the configured minimum severity are not sent at all.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final List<Notifier> notifiers;
  private final NotificationSettingsService settings;

  public List<String> send(NotificationMessage message) {
    Config config = settings.current();
    Severity severity = Severity.parse(message.severity(), Severity.HIGH);
    if (severity.ordinal() > config.minSeverity().ordinal()) {
      log.info(
          "Incident #{} is {} (below {}); not notified",
          message.incidentId(), severity, config.minSeverity());
      return List.of();
    }
    return notifiers.stream()
        .filter(n -> active(n, config))
        .filter(
            n -> {
              try {
                n.send(message, config);
                return true;
              } catch (Exception e) {
                log.warn("{} notification for incident #{} failed: {}", n.channel(), message.incidentId(), e.getMessage());
                return false;
              }
            })
        .map(Notifier::channel)
        .toList();
  }

  public List<String> enabledChannels() {
    Config config = settings.current();
    return notifiers.stream().filter(n -> active(n, config)).map(Notifier::channel).toList();
  }

  /** Per-channel state for the Settings page. */
  public record ChannelStatus(String channel, boolean switchedOn, String missing) {}

  public List<ChannelStatus> status(Config config) {
    return notifiers.stream()
        .map(n -> new ChannelStatus(n.channel(), n.switchedOn(config), n.missing(config)))
        .toList();
  }

  /**
   * Sends {@code message} through one channel using {@code config} even if the channel is
   * switched off, so it can be checked before it is turned on.
   */
  public void test(String channel, NotificationMessage message, Config config) throws Exception {
    Notifier notifier =
        notifiers.stream()
            .filter(n -> n.channel().equals(channel))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown channel " + channel));
    String missing = notifier.missing(config);
    if (missing != null) {
      throw new IllegalStateException(missing);
    }
    notifier.send(message, config);
  }

  private static boolean active(Notifier n, Config config) {
    return n.switchedOn(config) && n.missing(config) == null;
  }
}
