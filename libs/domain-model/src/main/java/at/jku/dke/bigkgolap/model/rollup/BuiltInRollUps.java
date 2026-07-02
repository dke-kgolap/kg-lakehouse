package at.jku.dke.bigkgolap.model.rollup;

import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.HierarchyNotAvailableException;
import at.jku.dke.bigkgolap.model.InvalidCubeSchemaException;
import at.jku.dke.bigkgolap.model.Level;
import at.jku.dke.bigkgolap.model.Member;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

final class BuiltInRollUps {

  static final String DATE_TO_MONTH = "builtin:date_to_month";
  static final String DATE_TO_YEAR = "builtin:date_to_year";
  static final String LOOKUP = "lookup";

  private BuiltInRollUps() {}

  static Member dateToMonth(Member member, CubeSchema schema) {
    LocalDate parsed;
    try {
      parsed = LocalDate.parse(member.value());
    } catch (DateTimeParseException e) {
      throw new HierarchyNotAvailableException(
          "%s requires a yyyy-MM-dd value but got '%s'".formatted(DATE_TO_MONTH, member.value()));
    }
    Level parent = parentLevel(member, schema);
    String rolled = "%04d-%02d".formatted(parsed.getYear(), parsed.getMonthValue());
    return new Member(parent, rolled);
  }

  static Member dateToYear(Member member, CubeSchema schema) {
    YearMonth parsed;
    try {
      parsed = YearMonth.parse(member.value());
    } catch (DateTimeParseException e) {
      throw new HierarchyNotAvailableException(
          "%s requires a yyyy-MM value but got '%s'".formatted(DATE_TO_YEAR, member.value()));
    }
    Level parent = parentLevel(member, schema);
    return new Member(parent, "%04d".formatted(parsed.getYear()));
  }

  static Member lookup(Member member, CubeSchema schema) {
    Level parent = parentLevel(member, schema);
    var dimension = schema.dimensions().get(member.level().dimension());
    if (dimension == null) {
      throw new HierarchyNotAvailableException(
          "No hierarchy data registered for dimension '%s'".formatted(member.level().dimension()));
    }
    List<Map<String, String>> rows = dimension.hierarchyData();
    if (rows.isEmpty()) {
      throw new HierarchyNotAvailableException(
          "Hierarchy data for dimension '%s' is empty".formatted(member.level().dimension()));
    }
    Map<String, String> match =
        rows.stream()
            .filter(row -> member.value().equals(row.get(member.level().name())))
            .findFirst()
            .orElseThrow(
                () ->
                    new HierarchyNotAvailableException(
                        "No hierarchy row matches '%s=%s' in dimension '%s'"
                            .formatted(
                                member.level().name(),
                                member.value(),
                                member.level().dimension())));
    String parentValue = match.get(parent.name());
    if (parentValue == null || parentValue.isEmpty()) {
      throw new HierarchyNotAvailableException(
          "Hierarchy row for '%s=%s' is missing value for parent level '%s'"
              .formatted(member.level().name(), member.value(), parent.name()));
    }
    return new Member(parent, parentValue);
  }

  private static Level parentLevel(Member member, CubeSchema schema) {
    String parentName = member.level().rollupTo();
    if (parentName == null) {
      throw new HierarchyNotAvailableException(
          "Level '%s' has no parent level (already at root)".formatted(member.level()));
    }
    Level parent = schema.locate(member.level().dimension(), parentName);
    if (parent == null) {
      throw new InvalidCubeSchemaException(
          "Parent level '%s' of '%s' not found in schema '%s'"
              .formatted(parentName, member.level(), schema.id()));
    }
    return parent;
  }
}
