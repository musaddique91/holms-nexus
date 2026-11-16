package com.maverickignite.holmes.toolset;

import java.util.List;

/** Supplies toolsets that are built from data (YAML) rather than declared as beans. */
public interface ToolsetProvider {

  List<Toolset> toolsets();
}
