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
            final CacheEntryFactory<K, V> factory,
            final EvictionPolicy<V> evictionpolicy
        ) {
            this.factory = factory;
            this.evictionpolicy = evictionpolicy;
        }

    @SuppressWarnings("unchecked")
    @Override
    public
    V get(final K key) {
        Object ret = this.store.get(key);

        if (ret == null) {
            final CountDownLatch latch = new CountDownLatch(1);
            final Object prev = this.store.putIfAbsent(key, latch);

			if (null == prev) {
				try {
					// this might throw runtimeexception
					ret = this.factory.create(key);
					this.store.replace(key, ret);
				} catch (final Throwable t) {
					this.store.remove(key);
					throw (t instanceof RuntimeException) ? (RuntimeException) t
							: new RuntimeException(t);
				} finally {
					latch.countDown();
				}
			} else {
				ret = prev;
			}
        }

        if (ret instanceof CountDownLatch) {
            // no instance is created yet for this key, it is being created
            // by another thread, wait till thats done
            try {
                ((CountDownLatch) ret).await();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }

            // this is newly created instance of this key
            // returned value can be null if it cant create key first-time
            ret = this.store.get(key);

            if (ret == null) {
				throw new RuntimeException(
						"error in creating first instance for key '" + key
								+ "', please check logs");
			}
        }
        else {
            final V entry = (V) ret;

            if (this.evictionpolicy != null && this.evictionpolicy.isExpired(entry)) {
				this.store.remove(key, entry);
                // so that next get will get updated value
                // and all threads requesting for same key will BLOCK.
            }
        }

        return (V)ret;
    }
}
