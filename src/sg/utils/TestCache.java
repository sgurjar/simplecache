package sg.utils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

public class TestCache {

    private static final
    ExpirableFutureTask.Factory<String, Config> factory =
        new ExpirableFutureTask.Factory<String, Config>(){
            @Override public ExpirableFutureTask<Config> create(String key) {
                ArrayList<File> files = new ArrayList<File>(10);

                for (String a : System.getProperty(key, "").trim().split(File.pathSeparator))
                    if (!(a = a.trim()).isEmpty()) files.add(new File(a));

                long[] stamp = new long[files.size()];

                return new Foo(key, files, stamp);
            }

    };

    static class Foo extends ExpirableFutureTask<Config> {

        private final String key;
        private final ArrayList<File> files;
        private final long[] lastmodified;

        public Foo(final String key, final ArrayList<File> files, final long[] lastmodified) {
            super(new Callable<Config>(){
                @Override public Config call() throws Exception {
                    return new Config(files, lastmodified);
                }
            });
            this.key = key;
            this.files = files;
            this.lastmodified = lastmodified;
        }

        @Override
        public boolean isExpired() {
            final int sz = this.files.size();
            for (int i = 0; i < sz; i++) {
                if (files.get(i).lastModified() != lastmodified[i]) return true;
            }
            return false;
        }

        public String getName(){return key;}
    }

    static class Config{
        Properties props = new Properties(); // protects from null

        Config(ArrayList<File> files, long[] lastmodified){
            for(int i=0; i < files.size(); i++){
                lastmodified[i] = load(files.get(i));
            }
        }
        long load(File file) {
            if (!file.exists ()) throw new ConfigException("file not found, " + file.getAbsolutePath());
            if (!file.canRead()) throw new ConfigException("read permission denied, " + file.getAbsolutePath());
            if (!file.isFile ()) throw new ConfigException("not a file, " + file.getAbsolutePath());

            FileReader fr = null;
            try {
                fr = new FileReader(file);
                Properties p = new Properties();
                p.load(fr);
                final long stamp = file.lastModified();
                props.putAll(p);
                return stamp;
            }
            catch (IOException e) { throw new ConfigException(e); }
            finally { if(fr != null) try{fr.close();} catch(IOException e) {throw new ConfigException(e);} }
        }
    }

    @SuppressWarnings("serial")
    static class
    ConfigException extends RuntimeException {
        public ConfigException(Throwable e){super(e);}
        public ConfigException(String m){super(m);}
    }

    static void sleep(long secs) {try {Thread.sleep(secs*1000);} catch (InterruptedException e) {e.printStackTrace();}}

    public static void main(String[] args) throws Exception {
        String f = "c:/tmp/tcpdump.txt";
        System.setProperty("configfile", f);

        final Cache<String, Config> cache = new SimpleCache<String, Config>(factory);

        class _Runnable implements Runnable{
            final CountDownLatch latch;
            _Runnable(CountDownLatch latch){this.latch=latch;}
            @Override public void run() {
                try {
                    Config conf = cache.get("configfile");
                    TRACE.P("got Config " + System.identityHashCode(conf));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                latch.countDown();
            }
        };

        Runnable waitForCompletion = new Runnable() {
            public void run() {
                final int N = 10;
                CountDownLatch latch = new CountDownLatch(N);
                _Runnable task = new _Runnable(latch);
                for (int i = 0; i < N; i++)new Thread(task).start();
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };

        waitForCompletion.run();

        new File(f).setLastModified(System.currentTimeMillis());

        waitForCompletion.run();

        waitForCompletion.run();
    }
}


/*
$Log: $
*/