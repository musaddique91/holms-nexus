package com.maverickignite.holmes.toolset;

import com.maverickignite.holmes.toolset.ToolRegistry.ToolsetStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/toolsets")
@RequiredArgsConstructor
class ToolsetController {

  private final ToolRegistry registry;

  @GetMapping
  List<ToolsetStatus> list(@RequestParam(defaultValue = "false") boolean probe) {
    return registry.status(probe);
  }
}
