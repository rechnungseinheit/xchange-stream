package info.bitrich.xchangestream.coinbasepro;

import static org.knowm.xchange.coinbasepro.CoinbaseProAdapters.adaptTradeHistory;

import org.knowm.xchange.coinbasepro.dto.trade.CoinbaseProFill;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.bitrich.xchangestream.coinbasepro.dto.CoinbaseProWebSocketTransaction;
import info.bitrich.xchangestream.core.StreamingTradeService;
import io.reactivex.Observable;

/**
 * Created by luca on 4/3/17.
 */
public class CoinbaseProStreamingTradeService implements StreamingTradeService {

    private static final Logger LOG = LoggerFactory.getLogger(CoinbaseProStreamingTradeService.class);

    private static final String MATCH = "match";

    private final CoinbaseProStreamingService service;

    CoinbaseProStreamingTradeService(CoinbaseProStreamingService service) {
        this.service = service;
    }

    private boolean containsPair(List<CurrencyPair> pairs, CurrencyPair pair) {
        for (CurrencyPair item : pairs) {
            if (item.compareTo(pair) == 0) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Observable<UserTrade> getUserTrades(CurrencyPair currencyPair, Object... args) {
        if (!containsPair(service.getProduct().getUserTrades(), currencyPair))
            throw new UnsupportedOperationException(String.format("The currency pair %s is not subscribed for user trades", currencyPair));
        if (!service.isAuthenticated()) {
            throw new ExchangeSecurityException("Not authenticated");
        }
        return service.getRawWebSocketTransactions(currencyPair, true)
                .filter(message -> message.getType().equals(MATCH))
                .filter((CoinbaseProWebSocketTransaction s) -> s.getUserId() != null)
                .map((CoinbaseProWebSocketTransaction s) -> s.toCoinbaseProFill())
                .map((CoinbaseProFill f) -> adaptTradeHistory(new CoinbaseProFill[]{f}))
                .map((UserTrades h) -> h.getUserTrades().get(0));
    }

    /**
     * <p><strong>Warning:</strong> the order change stream is not yet fully
     * implemented for Coinbase Pro. Orders are not fully populated, containing only
     * the values changed since the last update. Other values will be null. The
     * intention is to resolve this. See
     * https://github.com/bitrich-info/xchange-stream/issues/274 for progress.</p>
     */
    @Override
    public Observable<Order> getOrderChanges(CurrencyPair currencyPair, Object... args) {
        if (!containsPair(service.getProduct().getOrders(), currencyPair))
            throw new UnsupportedOperationException(String.format("The currency pair %s is not subscribed for orders", currencyPair));
        if (!service.isAuthenticated()) {
            throw new ExchangeSecurityException("Not authenticated");
        }
        LOG.warn("The order change stream is not yet fully implemented for Coinbase Pro. "
                + "Orders are not fully populated, containing only the values changed since "
                + "the last update. Other values will be null.");
        return service.getRawWebSocketTransactions(currencyPair, true)
                .filter(s -> s.getUserId() != null)
                .map(CoinbaseProStreamingAdapters::adaptOrder);
    }

    /**
     * Web socket transactions related to the specified currency, in their raw format.
     *
     * @param currencyPair The currency pair.
     * @return The stream.
     */
    public Observable<CoinbaseProWebSocketTransaction> getRawWebSocketTransactions(CurrencyPair currencyPair, boolean filterChannelName) {
        return service.getRawWebSocketTransactions(currencyPair, filterChannelName);
    }
}