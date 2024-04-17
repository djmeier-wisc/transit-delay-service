package com.doug.projects.transitdelayservice.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class UrlRedirectUtil {
    public static String handleRedirect(HttpURLConnection conn) {
        String newUrl = conn.getHeaderField("Location");
        try {
            new URL(newUrl).getHost();
        } catch (MalformedURLException e) {
            //handle case of location header failing to be absolute url
            newUrl = conn.getURL().getProtocol() + "://" + conn.getURL().getHost() + newUrl;
        }
        return newUrl;
    }

    public static boolean isRedirect(HttpURLConnection conn) throws IOException {
        return conn.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM ||
                conn.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP ||
                conn.getResponseCode() == 308;
    }
}
