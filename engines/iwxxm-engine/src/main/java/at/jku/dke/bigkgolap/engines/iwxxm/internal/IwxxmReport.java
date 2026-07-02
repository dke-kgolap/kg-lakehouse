package at.jku.dke.bigkgolap.engines.iwxxm.internal;

import java.time.LocalDate;

public record IwxxmReport(
    String topic, String location, LocalDate validFrom, LocalDate validUntil) {}
