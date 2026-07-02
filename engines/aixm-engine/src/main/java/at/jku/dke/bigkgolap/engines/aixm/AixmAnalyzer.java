package at.jku.dke.bigkgolap.engines.aixm;

import at.jku.dke.bigkgolap.engine.Analyzer;
import at.jku.dke.bigkgolap.engine.AnalyzerResult;
import at.jku.dke.bigkgolap.engines.aixm.internal.AixmContextExtractor;
import at.jku.dke.bigkgolap.engines.aixm.internal.AixmFeature;
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
import java.util.Objects;
import java.util.stream.Collectors;

public class AixmAnalyzer implements Analyzer {

  private static final String DIM_TIME = "time";
  private static final String DIM_LOCATION = "location";
  private static final String DIM_TOPIC = "topic";

  @Override
  public AnalyzerResult analyze(InputStream input, CubeSchema schema) {
    var extractor = new AixmContextExtractor();
    extractor.parse(input);
    var features = extractor.features();
    var locationIndicator = extractor.locationIndicator();
    var firDesignator = extractor.firDesignator();

    var featureContexts = new ArrayList<Map<String, Hierarchy>>();
    for (var feature : features) {
      featureContexts.addAll(contextsFor(feature, locationIndicator, firDesignator, schema));
    }
    return new AnalyzerResult(featureContexts, metadataOf(features));
  }

  private List<Map<String, Hierarchy>> contextsFor(
      AixmFeature feature, String locationIndicator, String firDesignator, CubeSchema schema) {
    var location = locationHierarchy(feature, locationIndicator, firDesignator, schema);
    var topic = topicHierarchy(feature, schema);
    var timeAnchors = timeHierarchies(feature, schema);

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

  private List<Hierarchy> timeHierarchies(AixmFeature feature, CubeSchema schema) {
    Level dayLevel = schema.locate(DIM_TIME, "day");
    if (dayLevel == null) {
      return Collections.singletonList(null);
    }
    LocalDate begin = feature.beginPosition();
    LocalDate end = feature.endPosition();
    if (begin == null && end == null) {
      return List.of(Hierarchy.all(DIM_TIME));
    }
    LocalDate effBegin = begin != null ? begin : end;
    LocalDate effEnd = end != null ? end : begin;
    var days = new ArrayList<Hierarchy>();
    LocalDate d = effBegin;
    while (!d.isAfter(effEnd)) {
      days.add(HierarchyFactory.create(dayLevel, d.toString(), schema));
      d = d.plusDays(1);
    }
    return days;
  }

  private Hierarchy locationHierarchy(
      AixmFeature feature, String locationIndicator, String firDesignator, CubeSchema schema) {
    Level locationLevel = schema.locate(DIM_LOCATION, "location");
    Level firLevel = schema.locate(DIM_LOCATION, "fir");
    String loc = feature.location();
    String fir = feature.affectedFir();
    // Baseline features carry no event:location; fall back to the document's location.
    // NOTAM event tags win; then an airport baseline's ICAO (location level), then a
    // FIR baseline's FIR/UIR Airspace designator (fir level).
    if ((loc == null || loc.isEmpty()) && (fir == null || fir.isEmpty())) {
      if (locationIndicator != null && !locationIndicator.isEmpty()) {
        loc = locationIndicator;
      } else if (isKnownMember(schema, "fir", firDesignator)) {
        // Only trust the FIR designator if the schema recognises it. A non-conformant
        // generator may emit an arbitrary designator; degrade to the unlocated wildcard
        // rather than failing the whole file on an unknown rollup.
        fir = firDesignator;
      }
    }
    // ICAO NOTAM convention permits event:location to carry either an aerodrome ICAO
    // (scope=A) or a FIR designator (scope=E/W). Reclassify by schema membership so a
    // FIR-scoped NOTAM is indexed at the fir level rather than failing the location
    // lookup. An unknown code degrades to the unlocated wildcard for the same reason
    // as the FIR-baseline path above: do not poison the consumer on noisy input.
    if (loc != null && !loc.isEmpty() && !loc.equals(fir)) {
      if (isKnownMember(schema, "location", loc)) {
        // event:location really is an aerodrome ICAO — keep it.
      } else if (isKnownMember(schema, "fir", loc)) {
        // event:location actually carries a FIR designator; promote it.
        if (fir == null || fir.isEmpty()) {
          fir = loc;
        }
        loc = null;
      } else {
        // Unknown to the schema — drop it rather than triggering a hierarchy lookup
        // failure further down. Other dimensions still emit normally.
        loc = null;
      }
    }
    if (loc != null && !loc.isEmpty() && !loc.equals(fir) && locationLevel != null) {
      return HierarchyFactory.create(locationLevel, loc, schema);
    } else if (fir != null && !fir.isEmpty() && firLevel != null) {
      return HierarchyFactory.create(firLevel, fir, schema);
    } else if (locationLevel == null && firLevel == null) {
      return null;
    } else {
      return Hierarchy.all(DIM_LOCATION);
    }
  }

  private boolean isKnownMember(CubeSchema schema, String levelName, String value) {
    if (value == null || value.isEmpty()) return false;
    var dimension = schema.dimensions().get(DIM_LOCATION);
    if (dimension == null) return false;
    return dimension.hierarchyData().stream().anyMatch(row -> value.equals(row.get(levelName)));
  }

  private Hierarchy topicHierarchy(AixmFeature feature, CubeSchema schema) {
    Level featureLevel = schema.locate(DIM_TOPIC, "feature");
    if (featureLevel == null) return null;
    String topic = feature.topic();
    if (topic == null || topic.isEmpty()) return null;
    return HierarchyFactory.create(featureLevel, topic, schema);
  }

  private Map<String, String> metadataOf(List<AixmFeature> features) {
    return Map.of(
        "aixm.featureCount",
        String.valueOf(features.size()),
        "aixm.topics",
        features.stream()
            .map(AixmFeature::topic)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.joining(",")),
        "aixm.locations",
        features.stream()
            .map(AixmFeature::location)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.joining(",")),
        "aixm.affectedFirs",
        features.stream()
            .map(AixmFeature::affectedFir)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.joining(",")));
  }
}
