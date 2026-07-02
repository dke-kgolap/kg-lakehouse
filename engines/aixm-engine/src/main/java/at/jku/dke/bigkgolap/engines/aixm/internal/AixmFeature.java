package at.jku.dke.bigkgolap.engines.aixm.internal;

import java.time.LocalDate;

public record AixmFeature(
    String topic,
    String location,
    String affectedFir,
    LocalDate beginPosition,
    LocalDate endPosition) {}
