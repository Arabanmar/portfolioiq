package com.portfolioiq.domain.model;

/**
 * Domain model for one stock a user holds. Plain data holder — no
 * Firebase/Firestore types leak into the domain layer.
 *
 * id is the Firestore document id. It is null for a holding that has not
 * been saved yet (e.g. the object built from the Add Holding form before
 * PortfolioRepository.addHolding assigns it one).
 */
public class StockHolding {

    private final String id;
    private final String ticker;
    private final double quantity;
    private final double purchasePrice;

    public StockHolding(String id, String ticker, double quantity, double purchasePrice) {
        this.id = id;
        this.ticker = ticker;
        this.quantity = quantity;
        this.purchasePrice = purchasePrice;
    }

    public String getId() {
        return id;
    }

    public String getTicker() {
        return ticker;
    }

    public double getQuantity() {
        return quantity;
    }

    public double getPurchasePrice() {
        return purchasePrice;
    }

    /** Returns a copy of this holding with the given id (used once Firestore assigns one). */
    public StockHolding withId(String newId) {
        return new StockHolding(newId, ticker, quantity, purchasePrice);
    }
}
