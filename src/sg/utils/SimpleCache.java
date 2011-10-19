package sg.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleCache<K,V> implements Cache<K,V> {

    private final ConcurrentMap<K, ExpirableFutureTask<V>> store = new ConcurrentHashMap<K, ExpirableFutureTask<V>>();

    private final ExpirableFutureTask.Factory<K,V> factory;

    public SimpleCache(ExpirableFutureTask.Factory<K, V> factory) {this.factory = factory;}

    public V get(K key) throws InterruptedException {
        ExpirableFutureTask<V> value = store.get(key);
        if (value != null) {
            if (value.isExpired()) {
                final ReentrantLock sync = value.sync();
                if (sync.tryLock()) { // if can't get lock keep using 'old' value
                    try {
                        final ExpirableFutureTask<V> newvalue = factory.create(key);
                        newvalue.run();
                        try {
                            newvalue.get();
                            store.replace(key, newvalue);
                            value=newvalue;
                        } catch (ExecutionException e) { // use 'old' value, can't create newvalue
                            Throwable cause = e.getCause();
                            cause.printStackTrace();
                        }
                    } finally {
                        sync.unlock();
                    }
                }
            }
        } else {
            final ExpirableFutureTask<V> newvalue = factory.create(key);
            value = store.putIfAbsent(key, newvalue);
            if (value == null) {// if Absent
                value = newvalue;
                newvalue.run(); // guarded by putIfAbsent, as it will return null once
            }
        }

        try { return value.get(); }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw (cause instanceof RuntimeException) ?
                    (RuntimeException) cause : new RuntimeException(cause);
        }
    }
}


/*
$Log: $
*/