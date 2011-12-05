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

    private final CacheEntryFactory<K, V> factory;
    private final EvictionPolicy<V> evictionpolicy;
    private final ConcurrentMap<K, Object> store = new ConcurrentHashMap<K, Object>();

    private static final String ERR_CREATING_FIRST_INSTANCE =
        "This thread was waiting for first instance creation for the key '%s', " +
        "first instance creation has failed.";

    public SimpleCache(final CacheEntryFactory<K, V> factory,
                       final EvictionPolicy<V> evictionpolicy) {
        this.factory = factory;
        this.evictionpolicy = evictionpolicy;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get(final K key) {
        Object ret = this.store.get(key);

        if (ret == null) {
            final CountDownLatch latch = new CountDownLatch(1);
            ret = this.store.putIfAbsent(key, latch);

            if (ret == null) {
                try {
                    final Entry<V> entry = new Entry<V>(this.factory.create(key));
                    this.store.replace(key, entry);
                    ret = entry;
                } catch(final Throwable t) {
                    this.store.remove(key);
                    throw (t instanceof RuntimeException) ? (RuntimeException) t : new RuntimeException(t);
                } finally {
                    latch.countDown();
                }
            }
        }

        if (ret instanceof CountDownLatch) {
            final CountDownLatch latch = (CountDownLatch) ret;

            try { latch.await(); }
            catch (final InterruptedException e) { throw new RuntimeException(e); }

            ret = this.store.get(key);

            if (!(ret instanceof Entry)) {
                throw new RuntimeException(String.format(ERR_CREATING_FIRST_INSTANCE, key));
            }
        }

        Entry<V> entry = (Entry<V>) ret;

        if (this.evictionpolicy != null && this.evictionpolicy.isExpired(entry.v)) {
            final AtomicInteger sync = entry.sync;

            if (sync.compareAndSet(0, 1)) {
                try {
                    entry = (Entry<V>) this.store.get(key);

                    if (this.evictionpolicy.isExpired(entry.v)) { // double-check
                        // here we are using same 'sync' instance as it was in expired entry
                        // so that there is always one lock per entry.
                        final Entry<V> newval = new Entry<V>(this.factory.create(key), sync);
                        this.store.replace(key, newval);
                        ret = newval;
                    }
                } catch (final Throwable catchall) {
                    catchall.printStackTrace();
                } finally {
                    sync.set(0);
                }
            }
        }

        return ((Entry<V>) ret).v;
    }

    private static final class Entry<V> {
        final AtomicInteger sync;
        final V v;

        Entry(final V v) {
            this(v, new AtomicInteger(0));
        }

        Entry(final V v, final AtomicInteger sync) {
            this.v = v;
            this.sync = sync;
        }
    }
}
