package at.jku.dke.bigkgolap.engines.iwxxm;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class IwxxmAnalyzerTest {

  private final IwxxmAnalyzer analyzer = new IwxxmAnalyzer();

  @Test
  void metarYieldsOneContextWithDayInstantAerodromeAndTopicMETAR() throws Exception {
    var result = analyzer.analyze(Fixtures.metar(), Fixtures.meteoSchema());

    Assertions.assertThat(result.featureContexts()).hasSize(1);
    var ctx = result.featureContexts().get(0);
    var topicHier = ctx.get("topic");
    Assertions.assertThat(topicHier).isNotNull();
    Assertions.assertThat(topicHier.leafMember().value()).isEqualTo("METAR");
    Assertions.assertThat(ctx.get("location").leafMember().value()).isEqualTo("LOWW");
    Assertions.assertThat(ctx.get("time").leafMember().value()).isEqualTo("2026-05-01");
  }

  @Test
  void tafFansOutAcrossDaysInTheValidPeriod() throws Exception {
    var result = analyzer.analyze(Fixtures.taf(), Fixtures.meteoSchema());

    var days =
        result.featureContexts().stream()
            .map(ctx -> ctx.get("time"))
            .filter(java.util.Objects::nonNull)
            .map(h -> h.leafMember().value())
            .collect(java.util.stream.Collectors.toSet());
    Assertions.assertThat(days).containsExactlyInAnyOrder("2026-05-01", "2026-05-02", "2026-05-03");
    Assertions.assertThat(result.featureContexts().get(0).get("topic").leafMember().value())
        .isEqualTo("TAF");
    Assertions.assertThat(result.featureContexts().get(0).get("location").leafMember().value())
        .isEqualTo("LOWW");
  }

  @Test
  void sigmetUsesFirLevelAndReportTimeInterval() throws Exception {
    var result = analyzer.analyze(Fixtures.sigmet(), Fixtures.meteoSchema());

    Assertions.assertThat(result.featureContexts()).isNotEmpty();
    var ctx = result.featureContexts().get(0);
    Assertions.assertThat(ctx.get("topic").leafMember().value()).isEqualTo("SIGMET");
    Assertions.assertThat(ctx.get("location").leafMember().value()).isEqualTo("LOVV");
    Assertions.assertThat(ctx.get("location").leafMember().level().name()).isEqualTo("fir");
  }

  @Test
  void outOfScopeRootYieldsEmptyResult() throws Exception {
    var result = analyzer.analyze(Fixtures.airmet(), Fixtures.meteoSchema());
    Assertions.assertThat(result.featureContexts()).isEmpty();
  }

  @Test
  void malformedXmlThrowsIwxxmParseException() {
    Assertions.assertThatThrownBy(
            () -> analyzer.analyze(Fixtures.malformed(), Fixtures.meteoSchema()))
        .isInstanceOf(IwxxmParseException.class);
  }
}
