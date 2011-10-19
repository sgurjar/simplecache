package sg.utils;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.ReentrantLock;

public abstract class ExpirableFutureTask<T> extends FutureTask<T> {

    public static interface Factory<K, V> {
        ExpirableFutureTask<V> create(K k);
    }

    private final ReentrantLock sync = new ReentrantLock();

    public ExpirableFutureTask(Callable<T> callable) {
        super(callable);
    }

    public ReentrantLock sync() {
        return sync;
    }

    public abstract boolean isExpired();
}


/*
$Log: $
*/