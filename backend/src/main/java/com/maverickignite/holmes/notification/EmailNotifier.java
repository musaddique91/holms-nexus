package com.maverickignite.holmes.notification;

import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.notification.NotificationSettingsService.Config;
import jakarta.mail.internet.MimeMessage;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** HTML e-mail over the SMTP server configured on the Settings page. */
@Component
class EmailNotifier implements Notifier {

  /** One SMTP client per saved settings version; previews (version 0) get a throwaway one. */
  private final AtomicReference<CachedSender> sender = new AtomicReference<>();

  private record CachedSender(long version, JavaMailSender sender) {}

  @Override
  public String channel() {
    return "email";
  }

  @Override
  public boolean switchedOn(Config c) {
    return c.email().enabled();
  }

  @Override
  public String missing(Config c) {
    NotificationSettingsService.Email e = c.email();
    if (Texts.isBlank(e.smtpHost())) {
      return "Add the SMTP server";
    }
    if (Texts.isBlank(e.from())) {
      return "Add a From address";
    }
    return null;
  }

  @Override
  public void send(NotificationMessage m, Config c) throws Exception {
    NotificationSettingsService.Email config = c.email();
    Set<String> to = new LinkedHashSet<>(config.to());
    if (m.emailRecipients() != null) {
      to.addAll(m.emailRecipients());
    }
    if (to.isEmpty()) {
      throw new IllegalStateException("No e-mail recipients configured for " + m.service());
    }
    JavaMailSender mail = senderFor(c);
    MimeMessage mime = mail.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
    helper.setFrom(config.from());
    helper.setTo(to.toArray(String[]::new));
    helper.setSubject(
        Texts.truncate("[" + m.severity() + "] " + m.service() + ": " + m.title(), 180));
    helper.setText(html(m), true);
    mail.send(mime);
  }

  private JavaMailSender senderFor(Config c) {
    if (c.version() == 0) {
      return build(c.email());
    }
    CachedSender cached = sender.get();
    if (cached == null || cached.version() != c.version()) {
      cached = new CachedSender(c.version(), build(c.email()));
      sender.set(cached);
    }
    return cached.sender();
  }

  static JavaMailSenderImpl build(NotificationSettingsService.Email e) {
    JavaMailSenderImpl s = new JavaMailSenderImpl();
    s.setHost(e.smtpHost());
    s.setPort(e.smtpPort());
    s.setDefaultEncoding("UTF-8");
    Properties props = s.getJavaMailProperties();
    if (!Texts.isBlank(e.smtpUsername())) {
      s.setUsername(e.smtpUsername());
      s.setPassword(e.smtpPassword());
      props.put("mail.smtp.auth", "true");
    }
    if (e.smtpPort() == 465) {
      props.put("mail.smtp.ssl.enable", "true");
    } else if (e.startTls()) {
      props.put("mail.smtp.starttls.enable", "true");
      props.put("mail.smtp.starttls.required", "true");
    }
    props.put("mail.smtp.connectiontimeout", "10000");
    props.put("mail.smtp.timeout", "15000");
    props.put("mail.smtp.writetimeout", "15000");
    return s;
  }

  private static String html(NotificationMessage m) {
    StringBuilder sb = new StringBuilder();
    sb.append("<div style=\"font-family:system-ui,sans-serif;max-width:720px\">");
    sb.append("<h2 style=\"margin:0 0 8px\">").append(h(m.title())).append("</h2>");
    sb.append("<p style=\"color:#555;margin:0 0 16px\">")
        .append(h(m.service())).append(m.environment() == null ? "" : " · " + h(m.environment()))
        .append(" · severity ").append(h(m.severity()))
        .append(" · ").append(m.occurrences()).append(" occurrences</p>");
    row(sb, "Summary", m.summary());
    row(sb, "Root cause", m.rootCause());
    if (m.prUrl() != null) {
      sb.append("<h3>Fix pull request</h3><p><a href=\"").append(h(m.prUrl())).append("\">")
          .append(h(m.prUrl())).append("</a></p>");
      row(sb, "What the fix changes", m.fixDescription());
    } else {
      row(sb, "Suggested fix", m.suggestedFix());
      if (m.fixStatus() != null) {
        sb.append("<p><em>Auto-fix: ").append(h(m.fixStatus())).append("</em></p>");
      }
    }
    sb.append("<p><a href=\"").append(h(m.dashboardLink()))
        .append("\" style=\"display:inline-block;padding:8px 14px;background:#1f6feb;color:#fff;border-radius:6px;text-decoration:none\">Open incident</a></p>");
    sb.append("<p style=\"color:#888;font-size:12px\">Sent by holms-nexus</p></div>");
    return sb.toString();
  }

  private static void row(StringBuilder sb, String label, String value) {
    if (!Texts.isBlank(value)) {
      sb.append("<h3 style=\"margin:16px 0 4px\">").append(label).append("</h3>")
          .append("<p style=\"white-space:pre-wrap;margin:0\">").append(h(value)).append("</p>");
    }
  }

  private static String h(String s) {
    return s == null ? "" : HtmlUtils.htmlEscape(s);
  }
}
