package com.portfolioiq.presentation.portfolio;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

import com.portfolioiq.R;
import com.portfolioiq.data.repository.FirestorePortfolioRepositoryImpl;
import com.portfolioiq.domain.model.StockHolding;
import com.portfolioiq.domain.repository.PortfolioRepository;
import com.portfolioiq.domain.repository.ResultCallback;
import com.portfolioiq.domain.usecase.AddStockHoldingUseCase;
import com.portfolioiq.domain.usecase.EditStockHoldingUseCase;

/**
 * Story 003 — Add/Edit Stock Holding.
 *
 * One Activity handles both Add and Edit: DashboardActivity starts it with
 * no extras for Add, or with EXTRA_HOLDING_ID (+ the current ticker/
 * quantity/price to prefill) for Edit. userId comes from FirebaseAuth's
 * current session the same pragmatic way DashboardActivity reads it,
 * rather than being threaded through every Intent.
 */
public class AddEditHoldingActivity extends AppCompatActivity {

    public static final String EXTRA_HOLDING_ID = "holding_id";
    public static final String EXTRA_TICKER = "ticker";
    public static final String EXTRA_QUANTITY = "quantity";
    public static final String EXTRA_PURCHASE_PRICE = "purchase_price";

    private EditText tickerInput;
    private EditText quantityInput;
    private EditText purchasePriceInput;
    private TextView errorText;
    private TextView screenTitleText;
    private Button saveButton;

    private AddStockHoldingUseCase addStockHoldingUseCase;
    private EditStockHoldingUseCase editStockHoldingUseCase;
    private String editingHoldingId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_holding);

        PortfolioRepository portfolioRepository = new FirestorePortfolioRepositoryImpl();
        addStockHoldingUseCase = new AddStockHoldingUseCase(portfolioRepository);
        editStockHoldingUseCase = new EditStockHoldingUseCase(portfolioRepository);

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
            tickerInput.setText(getIntent().getStringExtra(EXTRA_TICKER));
            quantityInput.setText(getIntent().getStringExtra(EXTRA_QUANTITY));
            purchasePriceInput.setText(getIntent().getStringExtra(EXTRA_PURCHASE_PRICE));
        }

        saveButton.setOnClickListener(v -> attemptSave());
        cancelText.setOnClickListener(v -> finish());
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
