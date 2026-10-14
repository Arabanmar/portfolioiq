package com.portfolioiq.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.portfolioiq.BuildConfig;
import com.portfolioiq.domain.model.AnalysisProgress;
import com.portfolioiq.domain.model.HoldingPosition;
import com.portfolioiq.domain.model.PortfolioAnalysis;
import com.portfolioiq.domain.model.StockAnalysis;
import com.portfolioiq.domain.repository.AiAnalysisRepository;
import com.portfolioiq.domain.repository.ResultCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Data-layer implementation of AiAnalysisRepository: plain HTTP + JSON calls
 * to the PortfolioIQ AI server (ai-server/), which runs TradingAgents.
 *
 * The server address comes from BuildConfig.AI_SERVER_URL (local.properties
 * AI_SERVER_URL, default http://10.0.2.2:8000 = "this laptop" as seen from
 * the Android emulator). The optional shared token comes from
 * AI_SERVER_TOKEN. No AI key is ever in the app: the Gemini key stays in
 * ai-server/.env on the laptop.
 */
public class HttpAiAnalysisRepositoryImpl implements AiAnalysisRepository {

    private static final String TAG = "PortfolioIQ-AI";
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final String GENERIC_ERROR = "Something went wrong with the AI analysis. Please try again.";

    private final String baseUrl = trimSlash(BuildConfig.AI_SERVER_URL);
    private final String token = BuildConfig.AI_SERVER_TOKEN;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void startAnalysis(List<HoldingPosition> positions, ResultCallback<String> callback) {
        run(callback, () -> {
            JSONArray holdings = new JSONArray();
            for (HoldingPosition p : positions) {
                JSONObject h = new JSONObject();
                h.put("ticker", p.getHolding().getTicker());
                h.put("quantity", p.getHolding().getQuantity());
                h.put("purchasePrice", p.getHolding().getPurchasePrice());
                if (p.isPriceLive()) {
                    h.put("currentPrice", p.getCurrentPrice());
                }
                holdings.put(h);
            }
            JSONObject body = new JSONObject().put("holdings", holdings);
            return request("POST", "/analysis", body, READ_TIMEOUT_MS).getString("jobId");
        });
    }

    @Override
    public void getProgress(String jobId, ResultCallback<AnalysisProgress> callback) {
        run(callback, () -> parseProgress(request("GET", "/analysis/" + jobId, null, READ_TIMEOUT_MS)));
    }

    @Override
    public void askQuestion(String jobId, String question, ResultCallback<String> callback) {
        run(callback, () -> {
            JSONObject body = new JSONObject().put("jobId", jobId).put("question", question);
            // An answer takes one Gemini call, which can take a while.
            return request("POST", "/chat", body, 90_000).getString("answer");
        });
    }

    // ---- plumbing ----

    private interface Call<T> {
        T run() throws Exception;
    }

    /** Runs a call on the background thread and delivers the result on the main thread. */
    private <T> void run(ResultCallback<T> callback, Call<T> call) {
        executor.execute(() -> {
            try {
                T result = call.run();
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (ServerError e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            } catch (IOException e) {
                // Logged so Android Studio's Logcat shows the real cause (filter: PortfolioIQ-AI).
                Log.w(TAG, "AI server call to " + baseUrl + " failed", e);
                String message = connectionMessage(e);
                mainHandler.post(() -> callback.onError(message));
            } catch (Exception e) {
                Log.w(TAG, "AI server response could not be read", e);
                mainHandler.post(() -> callback.onError(GENERIC_ERROR));
            }
        });
    }

    /**
     * Says which kind of network problem happened and which address the app
     * used, so "can't reach the server" can be told apart from "the server is
     * too slow" when debugging.
     */
    String connectionMessage(IOException e) {
        if (e instanceof SocketTimeoutException) {
            return "The AI server at " + baseUrl + " took too long to answer. "
                    + "Check the server window is still running (press Esc in it if its title says Select), then tap Retry.";
        }
        if (e instanceof ConnectException) {
            return "Can't connect to the AI server at " + baseUrl + ". "
                    + "Start it with ai-server/start_server.bat, then tap Retry.";
        }
        return "Can't reach the AI server at " + baseUrl + " (" + e.getClass().getSimpleName() + "). "
                + "Make sure it's running (ai-server/start_server.bat), then tap Retry.";
    }

    /** A user-safe message the server sent back with an error status. */
    private static class ServerError extends Exception {
        ServerError(String message) {
            super(message);
        }
    }

    private JSONObject request(String method, String path, JSONObject body, int readTimeoutMs) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestProperty("Accept", "application/json");
            if (token != null && !token.isEmpty()) {
                connection.setRequestProperty("X-PIQ-Token", token);
            }
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = stream == null ? "" : readAll(stream);
            if (code >= 400) {
                throw new ServerError(errorMessage(text, code));
            }
            return new JSONObject(text);
        } finally {
            connection.disconnect();
        }
    }

    /** The server's "detail" text when it is a plain sentence (it is written to be shown to users). */
    private static String errorMessage(String text, int code) {
        try {
            Object detail = new JSONObject(text).opt("detail");
            if (detail instanceof String && !((String) detail).isEmpty()) {
                return (String) detail;
            }
        } catch (Exception ignored) {
            // not JSON; fall through
        }
        if (code == 422) {
            return "The AI server didn't accept the portfolio data.";
        }
        return GENERIC_ERROR;
    }

    private static AnalysisProgress parseProgress(JSONObject job) throws Exception {
        String status = job.getString("status");
        JSONObject progress = job.getJSONObject("progress");
        int done = progress.optInt("done", 0);
        int total = progress.optInt("total", 0);
        String current = progress.isNull("current") ? null : progress.getString("current");

        if ("done".equals(status)) {
            return new AnalysisProgress(AnalysisProgress.Status.DONE, done, total, null,
                    parseAnalysis(job.getJSONObject("result")), null);
        }
        if ("failed".equals(status)) {
            String error = job.isNull("error") ? GENERIC_ERROR : job.getString("error");
            return new AnalysisProgress(AnalysisProgress.Status.FAILED, done, total, null, null, error);
        }
        return new AnalysisProgress(AnalysisProgress.Status.RUNNING, done, total, current, null, null);
    }

    private static PortfolioAnalysis parseAnalysis(JSONObject result) throws Exception {
        JSONObject metrics = result.getJSONObject("metrics");
        JSONObject risk = result.getJSONObject("risk");
        JSONObject summary = result.getJSONObject("summary");

        List<StockAnalysis> stocks = new ArrayList<>();
        JSONArray stockArray = result.getJSONArray("stocks");
        for (int i = 0; i < stockArray.length(); i++) {
            JSONObject s = stockArray.getJSONObject(i);
            stocks.add(new StockAnalysis(s.getString("ticker"), s.getString("rating"), s.optString("decision", "")));
        }

        return new PortfolioAnalysis(
                result.optString("analysisDate", ""),
                risk.getDouble("riskPercent"),
                risk.getString("riskLevel"),
                metrics.getDouble("diversificationScore"),
                metrics.getString("largestPosition"),
                metrics.getDouble("largestPositionPercent"),
                summary.optString("summary", ""),
                stringList(summary.optJSONArray("strengths")),
                stringList(summary.optJSONArray("weaknesses")),
                summary.optString("diversificationComment", ""),
                stocks);
    }

    private static List<String> stringList(JSONArray array) {
        List<String> list = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                list.add(array.optString(i));
            }
        }
        return list;
    }

    private static String readAll(InputStream stream) throws IOException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line);
            }
        }
        return text.toString();
    }

    private static String trimSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
