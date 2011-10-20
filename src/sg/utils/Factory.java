package sg.utils;

public interface Factory<K,V> {
    V create(K key);
}