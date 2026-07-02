package at.jku.dke.bigkgolap.index.internal;

import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class CartesianProductTest {

  @Test
  void producesAllCombinationsAcrossDimensionsInDeterministicOrder() {
    var result =
        toList(
            CartesianProduct.of(
                List.of(List.of("a1", "a2", "a3"), List.of("b1", "b2"), List.of("c1", "c2"))));
    Assertions.assertThat(result).hasSize(12);
    Assertions.assertThat(result.get(0)).containsExactly("a1", "b1", "c1");
    Assertions.assertThat(result.get(result.size() - 1)).containsExactly("a3", "b2", "c2");
  }

  @Test
  void emptyInputListYieldsEmptyCartesian() {
    Assertions.assertThat(toList(CartesianProduct.of(List.of()))).isEmpty();
  }

  @Test
  void anySubListEmptyYieldsEmptyCartesian() {
    var result = toList(CartesianProduct.of(List.of(List.of("a", "b"), List.of(), List.of("c"))));
    Assertions.assertThat(result).isEmpty();
  }

  @Test
  void singleDimensionYieldsOneTuplePerElement() {
    var result = toList(CartesianProduct.of(List.of(List.of("a", "b", "c"))));
    Assertions.assertThat(result).containsExactly(List.of("a"), List.of("b"), List.of("c"));
  }

  private static <T> List<List<T>> toList(Iterable<List<T>> iterable) {
    var list = new ArrayList<List<T>>();
    for (var item : iterable) list.add(item);
    return list;
  }
}
