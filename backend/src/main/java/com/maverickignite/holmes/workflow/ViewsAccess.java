package com.maverickignite.holmes.workflow;

import com.maverickignite.holmes.investigation.Investigation;
import com.maverickignite.holmes.investigation.ToolCallRecord;
import com.maverickignite.holmes.workflow.Views.InvestigationView;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

/** Public factory for views used outside this package. */
public final class ViewsAccess {

  private ViewsAccess() {}

  public static InvestigationView investigation(
      Investigation i, List<ToolCallRecord> calls, ObjectMapper json) {
    return InvestigationView.of(i, calls, json);
  }
}
