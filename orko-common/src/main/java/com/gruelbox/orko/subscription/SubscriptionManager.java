package com.gruelbox.orko.subscription;

import static com.gruelbox.orko.marketdata.MarketDataType.BALANCE;
import static com.gruelbox.orko.marketdata.MarketDataType.OPEN_ORDERS;
import static com.gruelbox.orko.marketdata.MarketDataType.ORDERBOOK;
import static com.gruelbox.orko.marketdata.MarketDataType.TICKER;
import static com.gruelbox.orko.marketdata.MarketDataType.TRADES;
import static com.gruelbox.orko.marketdata.MarketDataType.USER_TRADE_HISTORY;

import java.util.Collection;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.gruelbox.orko.db.Transactionally;
import com.gruelbox.orko.marketdata.ExchangeEventRegistry;
import com.gruelbox.orko.marketdata.MarketDataSubscription;
import com.gruelbox.orko.marketdata.ExchangeEventRegistry.ExchangeEventSubscription;
import com.gruelbox.orko.spi.TickerSpec;
import com.gruelbox.orko.util.SafelyClose;

import io.dropwizard.lifecycle.Managed;

@Singleton
class SubscriptionManager implements Managed {

  private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionManager.class);

  private final SubscriptionAccess subscriptionAccess;
  private final ExchangeEventRegistry exchangeEventRegistry;
  private final Transactionally transactionally;

  private volatile ExchangeEventSubscription subscription;

  @Inject
  SubscriptionManager(SubscriptionAccess subscriptionAccess,
                               ExchangeEventRegistry exchangeEventRegistry,
                               Transactionally transactionally) {
    this.subscriptionAccess = subscriptionAccess;
    this.exchangeEventRegistry = exchangeEventRegistry;
    this.transactionally = transactionally;
  }

  @Override
  public void start() throws Exception {
    subscription = exchangeEventRegistry.subscribe();
    transactionally.run(Transactionally.READ_ONLY_UNIT, this::update);
  }

  @Override
  public void stop() throws Exception {
    SafelyClose.the(subscription);
  }

  private void update() {
    Set<MarketDataSubscription> all = FluentIterable.from(subscriptionAccess.all()).transformAndConcat(this::subscriptionsFor).toSet();
    LOGGER.info("Updating permanent subscriptions to {}", all);
    subscription = subscription.replace(all);
  }

  void add(TickerSpec spec) {
    subscriptionAccess.add(spec);
    update();
  }

  void remove(TickerSpec spec) {
    subscriptionAccess.remove(spec);
    update();
  }

  private Collection<MarketDataSubscription> subscriptionsFor(TickerSpec spec) {
    return ImmutableList.of(
      MarketDataSubscription.create(spec, TICKER),
      MarketDataSubscription.create(spec, ORDERBOOK),
      MarketDataSubscription.create(spec, OPEN_ORDERS),
      MarketDataSubscription.create(spec, USER_TRADE_HISTORY),
      MarketDataSubscription.create(spec, BALANCE),
      MarketDataSubscription.create(spec, TRADES)
    );
  }
}