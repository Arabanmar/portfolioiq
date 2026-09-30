package com.portfolioiq.data.repository;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import com.portfolioiq.domain.model.StockHolding;
import com.portfolioiq.domain.repository.PortfolioRepository;
import com.portfolioiq.domain.repository.ResultCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-layer implementation of PortfolioRepository around Firestore.
 * Holdings live at users/{userId}/holdings/{holdingId} — nested under the
 * same "users" collection FirebaseAuthRepositoryImpl already writes a
 * profile document into, so each user's holdings are naturally scoped to
 * them without a separate top-level collection + a userId filter.
 */
public class FirestorePortfolioRepositoryImpl implements PortfolioRepository {

    private static final String USERS_COLLECTION = "users";
    private static final String HOLDINGS_SUBCOLLECTION = "holdings";
    private static final String GENERIC_ERROR = "Could not reach your portfolio. Please try again.";

    private static final String FIELD_TICKER = "ticker";
    private static final String FIELD_QUANTITY = "quantity";
    private static final String FIELD_PURCHASE_PRICE = "purchasePrice";

    private final FirebaseFirestore firestore;

    public FirestorePortfolioRepositoryImpl() {
        this.firestore = FirebaseFirestore.getInstance();
    }

    @Override
    public void getHoldings(String userId, ResultCallback<List<StockHolding>> callback) {
        holdingsCollection(userId).get()
                .addOnSuccessListener(querySnapshot -> {
                    List<StockHolding> holdings = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        holdings.add(toHolding(doc.getId(), doc.getData()));
                    }
                    callback.onSuccess(holdings);
                })
                .addOnFailureListener(exception -> callback.onError(GENERIC_ERROR));
    }

    @Override
    public void addHolding(String userId, StockHolding holding, ResultCallback<StockHolding> callback) {
        DocumentReference newDoc = holdingsCollection(userId).document();
        newDoc.set(toMap(holding))
                .addOnSuccessListener(unused -> callback.onSuccess(holding.withId(newDoc.getId())))
                .addOnFailureListener(exception -> callback.onError(GENERIC_ERROR));
    }

    @Override
    public void updateHolding(String userId, StockHolding holding, ResultCallback<Void> callback) {
        holdingsCollection(userId).document(holding.getId())
                .set(toMap(holding))
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(exception -> callback.onError(GENERIC_ERROR));
    }

    @Override
    public void deleteHolding(String userId, String holdingId, ResultCallback<Void> callback) {
        holdingsCollection(userId).document(holdingId).delete()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(exception -> callback.onError(GENERIC_ERROR));
    }

    private com.google.firebase.firestore.CollectionReference holdingsCollection(String userId) {
        return firestore.collection(USERS_COLLECTION).document(userId).collection(HOLDINGS_SUBCOLLECTION);
    }

    private Map<String, Object> toMap(StockHolding holding) {
        Map<String, Object> map = new HashMap<>();
        map.put(FIELD_TICKER, holding.getTicker());
        map.put(FIELD_QUANTITY, holding.getQuantity());
        map.put(FIELD_PURCHASE_PRICE, holding.getPurchasePrice());
        return map;
    }

    private StockHolding toHolding(String id, Map<String, Object> data) {
        String ticker = String.valueOf(data.get(FIELD_TICKER));
        double quantity = ((Number) data.get(FIELD_QUANTITY)).doubleValue();
        double purchasePrice = ((Number) data.get(FIELD_PURCHASE_PRICE)).doubleValue();
        return new StockHolding(id, ticker, quantity, purchasePrice);
    }
}
