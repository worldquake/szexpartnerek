package hu.detox.szexpartnerek;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.RequestBody;

import java.util.Iterator;

public interface Pager extends Iterator<String> {

    class PagerWrap implements Pager {
        protected Pager wrapped;

        public PagerWrap(Pager p) {
            wrapped = p;
        }

        @Override
        public void reset() {
            wrapped.reset();
        }

        @Override
        public void first(JsonNode node) {
            wrapped.first(node);
        }

        @Override
        public int current(JsonNode node) {
            return wrapped.current(node);
        }

        @Override
        public boolean hasNext() {
            return wrapped.hasNext();
        }

        @Override
        public String next() {
            return wrapped.next();
        }
    }

    void reset();

    void first(JsonNode node);

    int current(JsonNode node);

    default RequestBody req() {
        return null;
    }
}
