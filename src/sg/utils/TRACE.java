package sg.utils;

public class TRACE {
    static final long NANO_ORIGIN = System.nanoTime();
    static final long now() {return System.nanoTime() - NANO_ORIGIN;}
    static void P(String msg){
        System.out.println(
                now() +
                " - " +
                Thread.currentThread().toString() +
                " " +
                msg);
    }
    static void P(String fmt, Object... args){P(String.format(fmt, args));}
}


/*
$Log: $
*/