package com.portfolioiq.data.repository;

import android.os.Handler;
import android.os.Looper;

import com.portfolioiq.BuildConfig;
import com.portfolioiq.domain.repository.ResultCallback;
import com.portfolioiq.domain.repository.StockPriceRepository;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Data-layer implementation of StockPriceRepository around Finnhub's free
 * /quote endpoint (chosen over Alpha Vantage/Twelve Data for Sprint 2 —
 * see project memory for the comparison). Finnhub's free tier has no
 * batch-quote endpoint, so this calls /quote once per distinct ticker,
 * sequentially, on a background thread (Android forbids network calls on
 * the main thread), then posts the combined result back to the main
 * thread the same way FirebaseAuthRepositoryImpl's Firebase listeners do
 * automatically.
 *
 * The API key is never hardcoded here: it comes from BuildConfig, which
 * app/build.gradle.kts populates from local.properties (FINNHUB_API_KEY=...),
 * a file that is gitignored — same "no secrets in source" rule Part One's
 * security-reviewer already enforced for the Firebase key.
 *
 * A ticker whose request fails (bad symbol, rate limit, network error) is
 * simply left out of the returned map rather than failing the whole batch
 * — see GetPortfolioUseCase's Javadoc for how the domain layer handles a
 * missing entry.
 */
public class FinnhubStockPriceRepositoryImpl implements StockPriceRepository {

    private static final String QUOTE_URL = "https://finnhub.io/api/v1/quote?symbol=%s&token=%s";
    private static final String CURRENT_PRICE_FIELD = "c";
    private static final String GENERIC_ERROR = "Could not fetch live prices. Please try again.";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void getQuotes(List<String> tickers, ResultCallback<Map<String, Double>> callback) {
        if (tickers == null || tickers.isEmpty()) {
            callback.onSuccess(new HashMap<>());
            return;
        }

        executor.execute(() -> {
            Map<String, Double> prices = new HashMap<>();
            boolean anySucceeded = tickers.isEmpty();

            for (String ticker : tickers) {
                Double price = fetchQuote(ticker);
                if (price != null) {
                    prices.put(ticker, price);
                    anySucceeded = true;
                }
            }

            boolean succeeded = anySucceeded;
            mainHandler.post(() -> {
                if (succeeded) {
                    callback.onSuccess(prices);
                } else {
                    callback.onError(GENERIC_ERROR);
                }
            });
        });
    }

    /** Returns the current price for one ticker, or null if the lookup failed. */
    private Double fetchQuote(String ticker) {
        HttpURLConnection connection = null;
        try {
            String url = String.format(QUOTE_URL, ticker, BuildConfig.FINNHUB_API_KEY);
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }

            JSONObject json = new JSONObject(body.toString());
            double currentPrice = json.getDouble(CURRENT_PRICE_FIELD);
            // Finnhub returns c=0 for a symbol it doesn't recognize, rather
            // than an error response — treat that as "no quote" too.
            return currentPrice > 0 ? currentPrice : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
