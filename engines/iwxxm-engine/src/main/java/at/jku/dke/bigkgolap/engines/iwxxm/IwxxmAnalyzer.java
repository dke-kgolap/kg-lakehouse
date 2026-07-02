package at.jku.dke.bigkgolap.engines.iwxxm;

import at.jku.dke.bigkgolap.engine.Analyzer;
import at.jku.dke.bigkgolap.engine.AnalyzerResult;
import at.jku.dke.bigkgolap.engines.iwxxm.internal.IwxxmConstants;
import at.jku.dke.bigkgolap.engines.iwxxm.internal.IwxxmContextExtractor;
import at.jku.dke.bigkgolap.engines.iwxxm.internal.IwxxmReport;
import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.Hierarchy;
import at.jku.dke.bigkgolap.model.HierarchyFactory;
import at.jku.dke.bigkgolap.model.Level;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IwxxmAnalyzer implements Analyzer {

  private static final Logger log = LoggerFactory.getLogger(IwxxmAnalyzer.class);

  private static final String DIM_TIME = "time";
  private static final String DIM_LOCATION = "location";
  private static final String DIM_TOPIC = "topic";

  @Override
  public AnalyzerResult analyze(InputStream input, CubeSchema schema) {
    var extractor = new IwxxmContextExtractor();
    extractor.parse(input);
    IwxxmReport report = extractor.report();
    if (report == null) return AnalyzerResult.EMPTY;

    if (report.topic() == null) return AnalyzerResult.EMPTY;
    if (!IwxxmConstants.MVP_ROOTS.contains(report.topic())) {
      log.warn("IWXXM root '{}' not yet indexed; file stored but not queryable", report.topic());
      return AnalyzerResult.EMPTY;
    }

    var contexts = contextsFor(report, schema);
    return new AnalyzerResult(contexts, metadataOf(report));
  }

  private List<Map<String, Hierarchy>> contextsFor(IwxxmReport report, CubeSchema schema) {
    var location = locationHierarchy(report, schema);
    var topic = topicHierarchy(report, schema);
    var timeAnchors = timeHierarchies(report, schema);

    var result = new ArrayList<Map<String, Hierarchy>>();
    for (var time : timeAnchors) {
      var ctx = new HashMap<String, Hierarchy>();
      if (time != null) ctx.put(DIM_TIME, time);
      if (location != null) ctx.put(DIM_LOCATION, location);
      if (topic != null) ctx.put(DIM_TOPIC, topic);
      if (!ctx.isEmpty()) result.add(Map.copyOf(ctx));
    }
    return result;
  }

  private List<Hierarchy> timeHierarchies(IwxxmReport report, CubeSchema schema) {
    Level dayLevel = schema.locate(DIM_TIME, "day");
    if (dayLevel == null) {
      return java.util.Collections.singletonList(null);
    }
    LocalDate from = report.validFrom();
    LocalDate until = report.validUntil();
    if (from == null && until == null) {
      return List.of(Hierarchy.all(DIM_TIME));
    }
    LocalDate effFrom = from != null ? from : until;
    LocalDate effUntil = until != null ? until : from;
    var days = new ArrayList<Hierarchy>();
    LocalDate d = effFrom;
    while (!d.isAfter(effUntil)) {
      days.add(HierarchyFactory.create(dayLevel, d.toString(), schema));
      d = d.plusDays(1);
    }
    return days;
  }

  private Hierarchy locationHierarchy(IwxxmReport report, CubeSchema schema) {
    Level locationLevel = schema.locate(DIM_LOCATION, "location");
    Level firLevel = schema.locate(DIM_LOCATION, "fir");
    String code = report.location();
    if (code == null || code.isEmpty()) return null;
    if (IwxxmConstants.ROOT_SIGMET.equals(report.topic())) {
      return firLevel != null
          ? HierarchyFactory.create(firLevel, code, schema)
          : Hierarchy.all(DIM_LOCATION);
    } else {
      return locationLevel != null
          ? HierarchyFactory.create(locationLevel, code, schema)
          : Hierarchy.all(DIM_LOCATION);
    }
  }

  private Hierarchy topicHierarchy(IwxxmReport report, CubeSchema schema) {
    Level featureLevel = schema.locate(DIM_TOPIC, "feature");
    if (featureLevel == null) return null;
    String topic = report.topic();
    if (topic == null || topic.isEmpty()) return null;
    return HierarchyFactory.create(featureLevel, topic, schema);
  }

  private Map<String, String> metadataOf(IwxxmReport report) {
    var map = new HashMap<String, String>();
    if (report.topic() != null) map.put("iwxxm.topic", report.topic());
    if (report.location() != null) map.put("iwxxm.location", report.location());
    if (report.validFrom() != null) map.put("iwxxm.validFrom", report.validFrom().toString());
    if (report.validUntil() != null) map.put("iwxxm.validUntil", report.validUntil().toString());
    return Map.copyOf(map);
  }
}
