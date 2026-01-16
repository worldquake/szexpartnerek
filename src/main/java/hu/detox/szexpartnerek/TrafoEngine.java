package hu.detox.szexpartnerek;

import java.io.File;
import java.util.function.Function;

public interface TrafoEngine extends Function<String, Object> {
    Function<String, String> url();

    File in();

    File out();

    int page();
}
