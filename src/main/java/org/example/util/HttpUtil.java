package org.example.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HttpUtil {
    public static String sendRequestMatch(String url) throws Exception {
        var uri = URI.create(String.format(url));
        var client = HttpClient.newHttpClient();
        var request = HttpRequest
                .newBuilder()
                .uri(uri)
                .header("accept", "application/json")
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Error en el request de la url " + url);
        }

        return response.body();
    }
}
