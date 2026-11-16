package com.maverickignite.holmes.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FingerprinterTest {

  private final Fingerprinter fingerprinter = new Fingerprinter();

  private static final String TRACE_A =
      """
      org.springframework.dao.DataIntegrityViolationException: could not execute statement
      \tat org.springframework.orm.jpa.vendor.HibernateJpaDialect.convert(HibernateJpaDialect.java:290)
      \tat com.mshiba.backend.order.OrderService.place(OrderService.java:88)
      \tat com.mshiba.backend.order.OrderController.create(OrderController.java:41)
      Caused by: org.postgresql.util.PSQLException: ERROR: duplicate key value violates unique constraint
      \tat org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2733)
      \t... 42 more
      """;

  @Test
  void sameErrorWithDifferentLineNumbersAndIdsGroupsTogether() {
    String shifted = TRACE_A.replace("OrderService.java:88", "OrderService.java:97");
    var a = fingerprinter.fingerprint("orders-api", "prod", "Order 123 failed", TRACE_A);
    var b = fingerprinter.fingerprint("orders-api", "prod", "Order 456 failed", shifted);
    assertThat(a.hash()).isEqualTo(b.hash());
  }

  @Test
  void usesRootCauseTypeAndApplicationFramesInTitle() {
    var fp = fingerprinter.fingerprint("orders-api", "prod", "Order failed", TRACE_A);
    assertThat(fp.errorType()).isEqualTo("org.postgresql.util.PSQLException");
    assertThat(fp.title()).startsWith("PSQLException: Order failed");
  }

  @Test
  void differentServiceOrEnvironmentIsADifferentIncident() {
    var a = fingerprinter.fingerprint("orders-api", "prod", "x", TRACE_A);
    assertThat(fingerprinter.fingerprint("payments-api", "prod", "x", TRACE_A).hash()).isNotEqualTo(a.hash());
    assertThat(fingerprinter.fingerprint("orders-api", "dev", "x", TRACE_A).hash()).isNotEqualTo(a.hash());
  }

  @Test
  void messageOnlyErrorsAreNormalised() {
    var a =
        fingerprinter.fingerprint(
            "inventory-api", null, "Payment 8812 for user a@b.com failed with id 3fa85f64-5717-4562-b3fc-2c963f66afa6", null);
    var b =
        fingerprinter.fingerprint(
            "inventory-api", null, "Payment 17 for user z@y.org failed with id 00000000-0000-0000-0000-000000000000", null);
    assertThat(a.hash()).isEqualTo(b.hash());
    assertThat(Fingerprinter.normalize("Timeout after 3000 ms on 'orders'"))
        .isEqualTo("Timeout after <n> ms on <str>");
  }
}
