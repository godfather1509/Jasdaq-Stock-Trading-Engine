import uuid
import requests
from django import forms
from django.db import transaction
from django.contrib import admin, messages
from django.forms import BaseInlineFormSet
from django.urls import reverse
from django.utils.html import format_html
from .models import Company, Order, Trade


SPRING_BOOT_BASE = "http://127.0.0.1:8080/api/v1"


# ─── Company Creation Form ────────────────────────────────────────────────────

class CompanyCreationForm(forms.ModelForm):
    """Form shown when creating a new company in the admin panel."""

    class Meta:
        model = Company
        fields = ('symbol', 'name', 'total_shares', 'initial_price')
        labels = {
            'initial_price': 'Total Company Valuation',
        }
        help_texts = {
            'symbol': 'Ticker symbol (e.g. AAPL). Must be unique.',
            'total_shares': 'Total number of shares to issue at IPO.',
            'initial_price': (
                'Total company value at IPO (e.g. enter 1500000 for a ₹15,00,000 valuation). '
                'Per-share IPO price is calculated automatically as Valuation ÷ Total Shares.'
            ),
        }

    def clean_symbol(self):
        return self.cleaned_data['symbol'].upper().strip()

    def clean_total_shares(self):
        val = self.cleaned_data['total_shares']
        if val <= 0:
            raise forms.ValidationError("Total shares must be greater than zero.")
        return val

    def clean_initial_price(self):
        val = self.cleaned_data['initial_price']
        if val <= 0:
            raise forms.ValidationError("Total company valuation must be greater than zero.")
        return val


# ─── Order Creation Form ──────────────────────────────────────────────────────

class OrderCreationForm(forms.ModelForm):
    """Form shown when creating a new order from the admin panel."""
    
    BUY_SELL_CHOICES = [(True, 'BUY'), (False, 'SELL')]
    MARKET_LIMIT_CHOICES = [(True, 'MARKET'), (False, 'LIMIT')]
    
    buy_sell = forms.ChoiceField(choices=BUY_SELL_CHOICES, label="Side")
    market_limit = forms.ChoiceField(choices=MARKET_LIMIT_CHOICES, label="Order Type")
    
    class Meta:
        model = Order
        fields = ('company', 'buy_sell', 'market_limit', 'shares', 'price')
        help_texts = {
            'company': 'Select the company to trade.',
            'shares': 'Number of shares.',
            'price': 'Price in smallest currency unit (ignored for MARKET orders).',
        }

    def clean_buy_sell(self):
        val = self.cleaned_data['buy_sell']
        return val == 'True' or val is True

    def clean_market_limit(self):
        val = self.cleaned_data['market_limit']
        return val == 'True' or val is True
      
    def clean(self):
        cleaned_data = super().clean()
        market_limit = cleaned_data.get('market_limit')
        price = cleaned_data.get('price')
        
        if market_limit is False and (price is None or price <= 0):
            self.add_error('price', "LIMIT orders must have a price > 0.")
        if market_limit is True and price is None:
            cleaned_data['price'] = 0
            
        return cleaned_data


# ─── Inlines ────────────────────────────────────────────────────────────────

class OrderBySymbolFormSet(BaseInlineFormSet):
    """Filter orders by symbol instead of the company FK.

    The company FK is often NULL (orders placed before the FK was wired up),
    so the default FK-based filter returns 0 rows. Overriding get_queryset()
    directly is version-safe and avoids _queryset cache manipulation.
    """
    def get_queryset(self):
        if self.instance and self.instance.pk:
            return Order.objects.filter(
                symbol=self.instance.symbol
            ).order_by('-entry_time')
        return super().get_queryset()


class TradeBySymbolFormSet(BaseInlineFormSet):
    def get_queryset(self):
        if self.instance and self.instance.pk:
            return Trade.objects.filter(
                symbol=self.instance.symbol
            ).order_by('-trade_time')
        return super().get_queryset()


class OrderInline(admin.TabularInline):
    model = Order
    formset = OrderBySymbolFormSet
    fields = ('source_label', 'side_label', 'type_label', 'status_label', 'shares', 'initial_shares', 'price', 'entry_time', 'edit_link')
    readonly_fields = fields
    can_delete = False
    extra = 0
    show_change_link = True  # gives tr.has_original class so CSS can hide the header row

    class Media:
        css = {'all': ('admin/css/order_inline.css',)}

    def source_label(self, obj):
        val = obj.company_order
        is_company = (val == b'\x01' or val is True or val == 1)
        return '🏢 System' if is_company else '👤 User'
    source_label.short_description = 'Source'

    def side_label(self, obj):
        val = obj.buy_sell
        is_true = (val == b'\x01' or val is True or val == 1)
        return 'BUY' if is_true else 'SELL'
    side_label.short_description = 'Side'

    def type_label(self, obj):
        val = obj.market_limit
        is_true = (val == b'\x01' or val is True or val == 1)
        return 'MARKET' if is_true else 'LIMIT'
    type_label.short_description = 'Type'

    def status_label(self, obj):
        val = obj.status
        is_true = (val == b'\x01' or val is True or val == 1)
        return 'Filled' if is_true else 'Pending'
    status_label.short_description = 'Status'

    def edit_link(self, obj):
        if not obj.pk:
            return ''
        url = reverse('admin:trading_order_change', args=[obj.pk])
        return format_html('<a href="{}" class="inline-changelink" title="View/Edit">✎</a>', url)
    edit_link.short_description = ''


class TradeInline(admin.TabularInline):
    model = Trade
    formset = TradeBySymbolFormSet
    readonly_fields = ('trade_id', 'symbol', 'quantity', 'price', 'buy_order_id', 'sell_order_id', 'trade_time')
    can_delete = False
    extra = 0
    show_change_link = True


# ─── Company ────────────────────────────────────────────────────────────────

@admin.register(Company)
class CompanyAdmin(admin.ModelAdmin):
    list_display  = ('symbol', 'name', 'initial_price', 'current_price', 'all_time_high', 'total_shares', 'available_shares', 'market_cap', 'company_id')
    search_fields = ('symbol', 'name')
    list_filter   = ()
    ordering      = ('symbol',)
    inlines       = [OrderInline, TradeInline]

    def market_cap(self, obj):
        valuation = obj.current_price * obj.total_shares
        return f"₹{valuation:,}"
    market_cap.short_description = "Market Cap (current price × shares)"

    def get_readonly_fields(self, request, obj=None):
        if obj is None:
            return ()
        # company_id, current_price, available_shares, all_time_high are managed by the engine
        return ('company_id', 'current_price', 'available_shares', 'all_time_high')

    def get_form(self, request, obj=None, **kwargs):
        if obj is None:
            kwargs['form'] = CompanyCreationForm
        return super().get_form(request, obj, **kwargs)

    def get_fields(self, request, obj=None):
        if obj is None:
            return ('symbol', 'name', 'total_shares', 'initial_price')
        return ('company_id', 'symbol', 'name', 'initial_price', 'current_price',
                'all_time_high', 'total_shares', 'available_shares')

    def save_model(self, request, obj, form, change):
        """
        On first save (change=False):
        1. Auto-generate company_id.
        2. Set available_shares = total_shares, current_price = initial_price.
        3. Save to MySQL (so Spring Boot can see it on next restart).
        4. POST to Spring Boot to register the TradeEngine immediately (no restart needed).
        """
        if not change:
            # Generate a unique company ID (same format as Spring Boot IdGenerator)
            symbol = obj.symbol.upper()
            obj.company_id = f"{symbol}-{uuid.uuid4().hex[:8].upper()}"

            # Convert total company valuation → per-share IPO price
            obj.initial_price = obj.initial_price // obj.total_shares
            obj.current_price = obj.initial_price

            # Users own 0 shares at IPO — increments as BUY orders are filled
            obj.available_shares = 0
            # All-time high starts at the listing (per-share) price
            obj.all_time_high = obj.initial_price

        # Save to the shared MySQL database
        super().save_model(request, obj, form, change)

        if not change:
            # Notify Spring Boot to register TradeEngine immediately after commit releases DB lock.
            payload = {
                "companyId":       obj.company_id,
                "symbol":          obj.symbol,
                "name":            obj.name,
                "totalShares":     obj.total_shares,
                "availableShares": 0,
                "initialPrice":    obj.initial_price,   # already per-share at this point
                "currentPrice":    obj.current_price,
            }

            def notify_java():
                try:
                    requests.post(
                        f"{SPRING_BOOT_BASE}/companies",
                        json=payload,
                        timeout=5,
                    )
                except Exception as e:
                    # Logging exception silently as the user has already received the response.
                    print("Failed to notify engine:", e)

            transaction.on_commit(notify_java)
            messages.success(
                request,
                f"✅ Company '{obj.symbol}' registered. "
                f"Total shares: {obj.total_shares:,} | IPO price per share: {obj.initial_price:,}"
            )
        else:
            messages.success(request, f"✅ Company '{obj.symbol}' updated successfully.")
            if 'symbol' in form.changed_data:
                messages.warning(
                    request,
                    "⚠️ Symbol was changed. Existing open orders still reference the old symbol — "
                    "restart the trading engine to reload the order book for this company."
                )

    def delete_model(self, request, obj):
        """Cascade-delete all orders and trades by symbol, then the company."""
        symbol = obj.symbol
        deleted_orders, _ = Order.objects.filter(symbol=symbol).delete()
        deleted_trades, _ = Trade.objects.filter(symbol=symbol).delete()
        super().delete_model(request, obj)
        messages.warning(
            request,
            f"🗑️ '{symbol}' deleted along with {deleted_orders} order(s) and {deleted_trades} trade(s). "
            f"Restart the trading engine to remove the in-memory order book."
        )

    def delete_queryset(self, request, queryset):
        """Bulk delete — cascade orders and trades for every selected company."""
        for company in queryset:
            Order.objects.filter(symbol=company.symbol).delete()
            Trade.objects.filter(symbol=company.symbol).delete()
        super().delete_queryset(request, queryset)

    def has_delete_permission(self, request, obj=None):
        return True


# ─── Order ──────────────────────────────────────────────────────────────────

@admin.register(Order)
class OrderAdmin(admin.ModelAdmin):
    list_display  = ('order_id', 'symbol', 'source_label', 'side', 'order_type',
                     'status_label', 'shares', 'initial_shares', 'price', 'entry_time')
    list_filter   = ('symbol', 'company_order')
    search_fields = ('order_id', 'symbol')
    ordering      = ('-entry_time',)

    def source_label(self, obj):
        val = obj.company_order
        is_company = (val == b'\x01' or val is True or val == 1)
        return '🏢 System' if is_company else '👤 User'
    source_label.short_description = 'Source'

    def side(self, obj):
        val = obj.buy_sell
        is_true = (val == b'\x01' or val is True or val == 1)
        return 'BUY' if is_true else 'SELL'
    side.short_description = 'Side'

    def order_type(self, obj):
        val = obj.market_limit
        is_true = (val == b'\x01' or val is True or val == 1)
        return 'MARKET' if is_true else 'LIMIT'
    order_type.short_description = 'Type'

    def status_label(self, obj):
        val = obj.status
        is_true = (val == b'\x01' or val is True or val == 1)
        return 'Filled' if is_true else 'Pending'
    status_label.short_description = 'Status'

    def get_form(self, request, obj=None, **kwargs):
        if obj is None:
            kwargs['form'] = OrderCreationForm
        return super().get_form(request, obj, **kwargs)

    def get_fields(self, request, obj=None):
        if obj is None:
            return ('company', 'buy_sell', 'market_limit', 'shares', 'price')
        # Use custom display methods for boolean columns — raw BIT(1) bytes from MySQL
        # crash Django's _boolean_icon() if passed directly as readonly fields.
        return ('order_id', 'symbol', 'company', 'source_label', 'side', 'order_type',
                'status_label', 'shares', 'price', 'entry_time', 'event_time', 'final_price')

    def get_readonly_fields(self, request, obj=None):
        if obj is None:
            return ()
        return self.get_fields(request, obj)

    def has_delete_permission(self, request, obj=None):
        return False

    def save_model(self, request, obj, form, change):
        if not change:
            payload = {
                "companyId": obj.company.company_id,
                "symbol": obj.company.symbol,
                "buySell": obj.buy_sell,
                "marketLimit": obj.market_limit,
                "shares": obj.shares,
                "price": obj.price or 0
            }
            try:
                resp = requests.post(f"{SPRING_BOOT_BASE}/orders", json=payload, timeout=5)
                if resp.status_code == 200:
                    messages.success(request, f"✅ Order submitted to the Java Trading Engine. Refresh to see status.")
                else:
                    messages.error(request, f"❌ Failed to place order. Engine HTTP {resp.status_code}: {resp.text}")
            except Exception as e:
                messages.error(request, f"❌ Cannot connect to Trading Engine: {e}")
                
            # Assign a dummy PK just so Django Admin logging doesn't crash
            obj.order_id = "SENT-" + str(uuid.uuid4())[:8]
            
            # DO NOT call super().save_model() because Spring Boot owns the actual record persistence
            return
            
        super().save_model(request, obj, form, change)


# ─── Trade ──────────────────────────────────────────────────────────────────

@admin.register(Trade)
class TradeAdmin(admin.ModelAdmin):
    list_display  = ('trade_id', 'symbol', 'quantity', 'price', 'buy_order_id',
                     'sell_order_id', 'trade_time')
    list_filter   = ('symbol',)
    search_fields = ('trade_id', 'symbol', 'buy_order_id', 'sell_order_id')
    ordering      = ('-trade_time',)

    def has_delete_permission(self, request, obj=None):
        return False
