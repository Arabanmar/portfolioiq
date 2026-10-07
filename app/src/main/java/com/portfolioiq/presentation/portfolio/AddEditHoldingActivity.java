package com.portfolioiq.presentation.portfolio;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

import com.portfolioiq.R;
import com.portfolioiq.data.repository.FinnhubStockSearchRepositoryImpl;
import com.portfolioiq.data.repository.FirestorePortfolioRepositoryImpl;
import com.portfolioiq.domain.model.StockHolding;
import com.portfolioiq.domain.model.StockSymbol;
import com.portfolioiq.domain.repository.PortfolioRepository;
import com.portfolioiq.domain.repository.ResultCallback;
import com.portfolioiq.domain.repository.StockSearchRepository;
import com.portfolioiq.domain.usecase.AddStockHoldingUseCase;
import com.portfolioiq.domain.usecase.EditStockHoldingUseCase;
import com.portfolioiq.domain.usecase.SearchStocksUseCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Story 003 — Add/Edit Stock Holding.
 *
 * One Activity handles both Add and Edit: DashboardActivity starts it with
 * no extras for Add, or with EXTRA_HOLDING_ID (+ the current ticker/
 * quantity/price to prefill) for Edit. userId comes from FirebaseAuth's
 * current session the same pragmatic way DashboardActivity reads it,
 * rather than being threaded through every Intent.
 *
 * Sprint 3 (doctor's feedback): the ticker field suggests real stocks as the
 * user types (SearchStocksUseCase). Picking one fills in its symbol. Whether
 * the saved ticker is real is decided by the Add/Edit use cases, not here.
 */
public class AddEditHoldingActivity extends AppCompatActivity {

    public static final String EXTRA_HOLDING_ID = "holding_id";
    public static final String EXTRA_TICKER = "ticker";
    public static final String EXTRA_QUANTITY = "quantity";
    public static final String EXTRA_PURCHASE_PRICE = "purchase_price";

    /** Wait this long after the last keystroke before searching, so typing "APPLE" is one call, not five. */
    private static final long SEARCH_DELAY_MS = 350;

    private AutoCompleteTextView tickerInput;
    private EditText quantityInput;
    private EditText purchasePriceInput;
    private TextView errorText;
    private TextView screenTitleText;
    private Button saveButton;

    private AddStockHoldingUseCase addStockHoldingUseCase;
    private EditStockHoldingUseCase editStockHoldingUseCase;
    private SearchStocksUseCase searchStocksUseCase;
    private String editingHoldingId;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private final List<StockSymbol> currentSuggestions = new ArrayList<>();
    private SuggestionAdapter suggestionAdapter;
    /** True while the code (not the user) is setting the ticker text, so it doesn't trigger a search. */
    private boolean settingTickerText = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_holding);

        PortfolioRepository portfolioRepository = new FirestorePortfolioRepositoryImpl();
        StockSearchRepository stockSearchRepository = new FinnhubStockSearchRepositoryImpl();
        addStockHoldingUseCase = new AddStockHoldingUseCase(portfolioRepository, stockSearchRepository);
        editStockHoldingUseCase = new EditStockHoldingUseCase(portfolioRepository, stockSearchRepository);
        searchStocksUseCase = new SearchStocksUseCase(stockSearchRepository);

        screenTitleText = findViewById(R.id.screenTitleText);
        tickerInput = findViewById(R.id.tickerInput);
        quantityInput = findViewById(R.id.quantityInput);
        purchasePriceInput = findViewById(R.id.purchasePriceInput);
        errorText = findViewById(R.id.errorText);
        saveButton = findViewById(R.id.saveButton);
        TextView cancelText = findViewById(R.id.cancelText);

        editingHoldingId = getIntent().getStringExtra(EXTRA_HOLDING_ID);
        if (editingHoldingId != null) {
            screenTitleText.setText("Edit Holding");
            setTickerText(getIntent().getStringExtra(EXTRA_TICKER));
            quantityInput.setText(getIntent().getStringExtra(EXTRA_QUANTITY));
            purchasePriceInput.setText(getIntent().getStringExtra(EXTRA_PURCHASE_PRICE));
        }

        setUpTickerSuggestions();
        saveButton.setOnClickListener(v -> attemptSave());
        cancelText.setOnClickListener(v -> finish());
    }

    @Override
    protected void onDestroy() {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }
        super.onDestroy();
    }

    private void setUpTickerSuggestions() {
        suggestionAdapter = new SuggestionAdapter(this);
        tickerInput.setAdapter(suggestionAdapter);
        tickerInput.setOnItemClickListener((parent, view, position, id) -> {
            if (position < currentSuggestions.size()) {
                setTickerText(currentSuggestions.get(position).getSymbol());
                tickerInput.dismissDropDown();
            }
        });
        tickerInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!settingTickerText) {
                    scheduleSearch(s.toString());
                }
            }
        });
    }

    private void scheduleSearch(String text) {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }
        String query = text.trim();
        if (query.isEmpty()) {
            showSuggestions(Collections.emptyList());
            return;
        }
        pendingSearch = () -> searchStocksUseCase.execute(query, new ResultCallback<List<StockSymbol>>() {
            @Override
            public void onSuccess(List<StockSymbol> result) {
                // Ignore answers to an older query the user has already typed past.
                if (!query.equals(tickerInput.getText().toString().trim())) {
                    return;
                }
                showSuggestions(result);
                if (!result.isEmpty() && tickerInput.hasFocus()) {
                    tickerInput.showDropDown();
                }
            }

            @Override
            public void onError(String message) {
                // Suggestions are a convenience; the real-stock check still runs on Save.
            }
        });
        searchHandler.postDelayed(pendingSearch, SEARCH_DELAY_MS);
    }

    private void showSuggestions(List<StockSymbol> symbols) {
        currentSuggestions.clear();
        currentSuggestions.addAll(symbols);
        List<String> lines = new ArrayList<>();
        for (StockSymbol symbol : symbols) {
            lines.add(symbol.getDisplayText());
        }
        suggestionAdapter.setItems(lines);
    }

    private void setTickerText(String text) {
        // Picking a suggestion first makes the field show the whole line
        // ("AAPL - APPLE INC"), which queues a search; cancel it, it's stale.
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }
        settingTickerText = true;
        tickerInput.setText(text, false);
        tickerInput.setSelection(tickerInput.getText().length());
        settingTickerText = false;
    }

    private void attemptSave() {
        errorText.setVisibility(View.GONE);
        saveButton.setEnabled(false);

        String userId = currentUserId();
        if (userId == null) {
            saveButton.setEnabled(true);
            showError("You're signed out. Please sign in again.");
            return;
        }

        String ticker = tickerInput.getText().toString();
        String quantity = quantityInput.getText().toString();
        String purchasePrice = purchasePriceInput.getText().toString();

        if (editingHoldingId != null) {
            editStockHoldingUseCase.execute(userId, editingHoldingId, ticker, quantity, purchasePrice,
                    new ResultCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            finish();
                        }

                        @Override
                        public void onError(String message) {
                            saveButton.setEnabled(true);
                            showError(message);
                        }
                    });
        } else {
            addStockHoldingUseCase.execute(userId, ticker, quantity, purchasePrice,
                    new ResultCallback<StockHolding>() {
                        @Override
                        public void onSuccess(StockHolding result) {
                            finish();
                        }

                        @Override
                        public void onError(String message) {
                            saveButton.setEnabled(true);
                            showError(message);
                        }
                    });
        }
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private String currentUserId() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }

    /**
     * Shows the suggestions exactly as the search returned them. The default
     * ArrayAdapter filter would hide matches by company name (e.g. typing
     * "apple" for "AAPL - APPLE INC"), so filtering is a pass-through here.
     */
    private static class SuggestionAdapter extends ArrayAdapter<String> {

        private volatile List<String> snapshot = Collections.emptyList();

        SuggestionAdapter(Context context) {
            super(context, android.R.layout.simple_dropdown_item_1line);
        }

        void setItems(List<String> items) {
            snapshot = new ArrayList<>(items);
            clear();
            addAll(items);
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    List<String> current = snapshot;
                    FilterResults results = new FilterResults();
                    results.values = current;
                    results.count = current.size();
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    notifyDataSetChanged();
                }
            };
        }
    }

    /** Convenience for DashboardActivity to build the Edit intent. */
    public static Intent editIntent(android.content.Context context, StockHolding holding) {
        Intent intent = new Intent(context, AddEditHoldingActivity.class);
        intent.putExtra(EXTRA_HOLDING_ID, holding.getId());
        intent.putExtra(EXTRA_TICKER, holding.getTicker());
        intent.putExtra(EXTRA_QUANTITY, String.valueOf(holding.getQuantity()));
        intent.putExtra(EXTRA_PURCHASE_PRICE, String.valueOf(holding.getPurchasePrice()));
        return intent;
    }
}
