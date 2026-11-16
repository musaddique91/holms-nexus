package com.maverickignite.holmes.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TelegramNotifierTest {

  @Test
  void resolvesChatAndThreadIdFromVariousFormats() {
    // 1. Exact Telegram Web URL format from user
    var t1 = TelegramNotifier.parseTarget("https://web.telegram.org/a/#-1004432917613_10");
    assertThat(t1.chatId()).isEqualTo("-1004432917613");
    assertThat(t1.messageThreadId()).isEqualTo(10L);

    // 2. Exact t.me message link format from user
    var t2 = TelegramNotifier.parseTarget("https://t.me/c/4432917613/10/11");
    assertThat(t2.chatId()).isEqualTo("-1004432917613");
    assertThat(t2.messageThreadId()).isEqualTo(10L);

    // 3. chat_id:topic_id format
    var t3 = TelegramNotifier.parseTarget("-1004432917613:10");
    assertThat(t3.chatId()).isEqualTo("-1004432917613");
    assertThat(t3.messageThreadId()).isEqualTo(10L);

    // 4. chat_id_topic_id format
    var t4 = TelegramNotifier.parseTarget("-1004432917613_10");
    assertThat(t4.chatId()).isEqualTo("-1004432917613");
    assertThat(t4.messageThreadId()).isEqualTo(10L);

    // 5. Service has only topic ID (e.g. "10"), global default has chat_id
    var t5 = TelegramNotifier.resolveTarget("10", "-1004432917613");
    assertThat(t5.chatId()).isEqualTo("-1004432917613");
    assertThat(t5.messageThreadId()).isEqualTo(10L);

    // 6. Plain chat without topic
    var t6 = TelegramNotifier.resolveTarget(null, "-1004432917613");
    assertThat(t6.chatId()).isEqualTo("-1004432917613");
    assertThat(t6.messageThreadId()).isNull();

    // 7. Topic 1 (General topic link from user)
    var t7 = TelegramNotifier.parseTarget("https://t.me/c/4432917613/1/3");
    assertThat(t7.chatId()).isEqualTo("-1004432917613");
    assertThat(t7.messageThreadId()).isEqualTo(1L);
  }
}
