package at.jku.dke.bigkgolap.engines.fixm.internal;

import java.time.LocalDate;

public record FixmFlight(
    String departureAerodrome, LocalDate estimatedOffBlockTime, LocalDate actualTimeOfArrival) {}
