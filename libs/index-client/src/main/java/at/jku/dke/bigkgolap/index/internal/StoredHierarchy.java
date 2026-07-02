package at.jku.dke.bigkgolap.index.internal;

import at.jku.dke.bigkgolap.model.Hierarchy;
import java.util.Set;

public record StoredHierarchy(Hierarchy hierarchy, Set<String> associatedContexts) {}
