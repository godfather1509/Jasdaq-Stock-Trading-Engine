"""
Jasdaq Trading Engine — API Test Script
Usage: python test_api.py
Requires: pip install requests

NOTE — availableShares behaviour during testing:
  SELL orders placed here use the admin REST endpoint (/api/v1/orders), which
  treats them as company orders and skips the ownership check.  That means the
  matching BUY fills still call creditBuyShares() even though no shares were
  reserved from the pool.  The server caps availableShares at totalShares
  (LEAST clause in CompanyRepository), so it can never go above the company's
  total issue — but the number will drift upward during a test run.
  Reset it in MySQL when needed:
      UPDATE Companies SET available_shares = 0 WHERE symbol = 'XYZ';
"""

import requests
import random
import time
import sys

BASE_URL = "http://localhost:8080/api/v1"

# ─── ANSI colours ─────────────────────────────────────────────────────────────
GREEN   = "\033[92m"
RED     = "\033[91m"
YELLOW  = "\033[93m"
CYAN    = "\033[96m"
BLUE    = "\033[94m"
MAGENTA = "\033[95m"
DIM     = "\033[2m"
BOLD    = "\033[1m"
RESET   = "\033[0m"

def inr(amount):
    return f"₹{int(amount):,}"

def sep(char="─", width=72):
    print(DIM + char * width + RESET)

def header(title):
    print()
    print(BOLD + CYAN + "═" * 72 + RESET)
    print(BOLD + CYAN + f"  {title}" + RESET)
    print(BOLD + CYAN + "═" * 72 + RESET)

# ─── API helpers ──────────────────────────────────────────────────────────────

def get(path):
    r = requests.get(f"{BASE_URL}{path}", timeout=10)
    r.raise_for_status()
    return r.json()

def post(path, body):
    r = requests.post(f"{BASE_URL}{path}", json=body, timeout=10)
    return r.status_code, r.json()

# ─── Display helpers ──────────────────────────────────────────────────────────

def print_company_table(companies):
    print(f"\n  {'#':<4} {'SYMBOL':<10} {'NAME':<22} {'PRICE':>10} "
          f"{'TOTAL':>10} {'AVAILABLE':>10} {'MARKET CAP':>16}")
    sep()
    for i, c in enumerate(companies, 1):
        mc = c["currentPrice"] * c["totalShares"]
        print(f"  {i:<4} {c['symbol']:<10} {c['name']:<22} "
              f"{inr(c['currentPrice']):>10} "
              f"{c['totalShares']:>10,} "
              f"{c['availableShares']:>10,} "
              f"{inr(mc):>16}")


def print_company_detail(c, metrics=None):
    mc = c["currentPrice"] * c["totalShares"]
    print(f"\n  {'Company':<18}: {BOLD}{c['name']}{RESET} ({CYAN}{c['symbol']}{RESET})")
    print(f"  {'Company ID':<18}: {DIM}{c['companyId']}{RESET}")
    print(f"  {'Share Price':<18}: {BOLD}{GREEN}{inr(c['currentPrice'])}{RESET}")
    print(f"  {'Initial Price':<18}: {inr(c['initialPrice'])}")
    print(f"  {'Market Cap':<18}: {BOLD}{inr(mc)}{RESET}")
    print(f"  {'Total Shares':<18}: {c['totalShares']:,}")
    print(f"  {'Available Shares':<18}: {c['availableShares']:,}")
    if metrics:
        print(f"  {'Buy in book':<18}: {metrics.get('totalBuyShares', 0):,} shares")
        print(f"  {'Sell in book':<18}: {metrics.get('totalSellShares', 0):,} shares")


def print_orders(orders, limit=25):
    if not orders:
        print(DIM + "  No orders found.\n" + RESET)
        return

    print(f"\n  {'ORDER ID':<38} {'TYPE':<8} {'SIDE':<6} {'QTY':>9} "
          f"{'PRICE':>10} {'STATUS'}")
    sep()
    for o in orders[:limit]:
        side      = "BUY"    if o.get("buySell")      else "SELL"
        otype     = "MARKET" if o.get("marketLimit")   else "LIMIT"
        side_col  = GREEN    if o.get("buySell")       else RED

        init  = o.get("initialShares", 0)
        rem   = o.get("shares", 0)
        filled = init - rem if init > 0 else rem
        qty   = f"{filled}/{init}" if init > 0 else str(rem)

        status = o.get("fillStatus", "PENDING")
        if   status == "FILLED":           s_col = GREEN
        elif status == "PARTIALLY_FILLED": s_col = BLUE
        elif status == "CANCELLED":        s_col = RED
        else:                              s_col = YELLOW

        oid = o.get("orderId", "")
        # show last 36 chars of order id
        oid_display = ("…" + oid[-36:]) if len(oid) > 37 else oid

        print(f"  {DIM}{oid_display:<38}{RESET} {otype:<8} "
              f"{side_col}{side:<6}{RESET} {qty:>9} "
              f"{inr(o.get('price', 0)):>10}  "
              f"{s_col}{status}{RESET}")
    if len(orders) > limit:
        print(f"  {DIM}... and {len(orders) - limit} more{RESET}")
    print()


# ─── Order placement ──────────────────────────────────────────────────────────

def place(company_id, symbol, buy_sell, market_limit, shares, price, label=""):
    side  = "BUY"    if buy_sell      else "SELL"
    otype = "MARKET" if market_limit  else "LIMIT"
    p_str = "MARKET PRICE" if market_limit else inr(price)
    col   = GREEN if buy_sell else RED

    tag = f"  [{label}] " if label else "  "
    print(f"{tag}{col}{BOLD}{side} {otype}{RESET} — {shares} shares @ {p_str} ... ", end="", flush=True)

    body = {
        "companyId":   company_id,
        "symbol":      symbol,
        "buySell":     buy_sell,
        "marketLimit": market_limit,
        "shares":      shares,
        "price":       price,
    }
    try:
        status, resp = post("/orders", body)
        if status == 200:
            print(GREEN + "✓ accepted" + RESET)
        else:
            print(RED + f"✗ HTTP {status}: {resp}" + RESET)
    except Exception as e:
        print(RED + f"✗ {e}" + RESET)

    time.sleep(0.25)


# ─── Main ─────────────────────────────────────────────────────────────────────

def main():
    header("JASDAQ TRADING ENGINE — API TEST")

    # ── 1. All companies ──────────────────────────────────────────────────────
    print(f"\n{BOLD}[1] Fetching companies from {BASE_URL}/allCompanies ...{RESET}")
    try:
        companies = get("/allCompanies")
    except Exception as e:
        print(RED + f"\n  Cannot reach engine: {e}" + RESET)
        print(DIM + "  Make sure Spring Boot is running on port 8080.\n" + RESET)
        sys.exit(1)

    if not companies:
        print(RED + "\n  No companies found. Create one via Django admin first." + RESET)
        sys.exit(1)

    print_company_table(companies)
    print(f"\n  {len(companies)} company/companies found.")

    # ── 2. Pick a random company ──────────────────────────────────────────────
    company = random.choice(companies)
    cid    = company["companyId"]
    symbol = company["symbol"]

    print(f"\n{BOLD}[2] Randomly selected: {CYAN}{symbol}{RESET} — {company['name']}{RESET}")

    try:
        metrics = get(f"/{cid}/metrics")
    except Exception:
        metrics = {}

    print_company_detail(company, metrics)

    # ── 3. Place orders in multiple rounds ────────────────────────────────────
    header("ORDER PLACEMENT — 3 ROUNDS")

    p = max(company["currentPrice"], 10)   # base price, minimum ₹10

    # ── Round 1: Build the book with limit orders ────────────────────────────
    print(f"\n{BOLD}{YELLOW}Round 1 — Limit orders: build supply and demand{RESET}")
    sep()

    # SELL LIMITs at escalating prices (supply side)
    for mult, qty in [(0.90, 3), (1.00, 2), (1.10, 4), (1.20, 2), (1.30, 3)]:
        place(cid, symbol, False, False, qty, int(p * mult), label="SELL LIMIT")

    # BUY LIMITs below market (demand side, won't match immediately)
    for mult, qty in [(0.85, 3), (0.75, 4), (0.65, 2)]:
        place(cid, symbol, True, False, qty, int(p * mult), label="BUY LIMIT")

    print(f"\n  {DIM}Waiting 2s for engine ...{RESET}")
    time.sleep(2)

    # ── Round 2: Market orders sweep ─────────────────────────────────────────
    print(f"\n{BOLD}{YELLOW}Round 2 — Market orders sweep the book{RESET}")
    sep()

    # BUY MARKET: sweeps cheapest SELL LIMITs
    for qty in [3, 2, 4]:
        place(cid, symbol, True, True, qty, 0, label="BUY MARKET")

    # SELL MARKET: sweeps highest BUY LIMITs
    for qty in [2, 3]:
        place(cid, symbol, False, True, qty, 0, label="SELL MARKET")

    print(f"\n  {DIM}Waiting 2s for engine ...{RESET}")
    time.sleep(2)

    # ── Round 3: Price competition at updated market price ────────────────────
    print(f"\n{BOLD}{YELLOW}Round 3 — Price competition at updated market price{RESET}")
    sep()

    try:
        refreshed = get(f"/{cid}")
        p2 = max(refreshed["currentPrice"], 10)
    except Exception:
        p2 = p

    combos = [
        # (buy_sell, market_limit, shares, price_mult)
        (True,  False, 3, 1.05),   # BUY LIMIT above market → immediate match vs resting SELL
        (False, False, 3, 1.05),   # SELL LIMIT at same price → matches the BUY above
        (True,  False, 4, 0.95),   # BUY LIMIT just below market → rests
        (False, False, 2, 0.85),   # SELL LIMIT well below → immediate match vs resting BUY
        (True,  True,  3, 0),      # BUY MARKET
        (False, False, 2, 1.20),   # SELL LIMIT above market → rests
        (True,  False, 5, 0.70),   # BUY LIMIT well below → rests
        (False, True,  2, 0),      # SELL MARKET
    ]

    for buy_sell, market_limit, qty, mult in combos:
        price = int(p2 * mult) if not market_limit else 0
        place(cid, symbol, buy_sell, market_limit, qty, price)

    print(f"\n  {DIM}Waiting 2s for engine ...{RESET}")
    time.sleep(2)

    # ── 4. Final snapshot ─────────────────────────────────────────────────────
    header("FINAL STATE")

    try:
        final   = get(f"/{cid}")
        metrics = get(f"/{cid}/metrics")
        print_company_detail(final, metrics)
    except Exception as e:
        print(RED + f"  Could not fetch final state: {e}" + RESET)

    # ── 5. Order history ──────────────────────────────────────────────────────
    print(f"\n{BOLD}Order History (latest 25):{RESET}")
    try:
        orders = get(f"/{cid}/orders")
        print_orders(orders, limit=25)
    except Exception as e:
        print(RED + f"  Could not fetch orders: {e}" + RESET)

    print(BOLD + GREEN + "  Test complete.\n" + RESET)


if __name__ == "__main__":
    main()
