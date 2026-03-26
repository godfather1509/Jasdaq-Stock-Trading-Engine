from django.contrib import admin
from .models import Company, Order, Trade


# ─── Inlines ────────────────────────────────────────────────────────────────
class OrderInline(admin.TabularInline):
    model = Order
    # Use method names in fields/readonly_fields to avoid Django trying to 
    # use the boolean icon on binary BIT(1) data which causes a crash.
    fields = ('order_id', 'symbol', 'side_label', 'type_label', 'status_label', 'shares', 'price', 'entry_time')
    readonly_fields = fields
    can_delete = False
    extra = 0
    show_change_link = True

    def side_label(self, obj):
        # Handle cases where BIT(1) comes back as bytes b'\x01' / b'\x00'
        val = obj.buy_sell
        is_true = (val == b'\x01' or val == True or val == 1)
        return 'BUY' if is_true else 'SELL'
    side_label.short_description = 'Side'

    def type_label(self, obj):
        val = obj.market_limit
        is_true = (val == b'\x01' or val == True or val == 1)
        return 'MARKET' if is_true else 'LIMIT'
    type_label.short_description = 'Type'

    def status_label(self, obj):
        val = obj.status
        is_true = (val == b'\x01' or val == True or val == 1)
        return 'Filled' if is_true else 'Pending'
    status_label.short_description = 'Status'

class TradeInline(admin.TabularInline):
    model = Trade
    readonly_fields = ('trade_id', 'symbol', 'quantity', 'price', 'buy_order_id', 'sell_order_id', 'trade_time')
    can_delete = False
    extra = 0
    show_change_link = True


# ─── Company ────────────────────────────────────────────────────────────────
@admin.register(Company)
class CompanyAdmin(admin.ModelAdmin):
    list_display  = ('symbol', 'name', 'current_price', 'shares', 'company_id')
    search_fields = ('symbol', 'name')
    list_filter   = ()
    ordering      = ('symbol',)
    inlines       = [OrderInline, TradeInline]

    def save_model(self, request, obj, form, change):
        import uuid
        import time
        from .models import Order
        
        is_new = obj._state.adding
        if is_new and not obj.company_id:
            obj.company_id = str(uuid.uuid4())
            
        super().save_model(request, obj, form, change)
        
        if is_new:
            # Create the initial SELL limit order with all the shares
            order_id = str(uuid.uuid4())
            curr_time = int(time.time() * 1000)
            Order.objects.create(
                order_id=order_id,
                symbol=obj.symbol,
                buy_sell=False,     # SELL
                market_limit=False, # LIMIT
                status=False,       # PENDING
                shares=obj.shares,
                price=obj.current_price,
                entry_time=curr_time,
                event_time=curr_time,
                final_price=0,
                company=obj
            )


# ─── Order ──────────────────────────────────────────────────────────────────
@admin.register(Order)
class OrderAdmin(admin.ModelAdmin):
    list_display  = ('order_id', 'symbol', 'side', 'order_type', 'status_label',
                     'shares', 'price', 'entry_time')
    list_filter   = ('buy_sell', 'market_limit', 'status', 'symbol')
    search_fields = ('order_id', 'symbol')
    ordering      = ('-entry_time',)

    def side(self, obj):
        val = obj.buy_sell
        is_true = (val == b'\x01' or val == True or val == 1)
        return 'BUY' if is_true else 'SELL'
    side.short_description = 'Side'

    def order_type(self, obj):
        val = obj.market_limit
        is_true = (val == b'\x01' or val == True or val == 1)
        return 'MARKET' if is_true else 'LIMIT'
    order_type.short_description = 'Type'

    def status_label(self, obj):
        val = obj.status
        is_true = (val == b'\x01' or val == True or val == 1)
        return 'Filled' if is_true else 'Pending'
    status_label.short_description = 'Status'


# ─── Trade ──────────────────────────────────────────────────────────────────
@admin.register(Trade)
class TradeAdmin(admin.ModelAdmin):
    list_display  = ('trade_id', 'symbol', 'quantity', 'price', 'buy_order_id',
                     'sell_order_id', 'trade_time')
    list_filter   = ('symbol',)
    search_fields = ('trade_id', 'symbol', 'buy_order_id', 'sell_order_id')
    ordering      = ('-trade_time',)
