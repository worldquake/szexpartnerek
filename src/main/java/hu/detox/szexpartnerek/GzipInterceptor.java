package hu.detox.szexpartnerek;

import okhttp3.*;
import okio.GzipSource;
import okio.Okio;

import java.io.IOException;

public class GzipInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request.Builder newRequest = chain.request().newBuilder();
        newRequest.addHeader("Accept-Encoding", "gzip");
        Response response = chain.proceed(newRequest.build());
        if (isGzipped(response)) {
            return unzip(response);
        } else {
            return response;
        }
    }

    private Response unzip(final Response response) throws IOException {
        if (response.body() == null) {
            return response;
        }
        GzipSource gzipSource = new GzipSource(response.body().source());
        byte[] bodyString = Okio.buffer(gzipSource).readByteArray();
        ResponseBody responseBody = ResponseBody.create(response.body().contentType(), bodyString);
        Headers strippedHeaders = response.headers().newBuilder()
                .removeAll("Content-Encoding")
                .removeAll("Content-Length")
                .build();
        return response.newBuilder()
                .headers(strippedHeaders)
                .body(responseBody)
                .message(response.message())
                .build();
    }

    private Boolean isGzipped(Response response) {
        return response.header("Content-Encoding") != null && response.header("Content-Encoding").equals("gzip");
    }
}