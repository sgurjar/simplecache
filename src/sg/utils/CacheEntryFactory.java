package sg.utils;

public interface CacheEntryFactory<K, V> {
    V create(K key);
}
