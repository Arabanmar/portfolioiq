package com.portfolioiq.presentation.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

import com.portfolioiq.R;
import com.portfolioiq.data.repository.FinnhubStockPriceRepositoryImpl;
import com.portfolioiq.data.repository.FirestorePortfolioRepositoryImpl;
import com.portfolioiq.domain.model.HoldingPosition;
import com.portfolioiq.domain.model.PortfolioSummary;
import com.portfolioiq.domain.model.StockHolding;
import com.portfolioiq.domain.repository.PortfolioRepository;
import com.portfolioiq.domain.repository.ResultCallback;
import com.portfolioiq.domain.repository.StockPriceRepository;
import com.portfolioiq.domain.usecase.DeleteStockHoldingUseCase;
import com.portfolioiq.domain.usecase.GetPortfolioUseCase;
import com.portfolioiq.presentation.portfolio.AddEditHoldingActivity;

import java.util.Locale;

/**
 * Story 004 — Portfolio Dashboard & Calculations. The app's real main
 * screen after signing in (SignInActivity routes here instead of back to
 * the Welcome screen, now that this exists). Every number shown here
 * comes from GetPortfolioUseCase, which does all the calculation — this
 * class only formats and displays it, per the project's architecture rule.
 *
 * userId is read from FirebaseAuth's current session (same pragmatic
 * choice FirebaseAuthRepositoryImpl already makes) rather than passed
 * through the Activity stack, since Login/Register already leave the
 * user signed in with FirebaseAuth by the time this screen opens.
 */
public class DashboardActivity extends AppCompatActivity {

    private LinearLayout holdingsContainer;
    private TextView totalValueText;
    private TextView totalProfitLossText;
    private TextView errorText;
    private TextView emptyStateText;

    private GetPortfolioUseCase getPortfolioUseCase;
    private DeleteStockHoldingUseCase deleteStockHoldingUseCase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        PortfolioRepository portfolioRepository = new FirestorePortfolioRepositoryImpl();
        StockPriceRepository stockPriceRepository = new FinnhubStockPriceRepositoryImpl();
        getPortfolioUseCase = new GetPortfolioUseCase(portfolioRepository, stockPriceRepository);
        deleteStockHoldingUseCase = new DeleteStockHoldingUseCase(portfolioRepository);

        holdingsContainer = findViewById(R.id.holdingsContainer);
        totalValueText = findViewById(R.id.totalValueText);
        totalProfitLossText = findViewById(R.id.totalProfitLossText);
        errorText = findViewById(R.id.errorText);
        emptyStateText = findViewById(R.id.emptyStateText);
        Button addHoldingButton = findViewById(R.id.addHoldingButton);

        addHoldingButton.setOnClickListener(v ->
                startActivity(new Intent(this, AddEditHoldingActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh every time the screen becomes visible, so returning from
        // Add/Edit/Delete (or just background price movement) shows current
        // numbers instead of the ones from whenever onCreate last ran.
        loadPortfolio();
    }

    private void loadPortfolio() {
        String userId = currentUserId();
        if (userId == null) {
            showError("You're signed out. Please sign in again.");
            return;
        }

        errorText.setVisibility(View.GONE);
        getPortfolioUseCase.execute(userId, new ResultCallback<PortfolioSummary>() {
            @Override
            public void onSuccess(PortfolioSummary summary) {
                render(summary);
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    private void render(PortfolioSummary summary) {
        totalValueText.setText(formatCurrency(summary.getTotalValue()));

        if (summary.getPositions().isEmpty()) {
            totalProfitLossText.setText("No holdings yet");
        } else {
            totalProfitLossText.setText(String.format(Locale.US, "%s (%s)",
                    formatSignedCurrency(summary.getTotalProfitLoss()),
                    formatSignedPercent(summary.getTotalProfitLossPercent())));
        }

        holdingsContainer.removeAllViews();
        emptyStateText.setVisibility(summary.getPositions().isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (HoldingPosition position : summary.getPositions()) {
            View row = inflater.inflate(R.layout.item_holding, holdingsContainer, false);
            bindRow(row, position);
            holdingsContainer.addView(row);
        }
    }

    private void bindRow(View row, HoldingPosition position) {
        StockHolding holding = position.getHolding();

        TextView tickerText = row.findViewById(R.id.tickerText);
        TextView positionValueText = row.findViewById(R.id.positionValueText);
        TextView detailText = row.findViewById(R.id.detailText);
        TextView profitLossText = row.findViewById(R.id.profitLossText);
        TextView allocationText = row.findViewById(R.id.allocationText);
        TextView editHoldingText = row.findViewById(R.id.editHoldingText);
        TextView deleteHoldingText = row.findViewById(R.id.deleteHoldingText);

        tickerText.setText(holding.getTicker());
        positionValueText.setText(formatCurrency(position.getPositionValue()));
        detailText.setText(String.format(Locale.US, "%s shares @ %s",
                formatQuantity(holding.getQuantity()), formatCurrency(holding.getPurchasePrice())));
        profitLossText.setText(String.format(Locale.US, "%s (%s)",
                formatSignedCurrency(position.getProfitLoss()),
                formatSignedPercent(position.getProfitLossPercent())));
        allocationText.setText(String.format(Locale.US, "%.1f%% of portfolio%s",
                position.getAllocationPercent(), position.isPriceLive() ? "" : " · price unavailable"));

        editHoldingText.setOnClickListener(v ->
                startActivity(AddEditHoldingActivity.editIntent(this, holding)));
        deleteHoldingText.setOnClickListener(v -> deleteHolding(holding));
    }

    private void deleteHolding(StockHolding holding) {
        String userId = currentUserId();
        if (userId == null) {
            showError("You're signed out. Please sign in again.");
            return;
        }
        deleteStockHoldingUseCase.execute(userId, holding.getId(), new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                loadPortfolio();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
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

    private String formatCurrency(double value) {
        return String.format(Locale.US, "$%,.2f", value);
    }

    private String formatSignedCurrency(double value) {
        return String.format(Locale.US, "%s$%,.2f", value >= 0 ? "+" : "-", Math.abs(value));
    }

    private String formatSignedPercent(double value) {
        return String.format(Locale.US, "%s%.1f%%", value >= 0 ? "+" : "-", Math.abs(value));
    }

    private String formatQuantity(double quantity) {
        // Whole-share counts print without a trailing ".0"; fractional
        // shares keep up to 4 decimal places.
        if (quantity == Math.floor(quantity)) {
            return String.format(Locale.US, "%.0f", quantity);
        }
        return String.format(Locale.US, "%.4f", quantity);
    }
}
