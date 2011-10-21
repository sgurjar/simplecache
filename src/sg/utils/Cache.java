package sg.utils;

public interface Cache<K,V> {
    V get(K key);

    public enum Factory {

        DEFAULT {
            @Override public <KEY, VAL>
            Cache<KEY, VAL> create(
                CacheEntryFactory<KEY, VAL> factory,
                EvictionPolicy<VAL> evictionpolicy) {
                return new SimpleCache<KEY, VAL>(factory, evictionpolicy);
            }
        };

        public abstract <KEY, VAL>
        Cache<KEY, VAL> create(
            CacheEntryFactory<KEY, VAL> factory,
            EvictionPolicy<VAL> evictionpolicy
        );
    }
}
