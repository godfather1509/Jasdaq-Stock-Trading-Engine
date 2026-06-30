"""
Jasdaq Trading Engine — 60-Second Sustained Load Test
======================================================
Usage:  python test_api.py
Requires: pip install aiohttp

Hammers the engine for exactly 60 seconds with concurrent order writes and
read queries, then prints a full latency/throughput breakdown.

Workers
-------
  24 ORDER WORKERS  (3 per trader profile × 8 profiles)
      Each loops: pick random company → place order → repeat until T+60s.
      No sleeps, no phases — maximum sustained write throughput.

  8 READ WORKERS
      Continuously poll GET endpoints: /allCompanies, /market-stats,
      /{id}/metrics, /{id}/stats, /{id}/trades, /{id}/orders.

  1 REPORTER
      Prints a live stats line + ASCII progress bar every 5 seconds.

Semaphore: 50 simultaneous HTTP connections.
"""

import argparse
import asyncio
import random
import statistics
import subprocess
import sys
import time
from dataclasses import dataclass, field
from typing import List

try:
    import aiohttp
except ImportError:
    print("\033[91mMissing dependency. Run:  pip install aiohttp\033[0m")
    sys.exit(1)

_parser = argparse.ArgumentParser(description="Jasdaq 60-second load test")
_parser.add_argument(
    "--host",
    default="localhost:8080",
    metavar="HOST:PORT",
    help="Backend host and port (default: localhost:8080). "
         "Example: --host 192.168.1.42:8080",
)
_parser.add_argument(
    "--db-password",
    default="admin",
    metavar="PASSWORD",
    help="MySQL root password (default: admin)",
)
_args, _ = _parser.parse_known_args()

BASE_URL   = f"http://{_args.host}/api/v1"

# Try the user-supplied password first, then fallback candidates.
def _resolve_db_password(supplied: str) -> str:
    candidates = [supplied] + [p for p in ("admin", "root") if p != supplied]
    for pwd in candidates:
        try:
            res = subprocess.run(
                ["mysql", "-u", "root", f"-p{pwd}", "jasdaqdb",
                 "--skip-column-names", "-e", "SELECT 1;"],
                capture_output=True, text=True,
            )
            if res.returncode == 0:
                return pwd
        except FileNotFoundError:
            break  # mysql not in PATH — no point trying further
    return supplied  # fall back to whatever was supplied

DB_PASSWORD = _resolve_db_password(_args.db_password)
CONCURRENCY        = 50   # max simultaneous HTTP connections
DURATION           = 60   # seconds to hammer the server
WORKERS_PER_TRADER = 3    # concurrent order-placing coroutines per trader
READ_WORKERS       = 8    # concurrent read-only coroutines
WARMUP_SECONDS     = 8    # buys-only phase to seed user share ownership before selling
STATE_REFRESH_S    = 1.0  # how often to refresh per-company circulating shares / depth

# ─── ANSI colours ─────────────────────────────────────────────────────────────
GREEN   = "\033[92m"
RED     = "\033[91m"
YELLOW  = "\033[93m"
CYAN    = "\033[96m"
BLUE    = "\033[94m"
MAGENTA = "\033[95m"
ORANGE  = "\033[38;5;214m"
WHITE   = "\033[97m"
DIM     = "\033[2m"
BOLD    = "\033[1m"
RESET   = "\033[0m"


def inr(v: int) -> str:
    return f"₹{int(v):,}"


# ─── Trader profiles ──────────────────────────────────────────────────────────

@dataclass
class Trader:
    """Simulated market participant with a distinct trading personality."""
    name:        str
    color:       str
    style:       str
    max_qty:     int
    market_bias: float   # probability of MARKET (vs LIMIT) order
    buy_bias:    float   # probability of BUY (vs SELL)
    spread:      float   # fractional offset from market price for limits

    # runtime stats (mutated by order_worker; safe in single-threaded asyncio)
    orders:       int  = 0
    accepted:     int  = 0
    rejected:     int  = 0
    sell_skipped: int  = 0   # sells NOT sent: no circulating shares owned to sell
    buy_skipped:  int  = 0   # market buys NOT sent: no ask-side liquidity on offer
    latencies_ms: list = field(default_factory=list)

    @property
    def tag(self) -> str:
        return f"{self.color}{BOLD}{self.name:<9}{RESET}"


TRADERS: List[Trader] = [
    #           name       color    style          max   mkt%   buy%   spread
    Trader("Alice",  YELLOW,  "Scalper",      3,  0.70, 0.55, 0.025),
    Trader("Bob",    BLUE,    "Whale",        30, 0.85, 0.50, 0.010),
    Trader("Carol",  GREEN,   "Passive",       6,  0.10, 0.68, 0.100),
    Trader("Dave",   RED,     "PanicSeller",  20, 0.92, 0.10, 0.005),
    Trader("Eve",    MAGENTA, "DayTrader",     8,  0.55, 0.52, 0.040),
    Trader("Frank",  CYAN,    "DipBuyer",     12, 0.30, 0.90, 0.120),
    Trader("Grace",  ORANGE,  "Momentum",     15, 0.65, 0.62, 0.030),
    Trader("Heidi",  WHITE,   "Contrarian",    7,  0.40, 0.38, 0.060),
]

# Global read counters (safe to mutate between await points in asyncio)
_reads_ok:  int = 0
_reads_err: int = 0

# Live per-company state, refreshed by state_refresher(). Keyed by companyId:
#   availableShares = shares users currently own (the sellable float in circulation)
#   askDepth        = shares resting on the sell side ("shares available to buy")
#   currentPrice / totalShares = latest price and authorised float
COMPANY_STATE: dict = {}

# Semaphore initialised in main_async()
_sem: asyncio.Semaphore = None  # type: ignore[assignment]


# ─── Helpers ──────────────────────────────────────────────────────────────────

def _limit_price(base: int, buy_sell: bool, spread: float) -> int:
    jitter = spread * random.uniform(0.6, 1.5)
    return max(1, int(base * (1 - jitter if buy_sell else 1 + jitter)))


def _progress_bar(elapsed: float, total: float, width: int = 32) -> str:
    filled = int(width * min(elapsed, total) / total)
    bar = "█" * filled + "░" * (width - filled)
    pct = min(100.0, elapsed / total * 100)
    return f"[{bar}] {pct:4.0f}%"


def _sep(char: str = "─", width: int = 72) -> None:
    print(DIM + char * width + RESET)


def _header(title: str, color: str = CYAN) -> None:
    print()
    print(BOLD + color + "═" * 72 + RESET)
    print(BOLD + color + f"  {title}" + RESET)
    print(BOLD + color + "═" * 72 + RESET)


# ─── Workers ──────────────────────────────────────────────────────────────────

async def a_get(session: aiohttp.ClientSession, path: str) -> dict:
    async with _sem:
        async with session.get(f"{BASE_URL}{path}") as r:
            r.raise_for_status()
            return await r.json()


async def relist_all(session: aiohttp.ClientSession, companies: List[dict]) -> None:
    """Re-seed sell-side liquidity before the run: ask the engine to relist each
    company's unowned shares as a treasury sell wall, so the market starts buyable and
    two-sided. Replaces the old reset_available_shares(), which zeroed ownership without
    restoring a sell order — leaving unbuyable phantom shares."""
    placed_total = 0
    for c in companies:
        try:
            async with _sem:
                async with session.post(f"{BASE_URL}/{c['companyId']}/relist") as r:
                    data = await r.json()
            placed_total += int(data.get("placed", 0) or 0)
        except Exception:
            pass
    word = "company" if len(companies) == 1 else "companies"
    print(DIM + f"  [relist] re-seeded sell walls ({placed_total:,} shares placed "
          f"across {len(companies)} {word})." + RESET)


async def order_worker(
    session:   aiohttp.ClientSession,
    trader:    Trader,
    companies: List[dict],
    start:     float,
    deadline:  float,
) -> None:
    """
    Loop until deadline, placing orders sized to the shares actually in circulation:

      • SELL only what users currently own (availableShares). This mirrors the engine's
        ownership gate, so the order is no longer silently rejected and instead either
        rests as a real ask or crosses the bid book — building a two-sided market.
      • Market BUY only when the ask side actually has shares on offer (askDepth);
        otherwise the engine rejects it for "no liquidity".
      • Warmup (first WARMUP_SECONDS): buys only, biased to MARKET against the IPO wall,
        so user inventory exists before anyone tries to sell.

    Orders we deliberately don't send are counted (sell_skipped / buy_skipped) rather
    than fired blindly and bounced off the engine where the test couldn't see them.
    """
    while time.monotonic() < deadline:
        c     = random.choice(companies)
        cid   = c["companyId"]
        st    = COMPANY_STATE.get(cid, {})
        avail = int(st.get("availableShares", 0))   # user-held shares, sellable
        ask   = int(st.get("askDepth", 0))          # shares on offer = "available to buy"
        base  = max(int(st.get("currentPrice", c.get("currentPrice", 1))), 1)

        if (time.monotonic() - start) < WARMUP_SECONDS:
            buy_sell = True
            use_mkt  = ask > 0          # hit the wall to acquire inventory quickly
        else:
            buy_sell = random.random() < trader.buy_bias
            use_mkt  = random.random() < trader.market_bias

        if buy_sell:
            if use_mkt:
                if ask < 1:
                    trader.buy_skipped += 1
                    continue            # nothing offered — a market buy would be rejected
                qty = min(random.randint(1, trader.max_qty), ask)
                st["askDepth"] = ask - qty   # optimistic; state_refresher resyncs
                price = 0
            else:
                qty   = random.randint(1, trader.max_qty)
                price = _limit_price(base, True, trader.spread)
        else:
            if avail < 1:
                trader.sell_skipped += 1
                continue                # no circulating shares to sell
            qty = min(random.randint(1, trader.max_qty), avail)
            st["availableShares"] = avail - qty   # optimistic reserve; refresher resyncs
            price = 0 if use_mkt else _limit_price(base, False, trader.spread)

        body = {
            "companyId":   cid,
            "symbol":      c["symbol"],
            "buySell":     buy_sell,
            "marketLimit": use_mkt,
            "shares":      qty,
            "price":       price,
        }

        t0 = time.perf_counter()
        try:
            async with _sem:
                async with session.post(f"{BASE_URL}/orders", json=body) as resp:
                    status = resp.status
                    await resp.read()
            elapsed_ms = (time.perf_counter() - t0) * 1000
            trader.orders += 1
            trader.latencies_ms.append(elapsed_ms)
            if status == 200:
                trader.accepted += 1
            else:
                trader.rejected += 1
        except Exception:
            trader.orders   += 1
            trader.rejected += 1


async def read_worker(
    session:   aiohttp.ClientSession,
    companies: List[dict],
    deadline:  float,
) -> None:
    """Continuously poll read endpoints until deadline."""
    global _reads_ok, _reads_err
    global_endpoints = ["/allCompanies", "/market-stats"]
    per_company      = ["metrics", "stats", "trades", "orders"]
    idx = 0
    while time.monotonic() < deadline:
        try:
            if idx % 4 == 0:
                path = random.choice(global_endpoints)
            else:
                c    = random.choice(companies)
                ep   = per_company[idx % len(per_company)]
                path = f"/{c['companyId']}/{ep}"
            idx += 1
            async with _sem:
                async with session.get(f"{BASE_URL}{path}") as r:
                    await r.read()
            _reads_ok += 1
        except Exception:
            _reads_err += 1


async def state_refresher(
    session:   aiohttp.ClientSession,
    companies: List[dict],
    deadline:  float,
) -> None:
    """Keep COMPANY_STATE current so order_worker can size orders to real supply:
    circulating shares from /allCompanies, and book depth from each /metrics."""
    while time.monotonic() < deadline:
        try:
            for c in await a_get(session, "/allCompanies"):
                cid = c.get("companyId")
                if not cid:
                    continue
                st = COMPANY_STATE.setdefault(cid, {})
                st["availableShares"] = int(c.get("availableShares", 0))
                st["currentPrice"]    = int(c.get("currentPrice", 0))
                st["totalShares"]     = int(c.get("totalShares", 0))
        except Exception:
            pass
        for c in companies:
            cid = c["companyId"]
            try:
                m = await a_get(session, f"/{cid}/metrics")
                st = COMPANY_STATE.setdefault(cid, {})
                st["askDepth"] = int(m.get("totalSellShares", 0))
                st["bidDepth"] = int(m.get("totalBuyShares", 0))
            except Exception:
                pass
        await asyncio.sleep(STATE_REFRESH_S)


async def reporter(start: float) -> None:
    """Print a live stats line every 5 seconds, then exit gracefully."""
    tick = 1
    try:
        while True:
            target   = start + tick * 5.0
            sleep_s  = target - time.monotonic()
            if sleep_s > 0:
                await asyncio.sleep(sleep_s)

            elapsed = time.monotonic() - start
            if elapsed > DURATION + 0.5:
                break

            # Aggregate across all traders
            total   = sum(t.orders   for t in TRADERS)
            ok      = sum(t.accepted for t in TRADERS)
            skipped = sum(t.sell_skipped + t.buy_skipped for t in TRADERS)
            lats    = sorted(l for t in TRADERS for l in t.latencies_ms)

            p50  = lats[int(len(lats) * 0.50)]                    if lats else 0.0
            p99  = lats[max(0, int(len(lats) * 0.99) - 1)]        if lats else 0.0
            tput = total / elapsed if elapsed > 0 else 0.0
            hit  = (ok / total * 100) if total else 0.0
            rem  = max(0.0, DURATION - elapsed)

            bar  = _progress_bar(elapsed, DURATION)
            hcol = GREEN if hit >= 60 else YELLOW if hit >= 30 else RED

            print(
                f"  {DIM}{bar}{RESET}"
                f"  {BOLD}{elapsed:4.0f}s{RESET}  {DIM}rem {rem:.0f}s{RESET}"
                f"  orders: {BOLD}{total:,}{RESET}"
                f"  {hcol}hit {hit:.0f}%{RESET}"
                f"  {tput:.0f}/s"
                f"  p50={p50:.0f}ms  p99={p99:.0f}ms"
                f"  reads: {_reads_ok:,}"
                f"  {DIM}skip {skipped:,}{RESET}"
            )

            tick += 1
    except asyncio.CancelledError:
        pass


# ─── Display helpers ──────────────────────────────────────────────────────────

def print_company_table(companies: List[dict]) -> None:
    print(f"\n  {'#':<4} {'SYMBOL':<10} {'NAME':<22} {'PRICE':>10} "
          f"{'TOTAL':>10} {'AVAIL':>10}")
    _sep()
    for i, c in enumerate(companies, 1):
        print(f"  {i:<4} {c['symbol']:<10} {c['name']:<22} "
              f"{inr(c['currentPrice']):>10} {c['totalShares']:>10,} "
              f"{c['availableShares']:>10,}")


def print_company_delta(before: List[dict], after: List[dict]) -> None:
    print(f"\n  {'SYMBOL':<10} {'OPEN':>10} {'CLOSE':>10} {'CHANGE':>20}")
    _sep()
    b_map = {c["companyId"]: c for c in before}
    for c in after:
        b    = b_map.get(c["companyId"], c)
        chg  = c["currentPrice"] - b["currentPrice"]
        pct  = (chg / b["currentPrice"] * 100) if b["currentPrice"] else 0.0
        col  = GREEN if chg >= 0 else RED
        sign = "+" if chg >= 0 else ""
        print(f"  {c['symbol']:<10} "
              f"{inr(b['currentPrice']):>10} "
              f"{col}{inr(c['currentPrice']):>10}{RESET} "
              f"{col}{sign}{inr(chg)} ({sign}{pct:.1f}%){RESET}")


def print_trader_stats() -> None:
    _header("TRADER STATISTICS")
    print(f"\n  {'NAME':<10} {'STYLE':<13} {'ORDERS':>8} {'OK':>7} "
          f"{'ERR':>7} {'HIT%':>7} {'AVG ms':>8} {'P50':>7} {'P99':>8} {'SKIP':>8}")
    _sep()
    for t in TRADERS:
        if not t.orders and not (t.sell_skipped or t.buy_skipped):
            continue
        lats = sorted(t.latencies_ms)
        avg  = statistics.mean(lats)                         if lats else 0.0
        p50  = lats[int(len(lats) * 0.50)]                  if lats else 0.0
        p99  = lats[max(0, int(len(lats) * 0.99) - 1)]      if lats else 0.0
        hit  = (t.accepted / t.orders * 100)                 if t.orders else 0.0
        hcol = GREEN if hit >= 60 else YELLOW if hit >= 30 else RED
        print(f"  {t.color}{BOLD}{t.name:<10}{RESET} {t.style:<13} "
              f"{t.orders:>8,} {GREEN}{t.accepted:>7,}{RESET} {RED}{t.rejected:>7,}{RESET} "
              f"{hcol}{hit:>6.1f}%{RESET} {avg:>7.1f}  {p50:>6.0f}  {p99:>7.0f}"
              f"  {YELLOW}{t.sell_skipped + t.buy_skipped:>7,}{RESET}")

    total   = sum(t.orders   for t in TRADERS)
    ok      = sum(t.accepted for t in TRADERS)
    skipped = sum(t.sell_skipped + t.buy_skipped for t in TRADERS)
    lats  = sorted(l for t in TRADERS for l in t.latencies_ms)
    hit   = (ok / total * 100)                           if total else 0.0
    avg   = statistics.mean(lats)                        if lats  else 0.0
    p50   = lats[int(len(lats) * 0.50)]                 if lats  else 0.0
    p99   = lats[max(0, int(len(lats) * 0.99) - 1)]     if lats  else 0.0
    hcol  = GREEN if hit >= 60 else YELLOW
    _sep()
    print(f"  {BOLD}{'TOTAL':<10}{RESET} {'':13} "
          f"{total:>8,} {GREEN}{ok:>7,}{RESET} {RED}{total-ok:>7,}{RESET} "
          f"{hcol}{hit:>6.1f}%{RESET} {avg:>7.1f}  {p50:>6.0f}  {p99:>7.0f}"
          f"  {YELLOW}{skipped:>7,}{RESET}")


# ─── Reset / Teardown ─────────────────────────────────────────────────────────

def _mysql(*args: str) -> subprocess.CompletedProcess:
    """Run a mysql CLI command; returns a dummy result if mysql is not in PATH."""
    cmd = ["mysql", "-u", "root", f"-p{DB_PASSWORD}", "jasdaqdb"] + list(args)
    try:
        return subprocess.run(cmd, capture_output=True, text=True)
    except FileNotFoundError:
        dummy = subprocess.CompletedProcess(cmd, returncode=1)
        dummy.stdout = ""
        dummy.stderr = "mysql client not found in PATH"
        return dummy


def reset_available_shares(companies: List[dict]) -> None:
    failed = False
    for c in companies:
        res = _mysql("-e", f"UPDATE Companies SET available_shares = 0 "
                          f"WHERE company_id = '{c['companyId']}';")
        if res.returncode != 0 and not failed:
            failed = True
            err = res.stderr.strip().splitlines()[0] if res.stderr.strip() else "unknown error"
            print(YELLOW + f"  [reset] MySQL warning: {err}" + RESET)
    if not failed:
        print(DIM + f"  [reset] available_shares zeroed for {len(companies)} "
              f"compan{'y' if len(companies) == 1 else 'ies'}." + RESET)


def teardown_pending_orders(companies: List[dict]) -> None:
    """
    WHY THIS EXISTS
    ---------------
    The load test creates many resting LIMIT orders that live in two places:

      1. MySQL   — rows in the Orders table with status = 0 (pending)
      2. Memory  — nodes in Spring Boot's in-memory Red-Black Tree LOB

    After the script ends, both layers keep those orders alive.  If a real
    user (or the frontend) places even one new order, it can match against
    hundreds of stale test limits, causing a cascade of trades and price
    updates visible in the UI.

    This function handles layer (1): marks every pending user order as
    cancelled (status = 1) in MySQL so the DB is clean.

    Layer (2) can only be cleared by restarting Spring Boot — the in-memory
    LOB has no REST endpoint to flush it.
    """
    _header("TEARDOWN", YELLOW)

    # Count pending user orders before cleanup
    count_res = _mysql("--skip-column-names", "-e",
                       "SELECT COUNT(*) FROM Orders WHERE status = 0 AND company_order = 0;")
    pending = 0
    if count_res.returncode == 0:
        try:
            pending = int(count_res.stdout.strip())
        except ValueError:
            pass
    elif count_res.returncode != 0:
        err = count_res.stderr.strip().splitlines()[0] if count_res.stderr.strip() else "unknown"
        print(YELLOW + f"\n  [teardown] MySQL warning: {err} — skipping DB cleanup." + RESET)
        return

    # Mark all pending user orders as cancelled for every tested company
    print(f"\n  {DIM}Cancelling {pending:,} pending user order(s) in MySQL ...{RESET}")
    for c in companies:
        _mysql("-e",
               f"UPDATE Orders SET status = 1 "
               f"WHERE company_id = '{c['companyId']}' "
               f"AND status = 0 AND company_order = 0;")

    # Verify
    after_res = _mysql("--skip-column-names", "-e",
                       "SELECT COUNT(*) FROM Orders WHERE status = 0 AND company_order = 0;")
    remaining = "?"
    if after_res.returncode == 0:
        try:
            remaining = after_res.stdout.strip()
        except Exception:
            pass

    print(f"  {GREEN}✓{RESET}  {pending:,} orders cancelled  "
          f"({remaining} still pending in DB)")

    # NOTE: deliberately NOT zeroing available_shares here. Doing so leaves "unowned"
    # shares with no resting sell order to back them — an unbuyable phantom. The next
    # run's relist step re-seeds the sell wall instead, keeping the market consistent.

    # ── Warning about in-memory state ─────────────────────────────────────────
    print(f"""
  {YELLOW}{BOLD}┌──────────────────────────────────────────────────────────────────────┐
  │  ⚠  IN-MEMORY ORDER BOOK STILL HAS TEST ORDERS                      │
  │                                                                        │
  │  Spring Boot holds the order book (Red-Black Tree LOB) in memory.     │
  │  The DB is now clean, but stale limit orders may still be alive in     │
  │  the matching engine until the process restarts.                       │
  │                                                                        │
  │  If the frontend still shows price changes:                            │
  │    → Restart Spring Boot:  cd Jasdaq && ./mvnw spring-boot:run         │
  │    → The LOB re-initialises empty on startup.                          │
  └──────────────────────────────────────────────────────────────────────┘{RESET}
""")


# ─── Main ─────────────────────────────────────────────────────────────────────

async def main_async() -> None:
    global _sem
    _sem = asyncio.Semaphore(CONCURRENCY)

    order_worker_count = len(TRADERS) * WORKERS_PER_TRADER

    _header(f"JASDAQ  —  {DURATION}s SUSTAINED LOAD TEST")
    print(
        f"\n  {DIM}"
        f"{len(TRADERS)} profiles × {WORKERS_PER_TRADER} workers = "
        f"{order_worker_count} order workers  +  "
        f"{READ_WORKERS} read workers  ·  "
        f"{CONCURRENCY} concurrent connections  ·  "
        f"{BASE_URL}  ·  "
        f"db-password: {DB_PASSWORD}"
        f"{RESET}"
    )
    print(
        f"  {DIM}{WARMUP_SECONDS}s buys-only warmup  ·  "
        f"orders sized to circulation (sell ≤ owned, market-buy ≤ ask depth){RESET}"
    )

    connector = aiohttp.TCPConnector(limit=CONCURRENCY + 16)
    timeout   = aiohttp.ClientTimeout(total=30)

    async with aiohttp.ClientSession(connector=connector, timeout=timeout) as session:

        # Discover companies
        print(f"\n{BOLD}  Fetching companies ...{RESET}")
        try:
            companies: List[dict] = await a_get(session, "/allCompanies")
        except Exception as exc:
            print(RED + f"\n  Cannot reach engine: {exc}" + RESET)
            print(DIM + "  Make sure Spring Boot is running on port 8080." + RESET)
            sys.exit(1)

        if not companies:
            print(RED + "  No companies found — create one in Django admin first." + RESET)
            sys.exit(1)

        await relist_all(session, companies)
        print_company_table(companies)
        before = [dict(c) for c in companies]

        # Launch
        _sep("═")
        print(f"\n  {BOLD}{GREEN}▶  Starting {DURATION}s load test ...{RESET}\n")
        # Seed live state so the very first order is sized from real data
        for c in companies:
            COMPANY_STATE[c["companyId"]] = {
                "availableShares": int(c.get("availableShares", 0)),
                "currentPrice":    int(c.get("currentPrice", 0)),
                "totalShares":     int(c.get("totalShares", 0)),
                "askDepth": 0,
                "bidDepth": 0,
            }

        t_start  = time.monotonic()
        deadline = t_start + DURATION

        order_tasks = [
            asyncio.create_task(order_worker(session, trader, companies, t_start, deadline))
            for trader in TRADERS
            for _ in range(WORKERS_PER_TRADER)
        ]
        read_tasks = [
            asyncio.create_task(read_worker(session, companies, deadline))
            for _ in range(READ_WORKERS)
        ]
        refresher_task = asyncio.create_task(state_refresher(session, companies, deadline))
        reporter_task  = asyncio.create_task(reporter(t_start))

        # Wait for all order/read workers (they all exit at deadline)
        await asyncio.gather(*order_tasks, *read_tasks, return_exceptions=True)

        # Cleanly stop background tasks
        for task in (refresher_task, reporter_task):
            task.cancel()
        for task in (refresher_task, reporter_task):
            try:
                await task
            except asyncio.CancelledError:
                pass

        elapsed = time.monotonic() - t_start

        # Final state
        _header("FINAL STATE")
        try:
            after_raw = await asyncio.gather(
                *[a_get(session, f"/{c['companyId']}") for c in companies],
                return_exceptions=True,
            )
            after = [f for f in after_raw if isinstance(f, dict)]
        except Exception:
            after = list(companies)

        print(f"\n{BOLD}  Price Movement:{RESET}")
        print_company_delta(before, after)

        print_trader_stats()

        # Summary
        _header("SUMMARY", GREEN)
        total = sum(t.orders   for t in TRADERS)
        ok    = sum(t.accepted for t in TRADERS)
        sells_skipped = sum(t.sell_skipped for t in TRADERS)
        buys_skipped  = sum(t.buy_skipped  for t in TRADERS)
        lats  = sorted(l for t in TRADERS for l in t.latencies_ms)
        hit   = (ok / total * 100)                           if total else 0.0
        avg   = statistics.mean(lats)                        if lats  else 0.0
        p50   = lats[int(len(lats) * 0.50)]                 if lats  else 0.0
        p95   = lats[max(0, int(len(lats) * 0.95) - 1)]     if lats  else 0.0
        p99   = lats[max(0, int(len(lats) * 0.99) - 1)]     if lats  else 0.0
        tput  = total / elapsed                              if elapsed > 0 else 0.0

        print(f"""
  {BOLD}Duration        :{RESET} {elapsed:.1f} s
  {BOLD}Order workers   :{RESET} {order_worker_count}  ({WORKERS_PER_TRADER} per trader profile)
  {BOLD}Read workers    :{RESET} {READ_WORKERS}
  {BOLD}Total orders    :{RESET} {total:,}
  {BOLD}Accepted        :{RESET} {GREEN}{ok:,}{RESET}
  {BOLD}Rejected/Error  :{RESET} {RED}{total - ok:,}{RESET}
  {BOLD}Sells skipped   :{RESET} {YELLOW}{sells_skipped:,}{RESET}  {DIM}(no owned shares to sell){RESET}
  {BOLD}Buys skipped    :{RESET} {YELLOW}{buys_skipped:,}{RESET}  {DIM}(no ask-side liquidity){RESET}
  {BOLD}Hit rate        :{RESET} {hit:.1f}%
  {BOLD}Throughput      :{RESET} {tput:.1f} orders/s
  {BOLD}Avg latency     :{RESET} {avg:.1f} ms
  {BOLD}P50 latency     :{RESET} {p50:.1f} ms
  {BOLD}P95 latency     :{RESET} {p95:.1f} ms
  {BOLD}P99 latency     :{RESET} {p99:.1f} ms
  {BOLD}Total reads     :{RESET} {_reads_ok:,}  {DIM}(GET requests){RESET}
  {BOLD}Read errors     :{RESET} {_reads_err:,}
""")
        print(BOLD + GREEN + f"  {DURATION}s load test complete.\n" + RESET)

        # Cancel lingering limit orders so the frontend goes quiet
        teardown_pending_orders(companies)


def main() -> None:
    if sys.version_info < (3, 9):
        print(RED + "Python 3.9+ required." + RESET)
        sys.exit(1)
    asyncio.run(main_async())


if __name__ == "__main__":
    main()
