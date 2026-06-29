"""
Jasdaq UDP Multicast Listener
==============================
Subscribes to the engine's multicast feed and prints every market-update
packet as it arrives.  No external dependencies — standard library only.

The Spring Boot engine broadcasts a JSON packet on every order placement:
  {"Company id": "<id>", "current price": <int>, "timestamp": <epoch_ms>}

  Group : 230.0.0.0
  Port  : 5000  (UDP)

Usage
-----
  python multicast_listener.py            # listen indefinitely, Ctrl+C to stop
  python multicast_listener.py --verify   # fire one REST order, wait for echo

Firewall note (Windows)
-----------------------
If --verify receives nothing, allow inbound UDP on port 5000:
  netsh advfirewall firewall add rule name="Jasdaq Multicast" ^
        protocol=UDP dir=in localport=5000 action=allow
"""

import json
import socket
import struct
import sys
import time
import urllib.error
import urllib.request
from collections import defaultdict
from datetime import datetime, timezone
from typing import Optional

MCAST_GROUP = "230.0.0.0"
MCAST_PORT  = 5000
BUFFER_SIZE = 65535
REST_BASE   = "http://localhost:8080/api/v1"
VERIFY_TIMEOUT = 5.0   # seconds to wait for echo in --verify mode
SUMMARY_INTERVAL = 10  # seconds between live summary prints

# ─── ANSI ─────────────────────────────────────────────────────────────────────
GREEN  = "\033[92m"
RED    = "\033[91m"
YELLOW = "\033[93m"
CYAN   = "\033[96m"
DIM    = "\033[2m"
BOLD   = "\033[1m"
RESET  = "\033[0m"


# ─── Helpers ──────────────────────────────────────────────────────────────────

def _ts(epoch_ms: int) -> str:
    """Format epoch-ms as local HH:MM:SS.mmm."""
    dt = datetime.fromtimestamp(epoch_ms / 1000, tz=timezone.utc).astimezone()
    return dt.strftime("%H:%M:%S.") + f"{epoch_ms % 1000:03d}"


def _inr(v: int) -> str:
    return f"₹{v:,}"


def _sep(width: int = 64) -> None:
    print(DIM + "─" * width + RESET)


def _make_socket() -> socket.socket:
    """Create a UDP socket joined to the Jasdaq multicast group."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    # Bind to all interfaces on the multicast port
    sock.bind(("", MCAST_PORT))
    # Join the multicast group on the default interface
    mreq = struct.pack(
        "4s4s",
        socket.inet_aton(MCAST_GROUP),
        socket.inet_aton("0.0.0.0"),
    )
    sock.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)
    return sock


def _parse(raw: bytes) -> Optional[dict]:
    try:
        return json.loads(raw.decode())
    except Exception:
        return None


# ─── Summary table ────────────────────────────────────────────────────────────

def _print_summary(stats: dict, total: int, errors: int, elapsed: float) -> None:
    if not stats:
        print(f"  {DIM}(no packets yet){RESET}\n")
        return
    rate = total / elapsed if elapsed > 0 else 0.0
    print(f"\n  {DIM}── Summary ({elapsed:.0f}s elapsed, {rate:.1f} pkt/s) "
          + "─" * 30 + RESET)
    print(f"  {'COMPANY ID':<28} {'PKTS':>6} {'LAST PRICE':>13} "
          f"{'MIN':>13} {'MAX':>13}")
    _sep(78)
    for cid, s in sorted(stats.items()):
        lst  = _inr(s["last"]) if s["last"]  is not None else "—"
        mn   = _inr(s["min"])  if s["min"]   is not None else "—"
        mx   = _inr(s["max"])  if s["max"]   is not None else "—"
        col  = GREEN if (s.get("trend", 0) >= 0) else RED
        print(f"  {cid[:28]:<28} {s['count']:>6,} "
              f"{col}{lst:>13}{RESET} {mn:>13} {mx:>13}")
    _sep(78)
    print(f"  {DIM}Total: {total:,} packets   Errors: {errors}   "
          f"Rate: {rate:.1f} pkt/s{RESET}\n")


# ─── Listen mode ──────────────────────────────────────────────────────────────

def listen_forever() -> None:
    print(f"\n  {BOLD}{CYAN}Jasdaq UDP Multicast Listener{RESET}")
    print(f"  {DIM}Group {MCAST_GROUP}  ·  Port {MCAST_PORT}  ·  "
          f"Ctrl+C to stop{RESET}\n")
    _sep()

    try:
        sock = _make_socket()
    except OSError as exc:
        print(f"\n  {RED}Cannot open socket: {exc}{RESET}")
        print(f"  {DIM}Try running as administrator or add a firewall exception "
              f"for UDP port {MCAST_PORT}.{RESET}\n")
        sys.exit(1)

    # Per-company rolling stats
    stats: dict = defaultdict(lambda: {
        "count": 0, "last": None, "min": None, "max": None,
        "prev": None, "trend": 0,
    })

    total   = 0
    errors  = 0
    t_start = time.monotonic()
    next_summary = t_start + SUMMARY_INTERVAL

    print(f"  {DIM}Waiting for packets ...{RESET}\n")
    print(f"  {'#':>6}  {'TIME':<15}  {'COMPANY ID':<28}  {'PRICE':>13}")
    _sep(72)

    try:
        while True:
            sock.settimeout(1.0)
            try:
                raw, addr = sock.recvfrom(BUFFER_SIZE)
            except socket.timeout:
                now = time.monotonic()
                if now >= next_summary:
                    _print_summary(stats, total, errors, now - t_start)
                    next_summary = now + SUMMARY_INTERVAL
                continue

            total += 1
            msg = _parse(raw)

            if msg is None:
                errors += 1
                print(f"  {RED}[!]{RESET} {DIM}Unparseable packet "
                      f"({len(raw)} B) from {addr[0]}{RESET}")
                continue

            cid   = msg.get("Company id", "?")
            price = int(msg.get("current price", 0))
            ts    = int(msg.get("timestamp", 0))

            s = stats[cid]
            prev = s["prev"]
            s["count"] += 1
            s["last"]   = price
            s["prev"]   = price
            s["trend"]  = (price - prev) if prev is not None else 0
            if s["min"] is None or price < s["min"]:
                s["min"] = price
            if s["max"] is None or price > s["max"]:
                s["max"] = price

            col  = GREEN if s["trend"] >= 0 else RED
            sign = "▲" if s["trend"] > 0 else ("▼" if s["trend"] < 0 else " ")

            print(f"  {DIM}{total:>6}{RESET}  {DIM}{_ts(ts):<15}{RESET}  "
                  f"{BOLD}{cid[:28]:<28}{RESET}  "
                  f"{col}{sign} {_inr(price):>12}{RESET}")

            now = time.monotonic()
            if now >= next_summary:
                _print_summary(stats, total, errors, now - t_start)
                next_summary = now + SUMMARY_INTERVAL

    except KeyboardInterrupt:
        elapsed = time.monotonic() - t_start
        print()
        _sep()
        _print_summary(stats, total, errors, elapsed)
        print(f"  {BOLD}Stopped.{RESET}  "
              f"{total:,} packets received in {elapsed:.1f}s "
              f"({total / max(elapsed, 0.001):.1f} pkt/s)\n")
    finally:
        sock.close()


# ─── Verify mode ──────────────────────────────────────────────────────────────

def verify_mode() -> None:
    """
    End-to-end pipeline check:
      REST POST /orders  →  matching engine  →  MulticastBroadcaster  →  Python socket
    """
    print(f"\n  {BOLD}{CYAN}Jasdaq Multicast — End-to-End Verify{RESET}")
    print(f"  {DIM}Places one REST order, then listens for the multicast echo "
          f"(≤{VERIFY_TIMEOUT:.0f}s timeout).{RESET}\n")
    _sep()

    # Step 1: fetch companies
    print(f"  {BOLD}[1/3]{RESET}  GET {REST_BASE}/allCompanies")
    try:
        with urllib.request.urlopen(f"{REST_BASE}/allCompanies", timeout=5) as r:
            companies = json.loads(r.read())
    except urllib.error.URLError as exc:
        print(f"        {RED}✗  Cannot reach Spring Boot: {exc.reason}{RESET}")
        print(f"        {DIM}Make sure the server is running on port 8080.{RESET}\n")
        return
    except Exception as exc:
        print(f"        {RED}✗  {exc}{RESET}\n")
        return

    if not companies:
        print(f"        {RED}✗  No companies found — create one in Django admin first.{RESET}\n")
        return

    c = companies[0]
    print(f"        {GREEN}✓{RESET}  {len(companies)} compan"
          f"{'y' if len(companies) == 1 else 'ies'} found.  "
          f"Using {BOLD}{c['symbol']}{RESET} "
          f"(price ₹{c['currentPrice']:,})")

    # Step 2: open socket BEFORE placing order to avoid missing the broadcast
    print(f"\n  {BOLD}[2/3]{RESET}  Joining multicast group {MCAST_GROUP}:{MCAST_PORT}")
    try:
        sock = _make_socket()
        sock.settimeout(VERIFY_TIMEOUT)
        print(f"        {GREEN}✓{RESET}  Listening.")
    except OSError as exc:
        print(f"        {RED}✗  Socket error: {exc}{RESET}")
        print(f"        {DIM}Try running as administrator or add a firewall exception "
              f"for UDP port {MCAST_PORT}.{RESET}\n")
        return

    # Step 3: place order
    print(f"\n  {BOLD}[3/3]{RESET}  POST {REST_BASE}/orders  "
          f"(BUY MARKET 1×{c['symbol']})")
    body = json.dumps({
        "companyId":   c["companyId"],
        "symbol":      c["symbol"],
        "buySell":     True,
        "marketLimit": True,
        "shares":      1,
        "price":       0,
    }).encode()
    try:
        req = urllib.request.Request(
            f"{REST_BASE}/orders",
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        t_order = time.monotonic()
        with urllib.request.urlopen(req, timeout=5) as r:
            resp = json.loads(r.read())
        order_ms = (time.monotonic() - t_order) * 1000
        print(f"        {GREEN}✓{RESET}  {resp.get('status')} — "
              f"{resp.get('message', '')}  {DIM}({order_ms:.0f}ms){RESET}")
    except Exception as exc:
        print(f"        {RED}✗  Order failed: {exc}{RESET}\n")
        sock.close()
        return

    # Step 4: wait for echo
    print(f"\n  Waiting up to {VERIFY_TIMEOUT:.0f}s for multicast echo ...")
    t_wait = time.monotonic()
    try:
        raw, addr = sock.recvfrom(BUFFER_SIZE)
        rtt_ms = (time.monotonic() - t_wait) * 1000
        msg = _parse(raw)
        _sep()
        if msg:
            print(f"\n  {GREEN}{BOLD}✓  Pipeline OK — multicast echo in {rtt_ms:.0f}ms{RESET}")
            print(f"\n  {'From':12}: {addr[0]}:{addr[1]}")
            print(f"  {'Company':12}: {msg.get('Company id')}")
            price = int(msg.get("current price", 0))
            ts    = int(msg.get("timestamp", 0))
            print(f"  {'Price':12}: ₹{price:,}")
            print(f"  {'Timestamp':12}: {_ts(ts)}")
            raw_str = raw.decode(errors="replace")
            if len(raw_str) < 200:
                print(f"  {'Raw JSON':12}: {raw_str}")
        else:
            print(f"\n  {YELLOW}⚠  Packet received in {rtt_ms:.0f}ms but could not "
                  f"parse as JSON ({len(raw)} bytes).{RESET}")
            print(f"  Raw: {raw[:120]!r}")
    except socket.timeout:
        _sep()
        print(f"\n  {RED}✗  No multicast packet received within {VERIFY_TIMEOUT:.0f}s.{RESET}")
        print(f"""
  {DIM}Possible causes:
    1. Windows Firewall is blocking inbound UDP on port {MCAST_PORT}.
       Fix: netsh advfirewall firewall add rule name="Jasdaq Multicast" \\
                  protocol=UDP dir=in localport={MCAST_PORT} action=allow
    2. The placed order was rejected by the engine (no matching counterpart).
       The engine only broadcasts after calling placeOrder — even rejected
       orders trigger a broadcast, so this is unlikely.
    3. The multicast interface is not the loopback adapter.
       The Java sender uses the OS default multicast route; if that is a
       physical NIC, join on that interface's IP instead of 0.0.0.0.
    4. IP_MULTICAST_LOOP is disabled on the sender's socket.{RESET}
""")
    finally:
        sock.close()


# ─── Entry ────────────────────────────────────────────────────────────────────

def main() -> None:
    if sys.version_info < (3, 9):
        print(RED + "Python 3.9+ required." + RESET)
        sys.exit(1)
    if "--verify" in sys.argv:
        verify_mode()
    else:
        listen_forever()


if __name__ == "__main__":
    main()
