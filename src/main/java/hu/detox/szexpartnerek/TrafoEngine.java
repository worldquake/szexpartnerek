package hu.detox.szexpartnerek;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.io.Flushable;
import java.util.Iterator;
import java.util.function.Function;

public interface TrafoEngine extends Function<String, Object>, AutoCloseable {
    Function<String, String> url();

    Iterator<?> input(JsonNode parent);

    TrafoEngine[] subTrafos();

    Persister persister();

    String[] in();

    File out();

    int page();

    @Override
    default void close() throws Exception {
        if (this instanceof Flushable fl) fl.flush();
        TrafoEngine[] sts = subTrafos();
        if (sts != null) for (TrafoEngine te : sts) {
            te.close();
        }
        Persister p = persister();
        if (p != null) p.close();
    }
}
