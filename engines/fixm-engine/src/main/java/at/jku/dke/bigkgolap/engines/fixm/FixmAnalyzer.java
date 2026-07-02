package at.jku.dke.bigkgolap.engines.fixm;

import at.jku.dke.bigkgolap.engine.Analyzer;
import at.jku.dke.bigkgolap.engine.AnalyzerResult;
import at.jku.dke.bigkgolap.engines.fixm.internal.FixmConstants;
import at.jku.dke.bigkgolap.engines.fixm.internal.FixmContextExtractor;
import at.jku.dke.bigkgolap.engines.fixm.internal.FixmFlight;
import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.Hierarchy;
import at.jku.dke.bigkgolap.model.HierarchyFactory;
import at.jku.dke.bigkgolap.model.Level;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FixmAnalyzer implements Analyzer {

  private static final String DIM_TIME = "time";
  private static final String DIM_LOCATION = "location";
  private static final String DIM_TOPIC = "topic";

  @Override
  public AnalyzerResult analyze(InputStream input, CubeSchema schema) {
    var extractor = new FixmContextExtractor();
    extractor.parse(input);
    FixmFlight flight = extractor.flight();
    if (flight == null) return AnalyzerResult.EMPTY;
    if (flight.departureAerodrome() == null
        && flight.estimatedOffBlockTime() == null
        && flight.actualTimeOfArrival() == null) {
      return AnalyzerResult.EMPTY;
    }

    var location = locationHierarchy(flight, schema);
    var topic = topicHierarchy(schema);
    var timeAnchors = timeHierarchies(flight, schema);

    var contexts = new ArrayList<Map<String, Hierarchy>>();
    for (var time : timeAnchors) {
      var ctx = new HashMap<String, Hierarchy>();
      if (time != null) ctx.put(DIM_TIME, time);
      if (location != null) ctx.put(DIM_LOCATION, location);
      if (topic != null) ctx.put(DIM_TOPIC, topic);
      if (!ctx.isEmpty()) contexts.add(Map.copyOf(ctx));
    }

    return new AnalyzerResult(contexts, metadataOf(flight));
  }

  private List<Hierarchy> timeHierarchies(FixmFlight flight, CubeSchema schema) {
    Level dayLevel = schema.locate(DIM_TIME, "day");
    if (dayLevel == null) return Collections.singletonList(null);
    LocalDate from = flight.estimatedOffBlockTime();
    LocalDate until = flight.actualTimeOfArrival();
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

  private Hierarchy locationHierarchy(FixmFlight flight, CubeSchema schema) {
    Level locationLevel = schema.locate(DIM_LOCATION, "location");
    if (locationLevel == null) return null;
    String code = flight.departureAerodrome();
    return (code != null && !code.isEmpty())
        ? HierarchyFactory.create(locationLevel, code, schema)
        : Hierarchy.all(DIM_LOCATION);
  }

  private Hierarchy topicHierarchy(CubeSchema schema) {
    Level featureLevel = schema.locate(DIM_TOPIC, "feature");
    if (featureLevel == null) return null;
    return HierarchyFactory.create(featureLevel, FixmConstants.FIXM_TOPIC, schema);
  }

  private Map<String, String> metadataOf(FixmFlight flight) {
    var map = new HashMap<String, String>();
    if (flight.departureAerodrome() != null)
      map.put("fixm.departureAerodrome", flight.departureAerodrome());
    if (flight.estimatedOffBlockTime() != null)
      map.put("fixm.estimatedOffBlockTime", flight.estimatedOffBlockTime().toString());
    if (flight.actualTimeOfArrival() != null)
      map.put("fixm.actualTimeOfArrival", flight.actualTimeOfArrival().toString());
    return Map.copyOf(map);
  }
}
