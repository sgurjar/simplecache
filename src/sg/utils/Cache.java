package sg.utils;

public interface Cache<K,V> {
    V get(K key)throws InterruptedException;
}


/*
$Log: $
*/