package org.example.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HttpUtil {

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static String sendGetRequestMatch(String url) throws Exception {
        var uri = URI.create(String.format(url));
        var request = HttpRequest
                .newBuilder()
                .uri(uri)
                .header("accept", "application/json")
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Error en el GET request de la url " + url);
        }

        return response.body();
    }

    public static String sendPostRequestMatch(String url, String jsonBody, Map<String, String> headers) throws Exception {
        List<String> headersString = new ArrayList<>();
        headers.forEach((k,v) -> {
            headersString.add(k);
            headersString.add(v);
        });

        var uri = URI.create(String.format(url));
        var request = HttpRequest
                .newBuilder()
                .uri(uri)
                .headers(headersString.toArray(new String[0]))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Error en el POST request de la url " + url + ", response: " + response.body() + " " + response.statusCode());
        }

        return response.body();
    }
}
