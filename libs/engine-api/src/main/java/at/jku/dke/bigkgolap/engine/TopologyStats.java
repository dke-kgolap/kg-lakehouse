package at.jku.dke.bigkgolap.engine;

/** representation-specific topology statistics returned by {@link GraphBuilder#topologyStats()}. */
public record TopologyStats(long vertices, long edges) {}
