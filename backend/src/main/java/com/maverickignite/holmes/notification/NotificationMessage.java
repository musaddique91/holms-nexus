package com.maverickignite.holmes.notification;

import java.util.List;

/** Everything a channel needs to describe an analysed incident. */
public record NotificationMessage(
    long incidentId,
    String title,
    String service,
    String environment,
    String severity,
    long occurrences,
    String summary,
    String rootCause,
    String suggestedFix,
    String confidence,
    String fixStatus,
    String prUrl,
    String fixDescription,
    String dashboardLink,
    String slackChannel,
    List<String> emailRecipients,
    String telegramChatId) {}
