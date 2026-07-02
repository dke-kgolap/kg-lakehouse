package at.jku.dke.bigkgolap.model;

import java.util.Comparator;

public record Member(Level level, String value) implements Comparable<Member> {

  public static final char FORBIDDEN_CHAR = '|';

  public Member {
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException(
          "Member value must not be empty (level=%s)".formatted(level));
    }
    if (value.indexOf(FORBIDDEN_CHAR) >= 0) {
      throw new IllegalArgumentException(
          "Member value must not contain '%c' (level=%s, value='%s')"
              .formatted(FORBIDDEN_CHAR, level, value));
    }
    if (level.name().indexOf(FORBIDDEN_CHAR) >= 0) {
      throw new IllegalArgumentException(
          "Level name must not contain '%c' (level=%s)".formatted(FORBIDDEN_CHAR, level));
    }
  }

  public static Member of(Level level, String value) {
    return new Member(level, value);
  }

  @Override
  public int compareTo(Member other) {
    return Comparator.comparing(Member::level).thenComparing(Member::value).compare(this, other);
  }

  @Override
  public String toString() {
    return level + "=" + value;
  }
}
