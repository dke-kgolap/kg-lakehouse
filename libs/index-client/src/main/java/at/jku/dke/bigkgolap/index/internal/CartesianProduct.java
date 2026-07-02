package at.jku.dke.bigkgolap.index.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public final class CartesianProduct {

  private CartesianProduct() {}

  /**
   * Returns an {@link Iterable} over all tuples in the Cartesian product of {@code lists}. Yields
   * an empty iterable when {@code lists} is empty or any sub-list is empty. Each yielded list is a
   * fresh, unmodifiable snapshot.
   */
  public static <T> Iterable<List<T>> of(List<List<T>> lists) {
    if (lists.isEmpty()) return Collections.emptyList();
    for (var sub : lists) {
      if (sub.isEmpty()) return Collections.emptyList();
    }
    return () -> new CartesianIterator<>(lists);
  }

  // ------------------------------------------------------------------
  // Iterator implementation
  // ------------------------------------------------------------------

  private static final class CartesianIterator<T> implements Iterator<List<T>> {
    private final List<List<T>> lists;
    private final int[] indices;
    private boolean hasNext;

    CartesianIterator(List<List<T>> lists) {
      this.lists = lists;
      this.indices = new int[lists.size()];
      this.hasNext = true;
    }

    @Override
    public boolean hasNext() {
      return hasNext;
    }

    @Override
    public List<T> next() {
      if (!hasNext) throw new NoSuchElementException();
      // Snapshot current combination
      var result = new ArrayList<T>(lists.size());
      for (int i = 0; i < lists.size(); i++) {
        result.add(lists.get(i).get(indices[i]));
      }
      // Advance indices (right-to-left, like an odometer)
      advance();
      return Collections.unmodifiableList(result);
    }

    private void advance() {
      for (int i = lists.size() - 1; i >= 0; i--) {
        indices[i]++;
        if (indices[i] < lists.get(i).size()) {
          return;
        }
        indices[i] = 0;
      }
      // All indices wrapped: exhausted
      hasNext = false;
    }
  }
}
