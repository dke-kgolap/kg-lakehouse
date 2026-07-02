package at.jku.dke.bigkgolap.surface.api.dto;

import java.util.List;

public record QueryLogsResponse(List<LogsDto.QueryLogDto> logs) {}
