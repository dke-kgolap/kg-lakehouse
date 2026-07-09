package at.jku.dke.bigkgolap.query.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class QuadHashTest {

  @Test
  void equalQuadsHashEqual() {
    assertThat(QuadHash.of("<a> <b> <c> <g> .")).isEqualTo(QuadHash.of("<a> <b> <c> <g> ."));
  }

  @Test
  void differentQuadsHashDifferent() {
    assertThat(QuadHash.of("<a> <b> <c> <g> .")).isNotEqualTo(QuadHash.of("<a> <b> <d> <g> ."));
  }

  @Test
  void dedupsInAHashSet() {
    var set = new HashSet<QuadHash>();
    set.add(QuadHash.of("<a> <b> <c> <g> ."));
    set.add(QuadHash.of("<a> <b> <c> <g> ."));
    set.add(QuadHash.of("<a> <b> <d> <g> ."));
    assertThat(set).hasSize(2);
  }
}
