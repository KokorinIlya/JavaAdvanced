package ru.ifmo.rain.kokorin.arrayset;

import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;

public class ArraySet<T> extends AbstractSet<T> implements NavigableSet<T> {
    private final List<T> sortedData;
    private final Comparator<? super T> comparator;

    private ArraySet(List<T> list, Comparator<? super T> comp) {
        sortedData = list;
        comparator = comp;
    }

    public ArraySet() {
        this(Collections.emptyList(), null);
    }

    public ArraySet(Collection<? extends T> collection, Comparator<? super T> comp) {
        comparator = comp;
        //If comp is null, the natural ordering of the elements will be used
        //(https://docs.oracle.com/javase/9/docs/api/java/util/TreeSet.html#TreeSet-java.util.Comparator-)
        TreeSet<T> set = new TreeSet<>(comp);
        //throws NullPointerException, if collection is null
        set.addAll(Objects.requireNonNull(collection));
        sortedData = new ArrayList<>(set);
    }

    public ArraySet(Collection<? extends T> collection) {
        this(collection, null);
    }

    public ArraySet(ArraySet<T> other) {
        //ArraySet is immutable, so we can use the same data
        sortedData = other.sortedData;
        comparator = other.comparator;
    }

    private static final String SET_IS_EMPTY_MESSAGE = "ArraySet is empty, cannot get ";

    public T first() {
        if (!sortedData.isEmpty()) {
            return sortedData.get(0);
        }
        throw new NoSuchElementException(SET_IS_EMPTY_MESSAGE + "first element");
    }

    public T last() {
        if (!sortedData.isEmpty()) {
            return sortedData.get(sortedData.size() - 1);
        }
        throw new NoSuchElementException(SET_IS_EMPTY_MESSAGE + "last element");
    }

    public int size() {
        return sortedData.size();
    }

    private static final String unsupportedOperationMessage = "ArraySet is immutable, cannot perform ";

    public T pollFirst() {
        throw new UnsupportedOperationException(unsupportedOperationMessage + "pollFirst");
    }

    public T pollLast() {
        throw new UnsupportedOperationException(unsupportedOperationMessage + "pollLast");
    }

    @Override
    public boolean contains(Object o) {
        /*
        contains(Object o) can throw ClassCastException,
        if the type of the specified element is incompatible with this arrayset
        ClassCastException will be thrown if the cast (T) o is incorrect
        */
        return Collections.binarySearch(sortedData, (T) o, comparator) >= 0;
    }

    @Override
    public Iterator<T> iterator() {
        return Collections.unmodifiableList(sortedData).iterator();
    }

    private boolean valid(int index) {
        return 0 <= index && index < size();
    }

    private T getAnswer(T element, int addIfFound, int addIfNotFound) {
        int index = searchForPosition(element, addIfFound, addIfNotFound);

        if (valid(index)) {
            return sortedData.get(index);
        }
        return null;
    }

    private int searchForPosition(T element, int addIfFound, int addIfNotFound) {
        int index = Collections.binarySearch(sortedData, element, comparator);
        if (index >= 0) {
            //found
            return index + addIfFound;
        }
        /*
        returns -(insertion point) - 1
        The insertion point is defined the index of the first element greater
        than the key, or list.size() if all elements in the list are less than the specified key
         */
        //insertion_point + some const
        return -index - 1 + addIfNotFound;
    }

    @Override
    public T lower(T element) {
        return getAnswer(element, -1, -1);
    }

    @Override
    public T floor(T element) {
        return getAnswer(element, 0, -1);
    }

    @Override
    public T ceiling(T element) {
        return getAnswer(element, 0, 0);
    }

    @Override
    public T higher(T element) {
        return getAnswer(element, 1, 0);
    }

    @Override
    public NavigableSet<T> subSet(T fromElement, boolean fromInclusive, T toElement, boolean toInclusive) {
        int addIfFound = fromInclusive ? 0 : 1;
        int addIfNotFound = 0;
        int leftBorder = searchForPosition(fromElement, addIfFound, addIfNotFound);

        addIfFound = toInclusive ? 0 : -1;
        addIfNotFound = -1;
        int rightBorder = searchForPosition(toElement, addIfFound, addIfNotFound) + 1;

        if (leftBorder - rightBorder >= 0) {
            return Collections.emptyNavigableSet();
        }
        return new ArraySet<>(sortedData.subList(leftBorder, rightBorder), comparator);
    }

    @Override
    public NavigableSet<T> headSet(T toElement, boolean inclusive) {
        if (sortedData.isEmpty()) {
            return Collections.emptyNavigableSet();
        }
        return subSet(first(), true, toElement, inclusive);
    }

    @Override
    public NavigableSet<T> tailSet(T fromElement, boolean inclusive) {
        if (sortedData.isEmpty()) {
            return Collections.emptyNavigableSet();
        }
        return subSet(fromElement, inclusive, last(), true);
    }

    @Override
    public Comparator<? super T> comparator() {
        return comparator;
    }

    @Override
    public SortedSet<T> subSet(T fromElement, T toElement) {
        return subSet(fromElement, true, toElement, false);
    }

    @Override
    public SortedSet<T> tailSet(T fromElement) {
        return tailSet(fromElement, true);
    }

    @Override
    public SortedSet<T> headSet(T toElement) {
        return headSet(toElement, false);
    }

    private class ReversedList<E> extends AbstractList<E> {
        private final boolean reversed;
        private final List<E> data;

        ReversedList(List<E> other) {
            if (other instanceof ReversedList) {
                ReversedList<E> tmp = (ReversedList<E>)other;
                reversed = !tmp.reversed;
                data = tmp.data;
            } else {
                reversed = true;
                data = other;
            }
        }

        @Override
        public int size() {
            return data.size();
        }

        @Override
        public E get(int index) {
            if (!reversed) {
                return data.get(index);
            }
            return data.get(size() - 1 - index);
        }
    }

    @Override
    public NavigableSet<T> descendingSet() {
        ReversedList<T> reversed = new ReversedList<>(sortedData);
        return new ArraySet<>(reversed, Collections.reverseOrder(comparator));
    }

    @Override
    public Iterator<T> descendingIterator() {
        return descendingSet().iterator();
    }

    @Override
    public void clear() {
        /*
        ArraySet<Integer> arr = new ArraySet<>();
        arr.clear();

        will work without this overriding
         */

        throw new UnsupportedOperationException(unsupportedOperationMessage + "clear");
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        /*
        ArraySet<Integer> arr = new ArraySet<>();
        arr.addAll(Collections.emptyList());

        will work without this overriding
         */

        throw new UnsupportedOperationException(unsupportedOperationMessage + "addAll");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        /*
        ArraySet<Integer> arr = new ArraySet<>();
        arr.removeAll(Collections.<Integer>emptyList());

        will work without this overriding
         */

        throw new UnsupportedOperationException(unsupportedOperationMessage + "removeAll");
    }

    @Override
    public boolean removeIf(Predicate<? super T> filter) {

        /*
        ArraySet<Integer> arraySet1 = new ArraySet<>();
        arraySet1.removeIf((a) -> {return a.hashCode() > 0;});

        will work without this overriding
         */

        throw new UnsupportedOperationException(unsupportedOperationMessage + "removeIf");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        /*
        ArraySet<Integer> arraySet1 = new ArraySet<>();
        arraySet1.retainAll(Collections.emptyList());

        will work without this overriding
         */
        throw new UnsupportedOperationException(unsupportedOperationMessage + "retainAll");
    }
}
