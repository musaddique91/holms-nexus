package com.maverickignite.holmes.common;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/** Request factories with explicit timeouts; a hung integration must never hang an agent. */
public final class HttpClients {

  private HttpClients() {}

  public static JdkClientHttpRequestFactory factory(Duration connect, Duration read) {
    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(connect)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(read);
    return factory;
  }

  public static JdkClientHttpRequestFactory standard() {
    return factory(Duration.ofSeconds(10), Duration.ofSeconds(30));
  }
}
