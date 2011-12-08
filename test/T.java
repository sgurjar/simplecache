import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import sg.utils.Cache;
import sg.utils.CacheEntryFactory;
import sg.utils.EvictionPolicy;

public class T {

    public static void main(final String[] args) throws Exception {

        long test_duration_secs = 15;

        new T.test2(Cache.Factory.DEFAULT.create(factory, evictionPolicy)).run(test_duration_secs);
        new T.test2(Cache.Factory.BLOCKING.create(factory, evictionPolicy)).run(test_duration_secs);
    }

    static final CacheEntryFactory<String, Element> factory = new CacheEntryFactory<String, Element>() {
        @Override public Element create(final String key) {return new Element(key);}
    };

    static final EvictionPolicy<Element> evictionPolicy = new EvictionPolicy<Element>() {
        @Override public boolean isExpired(final Element entry) {return entry.isModified();}
    };

    static final class Element {
        final File file;
        final long lastmodified;
        String data;

        Element(final String filename) {
            this.file = new File(filename);
            FileReader fr = null;

            try {
                fr = new FileReader(this.file);
                final long len = this.file.length();
                final char[] cbuf = new char[(int) len];
                fr.read(cbuf);
                this.data = new String(cbuf);
            } catch (final IOException e) {
                LOG("Element ctor error " + e);
                throw new RuntimeException(e);
            } finally {
                if (fr != null) try { fr.close(); } catch (final IOException e) { e.printStackTrace(); }
            }

            this.lastmodified = this.file.lastModified();

            LOG("Element ctor created " + this);
        }

        public boolean isModified() {
            final boolean flag = this.lastmodified != this.file.lastModified();
            if(flag)
                LOG(String.format("%s lastloaded=%s lastmodified=%s returns=%s",
                              this.file,
                              dt(this.lastmodified),
                              dt(this.file.lastModified()),
                              flag));
            return flag;
        }

        String getData() {
            return this.data;
        }

        @Override
        public String toString() {
            return String.format("[%s] loaded-(%s) file-(%s)",
                    this.file,
                    dt(this.lastmodified),
                    dt(this.file.lastModified())
                    );
        }
    }


    static class test2 {
        final Cache<String, Element> cache;

        final String[] keys = {
                  "bwQ~_yln+EMO!Cum"
                , "/tmp/tcpdump.txt"
                , "/tmp/mem.txt"
        };

        final Callable<Element> r1 = new Callable<Element>() {
            @Override public Element call() {return cache.get(test2.this.keys[rndnum(test2.this.keys.length)]);}
        };

        final int N = 5;
        final ExecutorService threadPool = Executors.newCachedThreadPool();
        final ExecutorCompletionService<Element> ecs = new ExecutorCompletionService<Element>(this.threadPool);
        final ScheduledExecutorService update_lastmodified_executor = Executors.newSingleThreadScheduledExecutor();
        final ScheduledExecutorService test_executor = Executors.newScheduledThreadPool(10);

        test2(final Cache<String, Element> cache){
          this.cache = cache;
        }

        void run(long secs) {
            schedule_update_lastmodified();
            schedule_test();
            try{TimeUnit.SECONDS.sleep(secs);}catch(InterruptedException e){throw new RuntimeException(e);}
            close();
        }

        void update_lastmodified() {
            for (final String k : this.keys)
                new File(k).setLastModified(System.currentTimeMillis());
        }

        void schedule_update_lastmodified() {
            final long initialDelay = (long) (Math.random() * 10L);
            final long delay = (long) (Math.random() * 100L);
            this.update_lastmodified_executor.scheduleWithFixedDelay(
                    new Runnable() {@Override public void run() {update_lastmodified();}},
                    initialDelay,
                    delay,
                    TimeUnit.MILLISECONDS);

        }

        void schedule_test(){
            final long initialDelay = (long) (Math.random() * 10L);
            final long delay = (long) (Math.random() * 100L);
            this.test_executor.scheduleWithFixedDelay(
                new Runnable() {@Override public void run() {submit();take();}},
                initialDelay,
                delay,
                TimeUnit.MILLISECONDS
                );

        }

        void submit(){
            for (int i = 0; i < this.N; i++)this.ecs.submit(this.r1);
        }

        void take() {
            for (int i = 0; i < this.N; i++) {
                try {
                    final Element elm = this.ecs.take().get();
                    NUL.println(elm.getData());
                } catch (final Throwable e) {
                    LOG("take() "+cause(e));
                }
            }
        }

        void close() {
            update_lastmodified_executor.shutdown();
            test_executor.shutdown();
            threadPool.shutdown();
        }
    }

    static int rndnum(final int max) {
        return (int) (Math.abs((Math.random() * 10 * max) % max));
    }

    static Throwable cause(Throwable t) {
        for (;;) {
            final Throwable cause = t.getCause();

            if (cause != null)
                t = cause;
            else
                break;
        }

        return t;
    }

    static long base_nano = System.nanoTime();

    static long ms() {
        return TimeUnit.MILLISECONDS.convert(System.nanoTime() - base_nano, TimeUnit.NANOSECONDS);
    }

    static String dt(final long tm) {
        return DF.get().format(tm);
    }
    static final ThreadLocal<SimpleDateFormat> DF = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSSz");
        }
    };

    static final PrintStream NUL = new PrintStream(new OutputStream() {
        long bytes = 0;

        @Override
        public void write(final int b) throws IOException {
            this.bytes++; // do something
        }
    });

    static void LOG(Object msg){
      System.out.println(
          String.format("%s %s %s",
            ms(),
            Thread.currentThread(),
            String.valueOf(msg)
          )
        );
    }
}
