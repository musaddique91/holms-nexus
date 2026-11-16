package com.maverickignite.holmes.toolset.builtin;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maverickignite.holmes.toolset.ToolException;
import org.bson.Document;
import org.junit.jupiter.api.Test;

class MongoToolsetTest {

  @Test
  void rejectsServerSideJavascriptEvenWhenEscaped() {
    assertThatThrownBy(() -> MongoToolset.rejectUnsafeOperators(Document.parse("{\"$where\": \"sleep(1)\"}")))
        .isInstanceOf(ToolException.class);
    assertThatThrownBy(
            () -> MongoToolset.rejectUnsafeOperators(Document.parse("{\"\\u0024where\": \"sleep(1)\"}")))
        .isInstanceOf(ToolException.class);
    assertThatThrownBy(
            () -> MongoToolset.rejectUnsafeOperators(Document.parse("{\"a\": {\"$expr\": {\"$function\": {}}}}")))
        .isInstanceOf(ToolException.class);
  }

  @Test
  void allowsOrdinaryQueryOperators() {
    assertThatCode(
            () ->
                MongoToolset.rejectUnsafeOperators(
                    Document.parse("{\"status\": {\"$in\": [\"FAILED\"]}, \"$or\": [{\"n\": {\"$gt\": 1}}]}")))
        .doesNotThrowAnyException();
  }
}
