package sg.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;

/**
Requirements:
 1 only one instance per key should be created, either because its absent
   for a given key or its expired (in that case it will be recreated).
 2 most common path asking for a value for a key should not block or synchronize.
 3 when a value doesnt exists for a key, one thread should create the instance
   of value and put in the cache, other threads asking for same key should wait
   till thats complete.
 4 if a value is expired, one thread should (re)create the instance of value
   and other threads asking for same key (when one thread is recreating it)
   should BLOCK untill new value is created.

   http://cs.oswego.edu/pipermail/concurrency-interest/2011-October/008335.html
 */

public class BlockingCache<K,V> implements Cache<K, V> {
    private final CacheEntryFactory<K,V> factory;
    private final EvictionPolicy<V> evictionpolicy;
    private final ConcurrentMap<K, Object> store = new ConcurrentHashMap<K, Object>(); // ensures publication

    public BlockingCache(
            CacheEntryFactory<K, V> factory,
            EvictionPolicy<V> evictionpolicy
        ) {
            this.factory = factory;
            this.evictionpolicy = evictionpolicy;
        }

    @SuppressWarnings("unchecked")
    @Override
    public
    V get(K key) {
        Object ret = store.get(key);

        if (ret == null) {
            CountDownLatch latch = new CountDownLatch(1);
            Object prev = store.putIfAbsent(key, latch);

            if (null == prev) {
                ret = factory.create(key); // this might throw runtimeexception
                store.replace(key, ret);
                latch.countDown();
            } else {
                ret = prev;
            }
        }

        if (ret instanceof CountDownLatch) {
            // no instance is created yet for this key, it is being created
            // by another thread, wait till thats done
            try {
                ((CountDownLatch) ret).await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            // this is newly created instance of this key
            ret = store.get(key);
        } else {
            V entry = (V) ret;

            if (evictionpolicy != null && evictionpolicy.isExpired(entry)) {
                store.replace(key, entry, null);
                // so that next get will get updated value
                // and all threads requesting for same key will BLOCK.
            }
        }

        return (V)ret;
    }
}
