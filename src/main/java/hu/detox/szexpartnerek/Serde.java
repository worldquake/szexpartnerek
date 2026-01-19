package hu.detox.szexpartnerek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.jsoup.internal.StringUtil;

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public class Serde implements Closeable, Flushable {
    public static final ObjectMapper OM = new ObjectMapper();
    private static final Function<String, Object> TOSTR = responseBody -> responseBody;
    private PrintStream out;
    private BufferedReader reader;

    public Serde(PrintStream o, BufferedReader r) {
        reader = r;
        out = o;
    }

    public JsonNode serialize(Response response, Function<String, ?> trafo) throws IOException {
        if (trafo == null) {
            trafo = TOSTR;
        }
        var obj = trafo.apply(response.body().string());
        if (obj == null) return null;
        JsonNode bodyNode = OM.valueToTree(obj);
        var body = bodyNode.toString();
        List<String> heads = new LinkedList<>();
        for (String name : response.headers().names()) {
            for (String value : response.headers(name)) {
                heads.add(name + ": " + value);
                break;
            }
        }
        out.println(heads.size() + " " + body.length());
        out.println("HTTP/1.1 " + response.code() + " " + response.message());
        out.println("x-request: " + response.request().url());
        for (String h : heads) {
            out.println(h);
        }
        out.println(body);
        out.flush();
        return bodyNode;
    }

    public Response next() throws IOException {
        String statusLine = reader.readLine();
        if (StringUtil.isBlank(statusLine)) return null;
        int[] contentLengths = new int[2]; // Headers count, and body strlen
        String[] hl = statusLine.split(" ");
        contentLengths[0] = Integer.parseInt(hl[0]) + 1;
        contentLengths[1] = Integer.parseInt(hl[1]);
        statusLine = reader.readLine();
        if (!statusLine.startsWith("HTTP/1.1 ")) throw new IOException("Invalid status line: " + statusLine);
        String[] statusParts = statusLine.split(" ", 3);
        int code = Integer.parseInt(statusParts[1]);
        String message = statusParts.length > 2 ? statusParts[2] : "";
        String req = null;
        String contentType = null;
        Headers.Builder headersBuilder = new Headers.Builder();
        String line;
        for (int i = 0; i < contentLengths[0]; i++) {
            line = reader.readLine();
            int idx = line.indexOf(":");
            if (idx == -1) continue;
            String name = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            headersBuilder.add(name, value);
            if (contentLengths[1] <= 0 && name.equalsIgnoreCase("Content-Length")) {
                contentLengths[1] = Integer.parseInt(value);
            }
            if (name.equalsIgnoreCase("Content-Type")) {
                contentType = value;
            }
            if (name.equalsIgnoreCase("x-request")) {
                req = value;
            }
        }
        if (contentLengths[1] < 0) throw new IOException("Missing Content-Length");

        char[] body = new char[contentLengths[1]];
        int read = 0;
        while (read < contentLengths[1]) {
            int r = reader.read(body, read, contentLengths[1] - read);
            if (r == -1) throw new IOException("Unexpected end of stream");
            read += r;
        }
        reader.readLine();
        ResponseBody responseBody = ResponseBody.create(new String(body), MediaType.parse(contentType));
        return new Response.Builder()
                .request(new Request.Builder().url(req).build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(message)
                .headers(headersBuilder.build())
                .body(responseBody)
                .build();
    }

    @Override
    public void close() throws IOException {
        if (out != null) out.close();
        if (reader != null) reader.close();
    }

    @Override
    public void flush() {
        if (out != null) out.flush();
    }
}