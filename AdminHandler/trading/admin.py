from django.contrib import admin
from .models import Company, Order, Trade


# ─── Company ────────────────────────────────────────────────────────────────
@admin.register(Company)
class CompanyAdmin(admin.ModelAdmin):
    list_display  = ('symbol', 'name', 'current_price', 'shares', 'company_id')
    search_fields = ('symbol', 'name')
    list_filter   = ()
    ordering      = ('symbol',)


# ─── Order ──────────────────────────────────────────────────────────────────
@admin.register(Order)
class OrderAdmin(admin.ModelAdmin):
    list_display  = ('order_id', 'symbol', 'side', 'order_type', 'status_label',
                     'shares', 'price', 'entry_time')
    list_filter   = ('buy_sell', 'market_limit', 'status', 'symbol')
    search_fields = ('order_id', 'symbol')
    ordering      = ('-entry_time',)

    def side(self, obj):
        return '🟢 BUY' if obj.buy_sell else '🔴 SELL'
    side.short_description = 'Side'

    def order_type(self, obj):
        return 'MARKET' if obj.market_limit else 'LIMIT'
    order_type.short_description = 'Type'

    def status_label(self, obj):
        return '✅ Filled' if obj.status else '⏳ Pending'
    status_label.short_description = 'Status'


# ─── Trade ──────────────────────────────────────────────────────────────────
@admin.register(Trade)
class TradeAdmin(admin.ModelAdmin):
    list_display  = ('trade_id', 'symbol', 'quantity', 'price', 'buy_order_id',
                     'sell_order_id', 'trade_time')
    list_filter   = ('symbol',)
    search_fields = ('trade_id', 'symbol', 'buy_order_id', 'sell_order_id')
    ordering      = ('-trade_time',)
