package com.portfolioiq.data.repository;

import android.os.Handler;
import android.os.Looper;

import com.portfolioiq.BuildConfig;
import com.portfolioiq.domain.model.StockSymbol;
import com.portfolioiq.domain.repository.ResultCallback;
import com.portfolioiq.domain.repository.StockSearchRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Data-layer implementation of StockSearchRepository around Finnhub's
 * /search (symbol lookup) endpoint, using the same API key and the same
 * background-thread-then-main-thread pattern as FinnhubStockPriceRepositoryImpl.
 *
 * Listings with a dot in the symbol (e.g. "AAPL.MX", "APC.DE") are other
 * exchanges' copies of a stock. They are skipped so the user only sees
 * US-style tickers, which are the ones the /quote endpoint used by the
 * dashboard prices.
 */
public class FinnhubStockSearchRepositoryImpl implements StockSearchRepository {

    private static final String SEARCH_URL = "https://finnhub.io/api/v1/search?q=%s&token=%s";
    private static final String SEARCH_ERROR = "Could not load stock suggestions.";
    private static final String LOOKUP_ERROR = "Could not check the stock symbol. Please try again.";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void searchSymbols(String query, ResultCallback<List<StockSymbol>> callback) {
        executor.execute(() -> {
            try {
                List<StockSymbol> results = fetch(query);
                mainHandler.post(() -> callback.onSuccess(results));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(SEARCH_ERROR));
            }
        });
    }

    @Override
    public void lookupSymbol(String symbol, ResultCallback<StockSymbol> callback) {
        executor.execute(() -> {
            try {
                StockSymbol match = null;
                for (StockSymbol candidate : fetch(symbol)) {
                    if (candidate.getSymbol().equalsIgnoreCase(symbol)) {
                        match = candidate;
                        break;
                    }
                }
                StockSymbol found = match;
                mainHandler.post(() -> callback.onSuccess(found));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(LOOKUP_ERROR));
            }
        });
    }

    /** Calls /search and returns the US-style listings, in Finnhub's order. Throws on any failure. */
    private List<StockSymbol> fetch(String query) throws Exception {
        HttpURLConnection connection = null;
        try {
            String url = String.format(SEARCH_URL,
                    URLEncoder.encode(query, StandardCharsets.UTF_8.name()), BuildConfig.FINNHUB_API_KEY);
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + connection.getResponseCode());
            }

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }

            JSONArray items = new JSONObject(body.toString()).optJSONArray("result");
            List<StockSymbol> symbols = new ArrayList<>();
            if (items == null) {
                return symbols;
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String symbol = item.optString("symbol", "");
                if (symbol.isEmpty() || symbol.contains(".")) {
                    continue;
                }
                symbols.add(new StockSymbol(symbol, item.optString("description", "")));
            }
            return symbols;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
