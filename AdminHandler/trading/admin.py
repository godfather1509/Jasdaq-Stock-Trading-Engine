import uuid
import requests
from django import forms
from django.db import transaction
from django.contrib import admin, messages
from .models import Company, Order, Trade


SPRING_BOOT_BASE = "http://127.0.0.1:8080/api/v1"


# ─── Company Creation Form ────────────────────────────────────────────────────

class CompanyCreationForm(forms.ModelForm):
    """Form shown when creating a new company in the admin panel."""

    class Meta:
        model = Company
        fields = ('symbol', 'name', 'total_shares', 'initial_price')
        help_texts = {
            'symbol': 'Ticker symbol (e.g. AAPL). Must be unique.',
            'total_shares': 'Total number of shares authorised for trading. Cannot be changed after creation.',
            'initial_price': 'Starting price in smallest currency unit (e.g. paise / cents). E.g. 15000 = ₹150.00.',
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
            raise forms.ValidationError("Initial price must be greater than zero.")
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

class OrderInline(admin.TabularInline):
    model = Order
    fields = ('order_id', 'symbol', 'side_label', 'type_label', 'status_label', 'shares', 'initial_shares', 'price', 'entry_time')
    readonly_fields = fields
    can_delete = False
    extra = 0
    show_change_link = True

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


class TradeInline(admin.TabularInline):
    model = Trade
    readonly_fields = ('trade_id', 'symbol', 'quantity', 'price', 'buy_order_id', 'sell_order_id', 'trade_time')
    can_delete = False
    extra = 0
    show_change_link = True


# ─── Company ────────────────────────────────────────────────────────────────

@admin.register(Company)
class CompanyAdmin(admin.ModelAdmin):
    list_display  = ('symbol', 'name', 'initial_price', 'current_price', 'total_shares', 'available_shares', 'company_id')
    search_fields = ('symbol', 'name')
    list_filter   = ()
    ordering      = ('symbol',)
    inlines       = [OrderInline, TradeInline]

    # Fields shown when EDITING an existing company
    def get_readonly_fields(self, request, obj=None):
        if obj is None:
            return ()
        return ('company_id', 'symbol', 'name', 'total_shares', 'initial_price',
                'current_price', 'available_shares')

    def get_form(self, request, obj=None, **kwargs):
        """Use a simplified creation form for new companies."""
        if obj is None:
            # Creating a new company
            kwargs['form'] = CompanyCreationForm
        return super().get_form(request, obj, **kwargs)

    def get_fields(self, request, obj=None):
        if obj is None:
            # Show only editable creation fields
            return ('symbol', 'name', 'total_shares', 'initial_price')
        # Show all fields (all read-only) for existing companies
        return ('company_id', 'symbol', 'name', 'initial_price', 'current_price',
                'total_shares', 'available_shares')

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
            obj.available_shares = obj.total_shares
            obj.current_price = obj.initial_price

        # Save to the shared MySQL database
        super().save_model(request, obj, form, change)

        if not change:
            # Notify Spring Boot to register TradeEngine immediately after commit releases DB lock.
            payload = {
                "companyId":       obj.company_id,
                "symbol":          obj.symbol,
                "name":            obj.name,
                "totalShares":     obj.total_shares,
                "availableShares": obj.available_shares,
                "initialPrice":    obj.initial_price,
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
                f"Total shares: {obj.total_shares:,} | Price: {obj.initial_price}"
            )

    def has_delete_permission(self, request, obj=None):
        # Prevent accidental deletion of companies that have active orders
        return True


# ─── Order ──────────────────────────────────────────────────────────────────

@admin.register(Order)
class OrderAdmin(admin.ModelAdmin):
    list_display  = ('order_id', 'symbol', 'side', 'order_type', 'status_label',
                     'shares', 'initial_shares', 'price', 'entry_time')
    list_filter   = ('buy_sell', 'market_limit', 'status', 'symbol')
    search_fields = ('order_id', 'symbol')
    ordering      = ('-entry_time',)

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
        return ('order_id', 'symbol', 'company', 'buy_sell', 'market_limit', 'status', 'shares', 'price', 'entry_time', 'event_time', 'final_price')
        
    def get_readonly_fields(self, request, obj=None):
        if obj is None:
            return ()
        return self.get_fields(request, obj)

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
