from django.db import models


class Company(models.Model):
    """Maps to the 'Companies' table owned by Spring Boot / JPA."""
    company_id    = models.CharField(max_length=255, primary_key=True, db_column='company_id')
    symbol        = models.CharField(max_length=255)
    name          = models.CharField(max_length=255)
    initial_price = models.BigIntegerField(db_column='initial_price', default=0,
                                           help_text='Price set by admin at creation time.')
    current_price = models.BigIntegerField(db_column='current_price', default=0,
                                           help_text='Live market price — updated after each trade.')
    total_shares  = models.IntegerField(db_column='total_shares', default=0,
                                        help_text='Maximum shares available for trading (set at creation).')
    available_shares = models.IntegerField(db_column='available_shares', default=0,
                                           help_text='Shares still available for new BUY orders.')

    class Meta:
        managed = False          # Spring Boot owns DDL; Django only reads/writes data
        db_table = 'Companies'
        verbose_name = 'Company'
        verbose_name_plural = 'Companies'

    def __str__(self):
        return f"{self.symbol} – {self.name}"


class Order(models.Model):
    """Maps to the 'Orders' table created by Spring Boot / JPA."""
    order_id     = models.CharField(max_length=255, primary_key=True, db_column='order_id')
    symbol       = models.CharField(max_length=255)
    buy_sell     = models.BooleanField(db_column='buy_sell')        # True = BUY
    market_limit = models.BooleanField(db_column='market_limit')    # True = MARKET
    status       = models.BooleanField()                            # True = Filled
    shares         = models.IntegerField()
    initial_shares = models.IntegerField(db_column='initial_shares', default=0,
                                         help_text='Original quantity of the order at creation.')
    price        = models.BigIntegerField()
    entry_time   = models.BigIntegerField(db_column='entry_time')
    event_time   = models.BigIntegerField(db_column='event_time', null=True, blank=True)
    final_price   = models.BigIntegerField(db_column='final_price', null=True, blank=True)
    company_order = models.BooleanField(db_column='company_order', default=False,
                                        help_text='True for system-placed orders (IPO, relisting, admin). Cannot be cancelled from the frontend.')
    company      = models.ForeignKey(
        Company,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        db_column='company_id',
        related_name='orders',
    )

    class Meta:
        managed = False
        db_table = 'Orders'
        verbose_name = 'Order'
        verbose_name_plural = 'Orders'

    def __str__(self):
        val = self.buy_sell
        is_buy = val == b'\x01' or val is True or val == 1
        side = "BUY" if is_buy else "SELL"
        val_ml = self.market_limit
        order_type = "MARKET" if (val_ml == b'\x01' or val_ml is True or val_ml == 1) else "LIMIT"
        return f"{self.symbol} | {side} {order_type} | {self.shares} @ {self.price}"


class Trade(models.Model):
    """Maps to the 'Trades' table created by Spring Boot / JPA."""
    trade_id      = models.CharField(max_length=255, primary_key=True, db_column='trade_id')
    symbol        = models.CharField(max_length=255)
    buy_order_id  = models.CharField(max_length=255, db_column='buy_order_id')
    sell_order_id = models.CharField(max_length=255, db_column='sell_order_id')
    price         = models.BigIntegerField()
    quantity      = models.IntegerField()
    trade_time    = models.BigIntegerField(db_column='trade_time', null=True, blank=True)
    company       = models.ForeignKey(
        Company,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        db_column='company_id',
        related_name='trades',
    )

    class Meta:
        managed = False
        db_table = 'Trades'
        verbose_name = 'Trade'
        verbose_name_plural = 'Trades'

    def __str__(self):
        return f"{self.trade_id} | {self.symbol} | qty={self.quantity} @ {self.price}"
