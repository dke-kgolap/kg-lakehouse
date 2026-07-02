package at.jku.dke.bigkgolap.index.internal;

import at.jku.dke.bigkgolap.model.Dimension;
import at.jku.dke.bigkgolap.model.Hierarchy;
import at.jku.dke.bigkgolap.model.Level;
import at.jku.dke.bigkgolap.model.Member;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class HierarchyCodecTest {

  private final Level territory = new Level("territory", "location", 0, null, null);
  private final Level fir = new Level("fir", "location", 1, "territory", "lookup");
  private final Level location = new Level("location", "location", 2, "fir", "lookup");
  private final Dimension locationDim =
      new Dimension(
          "location",
          List.of(territory, fir, location),
          List.of(Map.of("territory", "Austria", "fir", "LOVV", "location", "LOWW")));

  @Test
  void encodeThenDecodeRoundTripsALeafHierarchy() {
    var original =
        Hierarchy.of(
            List.of(
                new Member(territory, "Austria"),
                new Member(fir, "LOVV"),
                new Member(location, "LOWW")));
    var encoded = HierarchyCodec.encode(original);
    var expected = new LinkedHashMap<String, String>();
    expected.put("territory", "Austria");
    expected.put("fir", "LOVV");
    expected.put("location", "LOWW");
    Assertions.assertThat(encoded).containsExactlyEntriesOf(expected);
    var decoded = HierarchyCodec.decode(locationDim, encoded);
    Assertions.assertThat(decoded).isEqualTo(original);
    Assertions.assertThat(decoded.id()).isEqualTo(original.id());
  }

  @Test
  void encodeOfAllHierarchyReturnsEmptyMap() {
    Assertions.assertThat(HierarchyCodec.encode(Hierarchy.all("location"))).isEmpty();
  }

  @Test
  void decodeOfEmptyMapReturnsAllHierarchy() {
    var h = HierarchyCodec.decode(locationDim, Map.of());
    Assertions.assertThat(h.isAllLevel()).isTrue();
    Assertions.assertThat(h.dimension()).isEqualTo("location");
  }

  @Test
  void decodeOfPartialMembersReturnsShallowerHierarchy() {
    var h = HierarchyCodec.decode(locationDim, Map.of("territory", "Austria"));
    Assertions.assertThat(h.members()).hasSize(1);
    Assertions.assertThat(h.leafMember().value()).isEqualTo("Austria");
  }
}
