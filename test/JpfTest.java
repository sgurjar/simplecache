import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import sg.utils.Cache;
import sg.utils.CacheEntryFactory;
import sg.utils.EvictionPolicy;

public class JpfTest implements Runnable {

    static final PrintStream NOOP = new PrintStream (
            new OutputStream() {
                @Override public void write(final int b) throws IOException {}
            }
    );

    public static void main(final String[] args) {
        new Thread(new JpfTest()).start();
    }

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
                throw new RuntimeException(e);
            } finally {
                if (fr != null)
                    try {
                        fr.close();
                    } catch (final IOException e) {
                        e.printStackTrace();
                    }
            }

            this.lastmodified = this.file.lastModified();
        }

        public boolean isModified() {
            final boolean flag = this.lastmodified != this.file.lastModified();
            if(flag)
                System.out.printf("%s %s lastloaded=%s lastmodified=%s returns=%s%n",
                              Thread.currentThread(),
                              this.file,
                              this.lastmodified,
                              this.file.lastModified(),
                              flag);
            return flag;
        }

        String getData() {
            return this.data;
        }

        @Override
        public String toString() {
            return String.format("[%s] loaded-(%s) file-(%s)",
                    this.file,
                    this.lastmodified,
                    this.file.lastModified()
                    );
        }
    }

    static final CacheEntryFactory<String, Element> factory =
        new CacheEntryFactory<String, Element>() {
            @Override public Element create(final String key) { return new Element(key); }
        };
    static final EvictionPolicy<Element> evictionPolicy =
        new EvictionPolicy<Element>() {
            @Override public boolean isExpired(final Element entry) { return entry.isModified(); }
        };

    static final Cache<String, Element> cache =
         Cache.Factory.DEFAULT.create(factory, evictionPolicy)
        //Cache.Factory.BLOCKING.create(factory, evictionPolicy)
        ;

    final String[] keys = {
            "bwQ~_yln+EMO!Cum",
            "/tmp/tcpdump.txt",
            "/tmp/mem.txt"
    };

    @Override
    public void run() {
        final Element element = cache.get(
                this.keys[(int) (Math.abs(Math.random() * 10) % this.keys.length)]);
        NOOP.println(element.getData());
    }

}
