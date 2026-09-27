package com.aibot;

import android.util.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;

/**
 * WebFetch - fetches and extracts clean text from URLs
 * Used to read full pages from search results
 */
public class WebFetch {

    private static final String TAG = "WebFetch";
    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_CHARS  = 3000; // keep it small for ARMv7a

    public interface FetchCallback {
        void onFetched(String content, String title);
        void onError(String error);
    }

    // ─── SYNC FETCH ───────────────────────────────────────────────────────────

    public String[] fetch(String url) {
        // returns [title, content]
        try {
            if (!url.startsWith("http")) url = "https://" + url;

            Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Android; Mobile)")
                .timeout(TIMEOUT_MS)
                .get();

            String title   = doc.title();
            String content = extractMainContent(doc);

            return new String[]{title, content};

        } catch (IOException e) {
            Log.e(TAG, "Fetch error: " + e.getMessage());
            return new String[]{"Error", "Could not fetch page: " + e.getMessage()};
        }
    }

    // ─── ASYNC FETCH ──────────────────────────────────────────────────────────

    public void fetchAsync(String url, FetchCallback callback) {
        new Thread(() -> {
            String[] result = fetch(url);
            if (result[0].equals("Error")) {
                callback.onError(result[1]);
            } else {
                callback.onFetched(result[1], result[0]);
            }
        }).start();
    }

    // ─── CONTENT EXTRACTION ───────────────────────────────────────────────────

    private String extractMainContent(Document doc) {
        // Remove unwanted elements
        doc.select("script, style, nav, footer, header, iframe, " +
                   "aside, form, button, input, .ad, .ads, #ad").remove();

        // Try to find main content area
        String content = "";

        // Try article first
        Element article = doc.selectFirst("article");
        if (article != null) {
            content = article.text();
        }

        // Try main tag
        if (content.length() < 100) {
            Element main = doc.selectFirst("main");
            if (main != null) content = main.text();
        }

        // Try largest div with text
        if (content.length() < 100) {
            Elements divs = doc.select("div");
            int maxLen = 0;
            for (Element div : divs) {
                String text = div.ownText();
                if (text.length() > maxLen) {
                    maxLen  = text.length();
                    content = div.text();
                }
            }
        }

        // Fallback to body
        if (content.length() < 100) {
            content = doc.body().text();
        }

        // Trim to max chars
        if (content.length() > MAX_CHARS) {
            content = content.substring(0, MAX_CHARS) + "...";
        }

        return cleanText(content);
    }

    private String cleanText(String text) {
        return text.replaceAll("\\s+", " ")
                   .replaceAll("[\\r\\n]+", " ")
                   .trim();
    }

    // ─── WIKIPEDIA FETCH ──────────────────────────────────────────────────────

    public String fetchWikipediaSummary(String topic) {
        try {
            String url = "https://en.wikipedia.org/wiki/" +
                         topic.replace(" ", "_");
            Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Android; Mobile)")
                .timeout(TIMEOUT_MS)
                .get();

            // Get first paragraph of Wikipedia article
            Elements paragraphs = doc.select(".mw-parser-output > p");
            for (Element p : paragraphs) {
                String text = p.text().trim();
                if (text.length() > 50) {
                    // Remove footnote numbers like [1][2]
                    text = text.replaceAll("\\[\\d+\\]", "");
                    if (text.length() > MAX_CHARS)
                        text = text.substring(0, MAX_CHARS) + "...";
                    return text;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Wikipedia fetch error: " + e.getMessage());
        }
        return null;
    }
}
