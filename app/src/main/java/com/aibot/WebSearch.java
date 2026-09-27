package com.aibot;

import android.util.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.*;

/**
 * Web Search using DuckDuckGo (no API key needed)
 * Parses results and returns clean text
 */
public class WebSearch {

    private static final String TAG = "WebSearch";
    private static final String DDG_URL = "https://html.duckduckgo.com/html/?q=";
    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_RESULTS = 5;

    public interface SearchCallback {
        void onResults(List<SearchResult> results);
        void onError(String error);
    }

    public static class SearchResult {
        public String title;
        public String snippet;
        public String url;

        public SearchResult(String title, String snippet, String url) {
            this.title   = title;
            this.snippet = snippet;
            this.url     = url;
        }

        @Override
        public String toString() {
            return title + ": " + snippet;
        }
    }

    // ─── SYNC SEARCH ──────────────────────────────────────────────────────────

    public List<SearchResult> search(String query) {
        List<SearchResult> results = new ArrayList<>();
        try {
            String url = DDG_URL + query.replace(" ", "+");
            Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Android; Mobile)")
                .timeout(TIMEOUT_MS)
                .get();

            Elements resultDivs = doc.select(".result");
            int count = 0;

            for (Element div : resultDivs) {
                if (count >= MAX_RESULTS) break;

                Element titleEl   = div.selectFirst(".result__title");
                Element snippetEl = div.selectFirst(".result__snippet");
                Element urlEl     = div.selectFirst(".result__url");

                if (titleEl == null || snippetEl == null) continue;

                String title   = titleEl.text().trim();
                String snippet = snippetEl.text().trim();
                String link    = urlEl != null ? urlEl.text().trim() : "";

                if (!title.isEmpty() && !snippet.isEmpty()) {
                    results.add(new SearchResult(title, snippet, link));
                    count++;
                }
            }

            // Fallback: try different selectors
            if (results.isEmpty()) {
                Elements links = doc.select("a.result__a");
                Elements snips = doc.select(".result__snippet");
                for (int i = 0; i < Math.min(links.size(), snips.size()) && i < MAX_RESULTS; i++) {
                    results.add(new SearchResult(
                        links.get(i).text(),
                        snips.get(i).text(),
                        links.get(i).attr("href")
                    ));
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "Search error: " + e.getMessage());
        }
        return results;
    }

    // ─── ASYNC SEARCH ─────────────────────────────────────────────────────────

    public void searchAsync(String query, SearchCallback callback) {
        new Thread(() -> {
            List<SearchResult> results = search(query);
            if (results.isEmpty()) {
                callback.onError("No results found for: " + query);
            } else {
                callback.onResults(results);
            }
        }).start();
    }

    // ─── SUMMARIZE RESULTS ────────────────────────────────────────────────────

    /**
     * Convert search results to a readable summary string
     * for feeding into the bot's context
     */
    public String summarizeResults(List<SearchResult> results) {
        if (results.isEmpty()) return "No information found.";

        StringBuilder sb = new StringBuilder();
        sb.append("Web search results: ");
        for (int i = 0; i < Math.min(3, results.size()); i++) {
            SearchResult r = results.get(i);
            sb.append(r.title).append(": ").append(r.snippet).append(" ");
        }
        return sb.toString().trim();
    }

    /**
     * Extract facts from search results for NARS
     */
    public List<String> extractFacts(List<SearchResult> results) {
        List<String> facts = new ArrayList<>();
        for (SearchResult r : results) {
            // Split snippet into sentences
            String[] sentences = r.snippet.split("[.!?]");
            for (String s : sentences) {
                s = s.trim();
                if (s.length() > 10 && s.length() < 200) {
                    facts.add(s);
                }
            }
        }
        return facts;
    }
}
