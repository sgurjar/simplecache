package sg.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

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
   should use expired instance of value and should not block.

   http://cs.oswego.edu/pipermail/concurrency-interest/2011-October/008335.html

 */
public class SimpleCache<K,V> implements Cache<K,V> {

    private final CacheEntryFactory<K,V> factory;
    private final EvictionPolicy<V> evictionpolicy;
    private final ConcurrentMap<K, Object> store = new ConcurrentHashMap<K, Object>(); // ensures publication

    public SimpleCache(
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
                ret = new Entry<V>(factory.create(key)); // this might throw runtimeexception
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
            Entry<V> entry = (Entry<V>) ret;

            if (evictionpolicy != null && evictionpolicy.isExpired(entry.v)) {
                final AtomicInteger sync = entry.sync;

                if (sync.compareAndSet(0, 1)) { // lock
                    // multiple threads can return true from canEvict
                    // but only one can get hold of lock, threads that can't
                    // get hold of lock doesn't block they returns old value
                    // while new value is being baked.
                    try {
                        entry = (Entry<V>) store.get(key);

                        if (evictionpolicy.isExpired(entry.v)) { // double-check
                            Entry<V> newval = new Entry<V>(factory.create(key), sync);
                            store.replace(key, newval); // replace might be faster then put
                            ret = newval;
                        }
                    } catch (Throwable catchall) {
                        catchall.printStackTrace(System.err); // old value will be returned
                    } finally {
                        sync.set(0); // don't forget to unlock
                    }
                }
            }
        }

        return ((Entry<V>) ret).v;
    }

    private static final
    class Entry<V> {
        final AtomicInteger sync;
        final V v;
        Entry(V v) { this(v, new AtomicInteger(0)); }
        Entry(V v, AtomicInteger sync) { this.v = v; this.sync = sync; }
   }
}
