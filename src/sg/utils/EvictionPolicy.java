package sg.utils;

public interface EvictionPolicy<E> {
    boolean isExpired(E entry);
}
