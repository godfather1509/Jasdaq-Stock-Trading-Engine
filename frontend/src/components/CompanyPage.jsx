import { useState, useEffect, useRef, useCallback } from "react";
import { useParams } from "react-router-dom";
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts";
import api from "../../api/apiConfig";
import SockJS from "sockjs-client";
import Stomp from "stompjs";

const BG       = "#0f172a";
const SURFACE  = "#1e293b";
const RAISED   = "#162032";
const ELEVATED = "#0d1625";
const BORDER   = "#334155";
const TEXT     = "#f1f5f9";
const TEXT_SEC = "#94a3b8";
const TEXT_DIM = "#64748b";
const INDIGO   = "#6366f1";
const INDIGO_LT= "#818cf8";
const GREEN    = "#10b981";
const GREEN_LT = "#34d399";
const RED      = "#ef4444";
const RED_LT   = "#f87171";

const card = {
    background: SURFACE,
    border: `1px solid ${BORDER}`,
    borderRadius: "12px",
};

const label = {
    fontSize: "11px", fontWeight: 600, color: TEXT_DIM,
    textTransform: "uppercase", letterSpacing: "0.07em",
    display: "block", marginBottom: "6px"
};

function CompanyPage() {
    const { companyId, companySymbol } = useParams();
    const [c, setC] = useState({});
    const [o, setO] = useState([]);
    const [chartData, setChartData] = useState([]);
    const [toasts, setToasts] = useState([]);
    const [isLive, setIsLive] = useState(true);
    const [windowSize, setWindowSize] = useState(60);
    const [windowEnd,  setWindowEnd]  = useState(0);
    const [orderPage, setOrderPage] = useState(0);
    const ORDER_PS = 10;
    const [sellPage, setSellPage] = useState(0);
    const [buyPage,  setBuyPage]  = useState(0);
    const BOOK_PS = 8;
    const [metrics, setMetrics] = useState({
        totalBuyShares: 0, totalSellShares: 0,
        buyLimitOrders: 0, sellLimitOrders: 0,
        buyMarketOrders: 0, sellMarketOrders: 0,
        currentPrice: 0
    });
    const [stats, setStats] = useState({ totalTrades: 0, totalVolume: 0, totalValueTraded: 0 });
    const [form, setForm] = useState({ quantity: "", price: "", type: "market", side: "buy" });

    const stompClient = useRef(null);
    const toastCounterRef = useRef(0);
    const pendingOrderRef = useRef(null);

    const fetchMetrics = useCallback(async () => {
        try {
            const res = await api.get(`${companyId}/metrics`);
            setMetrics(res.data);
        } catch (err) { console.warn("Failed to fetch metrics", err); }
    }, [companyId]);

    const fetchStats = useCallback(async () => {
        try {
            const res = await api.get(`${companyId}/stats`);
            setStats(res.data);
        } catch (err) { console.warn("Failed to fetch stats", err); }
    }, [companyId]);

    const showToast = (reason, type = "error") => {
        const id = ++toastCounterRef.current;
        setToasts(prev => [...prev, { id, reason, type }]);
        setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 6000);
    };

    const dismissToast = (id) => setToasts(prev => prev.filter(t => t.id !== id));

    useEffect(() => {
        const socket = new SockJS("http://localhost:8080/ws");
        const client = Stomp.over(socket);
        client.debug = null;

        client.connect({}, () => {
            stompClient.current = client;

            client.subscribe("/topic/market-updates", (msg) => {
                const update = JSON.parse(msg.body);
                if (update.companyId === companyId) {
                    setC(prev => ({ ...prev, currentPrice: update.price }));
                    const now = Date.now();
                    const time = new Date(now).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
                    setChartData(prev => [...prev, { time, price: update.price, ts: now }].slice(-2000));
                }
            });

            client.subscribe("/topic/orders", (msg) => {
                const updatedOrder = JSON.parse(msg.body);
                if (updatedOrder.symbol?.toUpperCase() !== companySymbol?.toUpperCase()) return;
                if (pendingOrderRef.current) {
                    clearTimeout(pendingOrderRef.current);
                    pendingOrderRef.current = null;
                }
                setO(prev => {
                    const exists = prev.find(item => item.orderId === updatedOrder.orderId);
                    if (exists) return prev.map(item => item.orderId === updatedOrder.orderId ? updatedOrder : item);
                    return [updatedOrder, ...prev];
                });
                // Bid/ask volumes change whenever an order is added, matched, or cancelled —
                // re-fetch immediately instead of waiting for the next 3-second poll.
                fetchMetrics();
            });

            client.subscribe("/topic/cancel", (msg) => {
                const canceledOrder = JSON.parse(msg.body);
                setO(prev => prev.filter(item => item.orderId !== canceledOrder.orderId));
                fetchMetrics();
            });

            client.subscribe("/topic/order-rejected", (msg) => {
                const rejection = JSON.parse(msg.body);
                showToast(rejection.reason || "Order was rejected by the matching engine.", "error");
            });
        });

        return () => { if (stompClient.current) stompClient.current.disconnect(); };
    }, [companyId, fetchMetrics]);

    const handleChange = (e) => {
        const { name, value } = e.target;
        setForm(prev => ({ ...prev, [name]: value }));
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        const qty = parseInt(form.quantity);
        const px  = parseInt(form.price) || 0;
        if (!qty || qty < 1) { showToast("Quantity must be at least 1.", "error"); return; }
        if (form.type === "limit" && px < 1) { showToast("Limit price must be at least 1.", "error"); return; }
        if (stompClient.current && stompClient.current.connected) {
            const isMkt = form.type === "market";
            const orderPayload = {
                symbol: companySymbol,
                buySell: form.side === "buy",
                marketLimit: isMkt,
                shares: qty,
                price: isMkt ? 0 : px,
                companyId
            };
            if (isMkt) {
                const side = form.side.toUpperCase();
                const opposite = form.side === "buy" ? "SELL" : "BUY";
                const timer = setTimeout(() => {
                    pendingOrderRef.current = null;
                    showToast(`${side} market order rejected: No ${opposite} orders available. Place a LIMIT order instead.`, "error");
                }, 4000);
                pendingOrderRef.current = timer;
            }
            stompClient.current.send("/app/placeOrder", {}, JSON.stringify(orderPayload));
            setForm(prev => ({ ...prev, quantity: "", price: "" }));
        } else {
            showToast("WebSocket not connected. Please refresh the page.", "error");
        }
    };

    const handleCancelOrder = (orderId) => {
        if (stompClient.current && stompClient.current.connected) {
            stompClient.current.send("/app/cancelOrder", {}, JSON.stringify({ orderId, companyId }));
        }
    };

    useEffect(() => {
        const fetchCompany = async () => {
            try {
                const res = await api.get(companyId);
                if (res.data) setC(res.data);
            } catch (err) { console.error(err); }
        };

        const fetchOrders = async () => {
            try {
                const res = await api.get(`${companyId}/orders`);
                if (Array.isArray(res.data)) setO(res.data);
            } catch (err) { console.warn("Failed to fetch orders", err); }
        };

        const fetchTrades = async () => {
            try {
                const res = await api.get(`${companyId}/trades`);
                // Endpoint returns DESC (newest first) — reverse so chart reads left=oldest, right=newest
                const trades = (Array.isArray(res.data) ? res.data : res.data?.content ?? []).reverse();
                if (trades.length > 0) {
                    const mapped = trades.slice(-2000).map(t => ({
                        time: new Date(t.tradeTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
                        price: t.price,
                        ts: t.tradeTime,
                    }));
                    setChartData(mapped);
                } else {
                    try {
                        const compRes = await api.get(companyId);
                        if (compRes.data && compRes.data.currentPrice) {
                            const now = Date.now();
                            const time = new Date(now).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
                            setChartData([{ time, price: compRes.data.currentPrice, ts: now }]);
                        }
                    } catch (e) {
                        console.warn("Failed to fetch company for chart fallback", e);
                    }
                }
            } catch (err) { console.warn("Failed to fetch historical trades", err); }
        };

        fetchCompany();
        fetchOrders();
        fetchMetrics();
        fetchTrades();
        fetchStats();
        const metricsInterval = setInterval(fetchMetrics, 3000);
        const companyInterval = setInterval(fetchCompany, 5000);
        const statsInterval   = setInterval(fetchStats, 30000);
        return () => { clearInterval(metricsInterval); clearInterval(companyInterval); clearInterval(statsInterval); };
    }, [companyId]);

    const inputStyle = {
        width: "100%", background: ELEVATED, border: `1px solid ${BORDER}`,
        borderRadius: "8px", padding: "10px 14px", color: TEXT,
        fontSize: "14px", outline: "none", transition: "border-color 0.15s",
        fontFamily: "'Inter', system-ui, sans-serif"
    };

    const isBuy = form.side === "buy";
    const btnColor = isBuy ? GREEN : RED;

    return (
        <div style={{ background: BG, color: TEXT, minHeight: "100vh", padding: "28px 32px", fontFamily: "'Inter', system-ui, sans-serif" }}>

            {/* Toast stack — newest on top, older cards peek behind */}
            {toasts.length > 0 && (() => {
                const visible = toasts.slice(-3); // keep at most 3 in the stack
                return (
                    <div style={{
                        position: "fixed", top: "20px", right: "20px",
                        zIndex: 9999, width: "380px",
                    }}>
                        {visible.map((toast, rawIdx) => {
                            // newest is last in array → smallest stackDepth → front
                            const stackDepth = visible.length - 1 - rawIdx;
                            const isFront = stackDepth === 0;
                            return (
                                <div key={toast.id} style={{
                                    position: "absolute", top: 0, left: 0, right: 0,
                                    zIndex: visible.length - stackDepth,
                                    transform: `translateY(${stackDepth * 8}px) scale(${1 - stackDepth * 0.04})`,
                                    transformOrigin: "top center",
                                    opacity: 1 - stackDepth * 0.18,
                                    pointerEvents: isFront ? "auto" : "none",
                                    transition: "transform 0.3s cubic-bezier(0.16,1,0.3,1), opacity 0.3s ease",
                                    background: SURFACE, border: `1px solid ${RED}40`,
                                    borderLeft: `4px solid ${RED}`,
                                    borderRadius: "10px", padding: "14px 16px",
                                    display: "flex", alignItems: "flex-start", gap: "12px",
                                    boxShadow: `0 ${4 + stackDepth * 3}px ${20 + stackDepth * 10}px rgba(0,0,0,${0.45 - stackDepth * 0.1})`,
                                    animation: isFront ? "slideInRight 0.2s ease-out" : "none",
                                }}>
                                    <div style={{ flex: 1, minWidth: 0 }}>
                                        <div style={{ display: "flex", alignItems: "center", gap: "6px", marginBottom: "4px" }}>
                                            <p style={{ color: RED_LT, fontWeight: 600, fontSize: "12px", margin: 0 }}>
                                                Order Rejected
                                            </p>
                                            {toasts.length > 1 && isFront && (
                                                <span style={{
                                                    background: `${RED}25`, color: RED_LT,
                                                    fontSize: "10px", fontWeight: 700,
                                                    padding: "1px 6px", borderRadius: "999px",
                                                }}>
                                                    {toasts.length}
                                                </span>
                                            )}
                                        </div>
                                        <p style={{ color: TEXT_SEC, fontSize: "13px", margin: 0, lineHeight: 1.5 }}>
                                            {toast.reason}
                                        </p>
                                    </div>
                                    {isFront && (
                                        <button onClick={() => dismissToast(toast.id)} style={{
                                            background: "none", border: "none", cursor: "pointer",
                                            color: TEXT_DIM, fontSize: "16px", lineHeight: 1,
                                            padding: "2px", flexShrink: 0,
                                        }}>✕</button>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                );
            })()}

            {/* Page header */}
            <div style={{ marginBottom: "28px", display: "flex", alignItems: "flex-start", justifyContent: "space-between" }}>
                <div>
                    <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
                        <div style={{
                            width: "40px", height: "40px", borderRadius: "10px",
                            background: `${INDIGO}20`, border: `1px solid ${INDIGO}40`,
                            display: "flex", alignItems: "center", justifyContent: "center",
                            fontSize: "16px", fontWeight: 700, color: INDIGO
                        }}>
                            {c.symbol?.[0]}
                        </div>
                        <div>
                            <h1 style={{ fontSize: "22px", fontWeight: 700, color: TEXT, margin: 0 }}>{c.name}</h1>
                            <p style={{ fontSize: "13px", color: TEXT_DIM, margin: "2px 0 0 0", fontWeight: 500 }}>{c.symbol}</p>
                        </div>
                    </div>
                </div>
                <div style={{ textAlign: "right" }}>
                    <p style={{ fontSize: "11px", color: TEXT_DIM, margin: "0 0 4px 0", fontWeight: 500, textTransform: "uppercase", letterSpacing: "0.06em" }}>Market Price</p>
                    <p style={{ fontSize: "28px", fontWeight: 700, color: GREEN, margin: 0, fontVariantNumeric: "tabular-nums" }}>
                        ₹{c.currentPrice?.toLocaleString()}
                    </p>
                    {(() => {
                        const pct = c.initialPrice > 0
                            ? ((c.currentPrice - c.initialPrice) / c.initialPrice * 100)
                            : 0;
                        const col  = pct >= 0 ? GREEN_LT : RED_LT;
                        const sign = pct >= 0 ? "+" : "";
                        return (
                            <p style={{ fontSize: "13px", color: col, margin: "4px 0 0 0", fontWeight: 600, fontVariantNumeric: "tabular-nums" }}>
                                {sign}{pct.toFixed(2)}% since listing
                            </p>
                        );
                    })()}
                </div>
            </div>

            {/* Top row: metrics + form + chart */}
            <div style={{ display: "grid", gridTemplateColumns: "340px 1fr", gap: "20px", marginBottom: "20px", alignItems: "start" }}>

                {/* Left: metrics + order form */}
                <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>

                    {/* Market Cap */}
                    <div style={{ ...card, padding: "14px 16px", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                        <div>
                            <p style={{ fontSize: "11px", fontWeight: 600, color: TEXT_DIM, textTransform: "uppercase", letterSpacing: "0.07em", margin: "0 0 4px 0" }}>
                                Market Cap
                            </p>
                            <p style={{ fontSize: "11px", color: TEXT_DIM, margin: 0 }}>
                                Current price × Total shares
                            </p>
                        </div>
                        <p style={{ fontSize: "20px", fontWeight: 700, color: INDIGO_LT, margin: 0, fontVariantNumeric: "tabular-nums" }}>
                            ₹{((c.currentPrice ?? 0) * (c.totalShares ?? 0)).toLocaleString()}
                        </p>
                    </div>

                    {/* % from Listing / % from ATH */}
                    {(() => {
                        const price   = c.currentPrice ?? 0;
                        const listing = c.initialPrice ?? 0;
                        const ath     = c.allTimeHigh  ?? 0;
                        const pctList = listing > 0 ? (price - listing) / listing * 100 : 0;
                        const pctAth  = ath     > 0 ? (price - ath)    / ath     * 100 : 0;
                        const sign    = v => v >= 0 ? "+" : "";
                        const rows = [
                            { lbl: "From Listing", pct: pctList, base: listing },
                            { lbl: "From ATH",     pct: pctAth,  base: ath     },
                        ];
                        return (
                            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
                                {rows.map(({ lbl, pct, base }) => {
                                    const col    = pct >= 0 ? GREEN_LT : RED_LT;
                                    const border = pct >= 0 ? `${GREEN}30` : `${RED}30`;
                                    return (
                                        <div key={lbl} style={{ ...card, padding: "16px", border: `1px solid ${border}` }}>
                                            <p style={{ fontSize: "11px", fontWeight: 600, color: TEXT_DIM, textTransform: "uppercase", letterSpacing: "0.07em", margin: "0 0 8px 0" }}>
                                                {lbl}
                                            </p>
                                            <p style={{ fontSize: "22px", fontWeight: 700, color: col, margin: "0 0 4px 0", fontVariantNumeric: "tabular-nums" }}>
                                                {sign(pct)}{pct.toFixed(2)}%
                                            </p>
                                            <p style={{ fontSize: "11px", color: TEXT_DIM, margin: 0, fontVariantNumeric: "tabular-nums" }}>
                                                base ₹{base.toLocaleString()}
                                            </p>
                                        </div>
                                    );
                                })}
                            </div>
                        );
                    })()}

                    {/* Shares in circulation */}
                    {(() => {
                        const free       = c.availableShares ?? 0;
                        const sellDepth  = metrics.totalSellShares ?? 0;
                        // "total" is just the user-owned pool — always ≤ totalShares.
                        // totalSellShares is the order-book sell depth (includes IPO supply),
                        // NOT user-owned shares, so it must not be added to availableShares.
                        const total = free;
                        return (
                            <div style={{ ...card, padding: "14px 16px" }}>
                                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "8px" }}>
                                    <p style={{ fontSize: "11px", fontWeight: 600, color: TEXT_DIM, textTransform: "uppercase", letterSpacing: "0.07em", margin: 0 }}>
                                        Shares in Circulation
                                    </p>
                                    <p style={{ fontSize: "20px", fontWeight: 700, color: INDIGO_LT, margin: 0, fontVariantNumeric: "tabular-nums" }}>
                                        {total.toLocaleString()}
                                        <span style={{ fontSize: "11px", fontWeight: 400, color: TEXT_DIM, marginLeft: "5px" }}>
                                            / {c.totalShares?.toLocaleString()}
                                        </span>
                                    </p>
                                </div>
                                <div style={{ display: "flex", gap: "8px" }}>
                                    <span style={{ fontSize: "10px", fontWeight: 600, padding: "2px 8px", borderRadius: "4px", background: `${GREEN}18`, color: GREEN_LT }}>
                                        {free.toLocaleString()} owned (can sell)
                                    </span>
                                    <span style={{ fontSize: "10px", fontWeight: 600, padding: "2px 8px", borderRadius: "4px", background: `${RED}18`, color: RED_LT }}>
                                        {sellDepth.toLocaleString()} in pending sell orders
                                    </span>
                                </div>
                            </div>
                        );
                    })()}

                    {/* Price Details */}
                    <div style={{ ...card, padding: "14px 16px" }}>
                        <p style={{ fontSize: "11px", fontWeight: 600, color: TEXT_DIM, textTransform: "uppercase", letterSpacing: "0.07em", margin: "0 0 10px 0" }}>
                            Price Details
                        </p>
                        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10px 16px" }}>
                            <div>
                                <p style={{ fontSize: "10px", color: TEXT_DIM, margin: "0 0 3px 0", fontWeight: 500 }}>Listing Price</p>
                                <p style={{ fontSize: "17px", fontWeight: 700, color: TEXT, margin: 0, fontVariantNumeric: "tabular-nums" }}>
                                    ₹{(c.initialPrice ?? 0).toLocaleString()}
                                </p>
                            </div>
                            <div>
                                <p style={{ fontSize: "10px", color: TEXT_DIM, margin: "0 0 3px 0", fontWeight: 500 }}>All-Time High</p>
                                <p style={{ fontSize: "17px", fontWeight: 700, color: GREEN_LT, margin: 0, fontVariantNumeric: "tabular-nums" }}>
                                    ₹{(c.allTimeHigh ?? 0).toLocaleString()}
                                </p>
                            </div>
                        </div>
                    </div>

                    {/* Trading Activity */}
                    {(() => {
                        const fmt = n => {
                            if (n >= 1e7)  return `₹${(n / 1e7).toFixed(2)} Cr`;
                            if (n >= 1e5)  return `₹${(n / 1e5).toFixed(2)} L`;
                            if (n >= 1000) return `₹${(n / 1000).toFixed(1)} K`;
                            return `₹${n.toLocaleString()}`;
                        };
                        return (
                            <div style={{ ...card, padding: "14px 16px" }}>
                                <p style={{ fontSize: "11px", fontWeight: 600, color: TEXT_DIM, textTransform: "uppercase", letterSpacing: "0.07em", margin: "0 0 10px 0" }}>
                                    Trading Activity
                                </p>
                                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "8px" }}>
                                    <div>
                                        <p style={{ fontSize: "10px", color: TEXT_DIM, margin: "0 0 3px 0", fontWeight: 500 }}>Total Trades</p>
                                        <p style={{ fontSize: "17px", fontWeight: 700, color: TEXT, margin: 0, fontVariantNumeric: "tabular-nums" }}>
                                            {(stats.totalTrades ?? 0).toLocaleString()}
                                        </p>
                                    </div>
                                    <div>
                                        <p style={{ fontSize: "10px", color: TEXT_DIM, margin: "0 0 3px 0", fontWeight: 500 }}>Volume (shares)</p>
                                        <p style={{ fontSize: "17px", fontWeight: 700, color: TEXT, margin: 0, fontVariantNumeric: "tabular-nums" }}>
                                            {(stats.totalVolume ?? 0).toLocaleString()}
                                        </p>
                                    </div>
                                    <div>
                                        <p style={{ fontSize: "10px", color: TEXT_DIM, margin: "0 0 3px 0", fontWeight: 500 }}>Turnover</p>
                                        <p style={{ fontSize: "15px", fontWeight: 700, color: INDIGO_LT, margin: 0, fontVariantNumeric: "tabular-nums" }}>
                                            {fmt(stats.totalValueTraded ?? 0)}
                                        </p>
                                    </div>
                                </div>
                            </div>
                        );
                    })()}

                    {/* Order form */}
                    <div style={{ ...card, padding: "20px" }}>
                        <p style={{ fontSize: "14px", fontWeight: 600, color: TEXT, margin: "0 0 16px 0" }}>Place Order</p>
                        <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "14px" }}>

                            {/* Buy / Sell toggle */}
                            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", borderRadius: "8px", overflow: "hidden", border: `1px solid ${BORDER}` }}>
                                {["buy", "sell"].map(side => (
                                    <button
                                        key={side}
                                        type="button"
                                        onClick={() => setForm(prev => ({ ...prev, side }))}
                                        style={{
                                            padding: "10px", border: "none", cursor: "pointer",
                                            fontWeight: 600, fontSize: "13px",
                                            fontFamily: "'Inter', system-ui, sans-serif",
                                            background: form.side === side
                                                ? (side === "buy" ? GREEN : RED)
                                                : ELEVATED,
                                            color: form.side === side ? "#fff" : TEXT_DIM,
                                            transition: "background 0.15s, color 0.15s"
                                        }}
                                    >
                                        {side.toUpperCase()}
                                    </button>
                                ))}
                            </div>

                            <div>
                                <label style={label}>Order Type</label>
                                <select name="type" value={form.type} onChange={handleChange} style={{ ...inputStyle, cursor: "pointer" }}>
                                    <option value="market">Market</option>
                                    <option value="limit">Limit</option>
                                </select>
                                <p style={{ fontSize: "11px", color: TEXT_DIM, margin: "6px 0 0 0", lineHeight: 1.5 }}>
                                    {form.type === "market"
                                        ? "Executes immediately at the best available price. No price guarantee."
                                        : "Executes only at your specified price or better. Stays in the book until matched."}
                                </p>
                            </div>

                            <div>
                                <label style={label}>Quantity (shares)</label>
                                <input
                                    type="number" name="quantity" value={form.quantity}
                                    onChange={handleChange} placeholder="0"
                                    min="1" step="1"
                                    style={inputStyle}
                                    onFocus={e => e.target.style.borderColor = INDIGO}
                                    onBlur={e => e.target.style.borderColor = BORDER}
                                    required
                                />
                            </div>

                            {form.type === "limit" && (
                                <div>
                                    <label style={label}>Limit Price <span style={{ fontWeight: 400, textTransform: "none", letterSpacing: 0 }}>(per share)</span></label>
                                    <input
                                        type="number" name="price" value={form.price}
                                        onChange={handleChange} placeholder="0"
                                        min="1" step="1"
                                        style={inputStyle}
                                        onFocus={e => e.target.style.borderColor = INDIGO}
                                        onBlur={e => e.target.style.borderColor = BORDER}
                                        required
                                    />
                                </div>
                            )}

                            <button
                                type="submit"
                                style={{
                                    background: isBuy ? GREEN : RED,
                                    color: "#fff", border: "none", borderRadius: "8px",
                                    padding: "12px", fontWeight: 600, fontSize: "14px",
                                    cursor: "pointer", transition: "opacity 0.15s",
                                    fontFamily: "'Inter', system-ui, sans-serif"
                                }}
                                onMouseEnter={e => e.currentTarget.style.opacity = "0.85"}
                                onMouseLeave={e => e.currentTarget.style.opacity = "1"}
                            >
                                {isBuy ? "Buy" : "Sell"} {companySymbol}
                            </button>
                        </form>
                    </div>
                </div>

                {/* Right: chart + order book */}
                <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
                    {(() => {
                        const n = chartData.length;

                        // Visible slice
                        const endIdx   = isLive ? n - 1 : Math.min(windowEnd, n - 1);
                        const startIdx = Math.max(0, endIdx - windowSize + 1);
                        const visibleData = n > 0 ? chartData.slice(startIdx, endIdx + 1) : [];

                        const zoomIn  = () => setWindowSize(s => Math.max(10, Math.floor(s * 0.6)));
                        const zoomOut = () => setWindowSize(s => Math.min(Math.max(n, 10), Math.ceil(s * 1.667)));

                        const panStep = Math.max(1, Math.floor(windowSize * 0.25));
                        const panLeft = () => {
                            const cur = isLive ? n - 1 : windowEnd;
                            setWindowEnd(Math.max(windowSize - 1, cur - panStep));
                            setIsLive(false);
                        };
                        const panRight = () => {
                            const cur  = isLive ? n - 1 : windowEnd;
                            const next = Math.min(n - 1, cur + panStep);
                            setWindowEnd(next);
                            setIsLive(next >= n - 1);
                        };

                        const handleRangeClick = (minutes) => {
                            setIsLive(false);
                            if (minutes === null) {
                                setWindowSize(Math.max(n, 10));
                                setWindowEnd(n - 1);
                            } else {
                                const cutoff = Date.now() - minutes * 60 * 1000;
                                const idx = chartData.findIndex(d => d.ts >= cutoff);
                                const count = idx === -1 ? n : n - idx;
                                setWindowSize(Math.max(count, 10));
                                setWindowEnd(n - 1);
                            }
                        };

                        const RANGES = [["1m", 1], ["5m", 5], ["15m", 15], ["1h", 60], ["All", null]];

                        const canZoomIn   = windowSize > 10;
                        const canZoomOut  = windowSize < n;
                        const canPanLeft  = !isLive && startIdx > 0;
                        const canPanRight = !isLive && endIdx < n - 1;

                        const ctrlBtn = (active) => ({
                            background: "none",
                            border: `1px solid ${BORDER}`,
                            color: active ? TEXT_SEC : TEXT_DIM,
                            cursor: active ? "pointer" : "default",
                            fontSize: "13px", fontWeight: 500,
                            padding: "2px 9px", borderRadius: "4px",
                            opacity: active ? 1 : 0.35,
                            fontFamily: "'Inter', system-ui, sans-serif",
                            lineHeight: 1.4,
                            transition: "color 0.12s, border-color 0.12s",
                        });

                        return (
                            <div style={{ ...card, padding: "20px 24px" }}>
                                {/* Header */}
                                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "16px" }}>
                                    <div style={{ display: "flex", alignItems: "baseline", gap: "8px" }}>
                                        <p style={{ fontSize: "13px", fontWeight: 600, color: TEXT_SEC, margin: 0 }}>Price Chart</p>
                                        <span style={{ fontSize: "11px", color: TEXT_DIM }}>
                                            {visibleData.length}/{n} pts
                                        </span>
                                    </div>
                                    <div style={{ display: "flex", gap: "5px", alignItems: "center" }}>
                                        {/* Zoom */}
                                        <button onClick={zoomOut} disabled={!canZoomOut} style={ctrlBtn(canZoomOut)}
                                            onMouseEnter={e => { if (canZoomOut) { e.currentTarget.style.color = TEXT; e.currentTarget.style.borderColor = TEXT_SEC; }}}
                                            onMouseLeave={e => { e.currentTarget.style.color = canZoomOut ? TEXT_SEC : TEXT_DIM; e.currentTarget.style.borderColor = BORDER; }}>−</button>
                                        <button onClick={zoomIn} disabled={!canZoomIn} style={ctrlBtn(canZoomIn)}
                                            onMouseEnter={e => { if (canZoomIn) { e.currentTarget.style.color = TEXT; e.currentTarget.style.borderColor = TEXT_SEC; }}}
                                            onMouseLeave={e => { e.currentTarget.style.color = canZoomIn ? TEXT_SEC : TEXT_DIM; e.currentTarget.style.borderColor = BORDER; }}>+</button>

                                        <span style={{ width: "1px", height: "14px", background: BORDER, margin: "0 2px", display: "inline-block" }} />

                                        {/* Pan */}
                                        <button onClick={panLeft} disabled={!canPanLeft} style={ctrlBtn(canPanLeft)}
                                            onMouseEnter={e => { if (canPanLeft) { e.currentTarget.style.color = TEXT; e.currentTarget.style.borderColor = TEXT_SEC; }}}
                                            onMouseLeave={e => { e.currentTarget.style.color = canPanLeft ? TEXT_SEC : TEXT_DIM; e.currentTarget.style.borderColor = BORDER; }}>‹</button>
                                        <button onClick={panRight} disabled={!canPanRight} style={ctrlBtn(canPanRight)}
                                            onMouseEnter={e => { if (canPanRight) { e.currentTarget.style.color = TEXT; e.currentTarget.style.borderColor = TEXT_SEC; }}}
                                            onMouseLeave={e => { e.currentTarget.style.color = canPanRight ? TEXT_SEC : TEXT_DIM; e.currentTarget.style.borderColor = BORDER; }}>›</button>

                                        <span style={{ width: "1px", height: "14px", background: BORDER, margin: "0 2px", display: "inline-block" }} />

                                        {/* Range presets */}
                                        {RANGES.map(([lbl, mins]) => (
                                            <button key={lbl} onClick={() => handleRangeClick(mins)} style={{
                                                background: "none", border: `1px solid ${BORDER}`,
                                                color: TEXT_DIM, fontSize: "11px", fontWeight: 600,
                                                padding: "3px 9px", borderRadius: "4px", cursor: "pointer",
                                                fontFamily: "'Inter', system-ui, sans-serif",
                                                transition: "color 0.12s, border-color 0.12s",
                                            }}
                                                onMouseEnter={e => { e.currentTarget.style.color = TEXT; e.currentTarget.style.borderColor = TEXT_SEC; }}
                                                onMouseLeave={e => { e.currentTarget.style.color = TEXT_DIM; e.currentTarget.style.borderColor = BORDER; }}
                                            >{lbl}</button>
                                        ))}

                                        <span style={{ width: "1px", height: "14px", background: BORDER, margin: "0 2px", display: "inline-block" }} />

                                        {/* Live toggle */}
                                        <button onClick={() => setIsLive(true)} style={{
                                            background: isLive ? `${GREEN}18` : "none",
                                            border: `1px solid ${isLive ? GREEN : BORDER}`,
                                            color: isLive ? GREEN_LT : TEXT_DIM,
                                            fontSize: "11px", fontWeight: 700,
                                            padding: "3px 10px", borderRadius: "4px", cursor: "pointer",
                                            fontFamily: "'Inter', system-ui, sans-serif",
                                            display: "flex", alignItems: "center", gap: "5px",
                                            transition: "all 0.15s",
                                        }}>
                                            <span style={{
                                                width: "6px", height: "6px", borderRadius: "50%",
                                                background: isLive ? GREEN : TEXT_DIM,
                                                display: "inline-block",
                                            }} />
                                            LIVE
                                        </button>
                                    </div>
                                </div>

                                <ResponsiveContainer width="100%" height={320}>
                                    <LineChart data={visibleData} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                                        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={`${BORDER}80`} />
                                        <XAxis
                                            dataKey="time"
                                            axisLine={false} tickLine={false}
                                            tick={{ fill: TEXT_DIM, fontSize: 11, fontFamily: "'Inter', sans-serif" }}
                                            interval="preserveStartEnd"
                                            minTickGap={60}
                                        />
                                        <YAxis
                                            domain={["auto", "auto"]}
                                            axisLine={false} tickLine={false}
                                            tick={{ fill: TEXT_DIM, fontSize: 11, fontFamily: "'Inter', sans-serif" }}
                                            tickFormatter={v => `₹${Number(v).toLocaleString()}`}
                                            width={90}
                                        />
                                        <Tooltip
                                            contentStyle={{
                                                background: SURFACE, border: `1px solid ${BORDER}`,
                                                borderRadius: "8px", color: TEXT, fontSize: "13px",
                                                fontFamily: "'Inter', sans-serif",
                                            }}
                                            labelStyle={{ color: TEXT_SEC }}
                                            formatter={v => [`₹${Number(v).toLocaleString()}`, "Price"]}
                                        />
                                        <Line
                                            type="monotone" dataKey="price" stroke={INDIGO}
                                            strokeWidth={2} dot={false}
                                            activeDot={{ r: 4, strokeWidth: 0, fill: INDIGO_LT }}
                                            isAnimationActive={false}
                                        />
                                    </LineChart>
                                </ResponsiveContainer>
                            </div>
                        );
                    })()}

                    {/* Order Book */}
                    <div style={{ ...card, padding: "24px" }}>
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "20px" }}>
                            <p style={{ fontSize: "15px", fontWeight: 600, color: TEXT, margin: 0 }}>Order Book</p>
                            <span style={{ fontSize: "12px", color: TEXT_DIM }}>Click a row to pre-fill the order form</span>
                        </div>
                        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "24px" }}>

                            {/* Sell Orders */}
                            {(() => {
                                const allSell = o.filter(ord => !ord.buySell && !ord.status && ord.shares > 0 && !ord.marketLimit).sort((a, b) => a.price - b.price);
                                const totalSellPages = Math.max(1, Math.ceil(allSell.length / BOOK_PS));
                                const sp = Math.min(sellPage, totalSellPages - 1);
                                const pageSell = allSell.slice(sp * BOOK_PS, (sp + 1) * BOOK_PS);
                                return (
                                    <div>
                                        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "12px" }}>
                                            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                                                <div style={{ width: "8px", height: "8px", borderRadius: "2px", background: RED }}></div>
                                                <span style={{ fontSize: "12px", fontWeight: 600, color: RED_LT }}>Sell Orders</span>
                                                <span style={{ fontSize: "11px", color: TEXT_DIM }}>{allSell.length}</span>
                                            </div>
                                            {totalSellPages > 1 && (
                                                <div style={{ display: "flex", alignItems: "center", gap: "4px" }}>
                                                    <button onClick={() => setSellPage(p => Math.max(0, p - 1))} disabled={sp === 0} style={{ background: "none", border: `1px solid ${BORDER}`, color: sp === 0 ? TEXT_DIM : TEXT_SEC, cursor: sp === 0 ? "default" : "pointer", fontSize: "11px", padding: "2px 7px", borderRadius: "4px", opacity: sp === 0 ? 0.4 : 1 }}>‹</button>
                                                    <span style={{ fontSize: "11px", color: TEXT_DIM }}>{sp + 1}/{totalSellPages}</span>
                                                    <button onClick={() => setSellPage(p => Math.min(totalSellPages - 1, p + 1))} disabled={sp === totalSellPages - 1} style={{ background: "none", border: `1px solid ${BORDER}`, color: sp === totalSellPages - 1 ? TEXT_DIM : TEXT_SEC, cursor: sp === totalSellPages - 1 ? "default" : "pointer", fontSize: "11px", padding: "2px 7px", borderRadius: "4px", opacity: sp === totalSellPages - 1 ? 0.4 : 1 }}>›</button>
                                                </div>
                                            )}
                                        </div>
                                        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "13px" }}>
                                            <thead>
                                                <tr style={{ borderBottom: `1px solid ${BORDER}` }}>
                                                    {["Price", "Shares"].map((h, i) => (
                                                        <th key={i} style={{ padding: "8px 12px", textAlign: i === 1 ? "right" : "left", fontSize: "11px", fontWeight: 600, color: TEXT_DIM, textTransform: "uppercase", letterSpacing: "0.06em" }}>{h}</th>
                                                    ))}
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {pageSell.length > 0 ? pageSell.map((ord, idx) => (
                                                    <tr key={idx} style={{ borderBottom: `1px solid ${BORDER}30`, cursor: "pointer", transition: "background 0.1s" }}
                                                        onMouseEnter={e => e.currentTarget.style.background = `${RED}08`}
                                                        onMouseLeave={e => e.currentTarget.style.background = "transparent"}
                                                        onClick={() => setForm(prev => ({ ...prev, side: "buy", type: "limit", price: String(ord.price), quantity: String(ord.shares) }))}>
                                                        <td style={{ padding: "10px 12px", color: RED_LT, fontWeight: 600, fontVariantNumeric: "tabular-nums" }}>₹{ord.price.toLocaleString()}</td>
                                                        <td style={{ padding: "10px 12px", textAlign: "right", color: TEXT_SEC, fontVariantNumeric: "tabular-nums" }}>{ord.shares.toLocaleString()}</td>
                                                    </tr>
                                                )) : (
                                                    <tr><td colSpan="2" style={{ padding: "24px 12px", textAlign: "center", fontSize: "12px", color: TEXT_DIM, fontStyle: "italic" }}>No sell orders</td></tr>
                                                )}
                                            </tbody>
                                        </table>
                                    </div>
                                );
                            })()}

                            {/* Buy Orders */}
                            {(() => {
                                const allBuy = o.filter(ord => ord.buySell && !ord.status && ord.shares > 0 && !ord.marketLimit).sort((a, b) => b.price - a.price);
                                const totalBuyPages = Math.max(1, Math.ceil(allBuy.length / BOOK_PS));
                                const bp = Math.min(buyPage, totalBuyPages - 1);
                                const pageBuy = allBuy.slice(bp * BOOK_PS, (bp + 1) * BOOK_PS);
                                return (
                                    <div>
                                        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "12px" }}>
                                            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                                                <div style={{ width: "8px", height: "8px", borderRadius: "2px", background: GREEN }}></div>
                                                <span style={{ fontSize: "12px", fontWeight: 600, color: GREEN_LT }}>Buy Orders</span>
                                                <span style={{ fontSize: "11px", color: TEXT_DIM }}>{allBuy.length}</span>
                                            </div>
                                            {totalBuyPages > 1 && (
                                                <div style={{ display: "flex", alignItems: "center", gap: "4px" }}>
                                                    <button onClick={() => setBuyPage(p => Math.max(0, p - 1))} disabled={bp === 0} style={{ background: "none", border: `1px solid ${BORDER}`, color: bp === 0 ? TEXT_DIM : TEXT_SEC, cursor: bp === 0 ? "default" : "pointer", fontSize: "11px", padding: "2px 7px", borderRadius: "4px", opacity: bp === 0 ? 0.4 : 1 }}>‹</button>
                                                    <span style={{ fontSize: "11px", color: TEXT_DIM }}>{bp + 1}/{totalBuyPages}</span>
                                                    <button onClick={() => setBuyPage(p => Math.min(totalBuyPages - 1, p + 1))} disabled={bp === totalBuyPages - 1} style={{ background: "none", border: `1px solid ${BORDER}`, color: bp === totalBuyPages - 1 ? TEXT_DIM : TEXT_SEC, cursor: bp === totalBuyPages - 1 ? "default" : "pointer", fontSize: "11px", padding: "2px 7px", borderRadius: "4px", opacity: bp === totalBuyPages - 1 ? 0.4 : 1 }}>›</button>
                                                </div>
                                            )}
                                        </div>
                                        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "13px" }}>
                                            <thead>
                                                <tr style={{ borderBottom: `1px solid ${BORDER}` }}>
                                                    {["Price", "Shares"].map((h, i) => (
                                                        <th key={i} style={{ padding: "8px 12px", textAlign: i === 1 ? "right" : "left", fontSize: "11px", fontWeight: 600, color: TEXT_DIM, textTransform: "uppercase", letterSpacing: "0.06em" }}>{h}</th>
                                                    ))}
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {pageBuy.length > 0 ? pageBuy.map((ord, idx) => (
                                                    <tr key={idx} style={{ borderBottom: `1px solid ${BORDER}30`, cursor: "pointer", transition: "background 0.1s" }}
                                                        onMouseEnter={e => e.currentTarget.style.background = `${GREEN}08`}
                                                        onMouseLeave={e => e.currentTarget.style.background = "transparent"}
                                                        onClick={() => setForm(prev => ({ ...prev, side: "sell", type: "limit", price: String(ord.price), quantity: String(ord.shares) }))}>
                                                        <td style={{ padding: "10px 12px", color: GREEN_LT, fontWeight: 600, fontVariantNumeric: "tabular-nums" }}>₹{ord.price.toLocaleString()}</td>
                                                        <td style={{ padding: "10px 12px", textAlign: "right", color: TEXT_SEC, fontVariantNumeric: "tabular-nums" }}>{ord.shares.toLocaleString()}</td>
                                                    </tr>
                                                )) : (
                                                    <tr><td colSpan="2" style={{ padding: "24px 12px", textAlign: "center", fontSize: "12px", color: TEXT_DIM, fontStyle: "italic" }}>No buy orders</td></tr>
                                                )}
                                            </tbody>
                                        </table>
                                    </div>
                                );
                            })()}
                        </div>
                    </div>
                </div>
            </div>

            {/* Order History */}
            {(() => {
                const totalOrderPages = Math.max(1, Math.ceil(o.length / ORDER_PS));
                const op = Math.min(orderPage, totalOrderPages - 1);
                const pageOrders = o.slice(op * ORDER_PS, (op + 1) * ORDER_PS);
                const firstItem = o.length === 0 ? 0 : op * ORDER_PS + 1;
                const lastItem  = Math.min((op + 1) * ORDER_PS, o.length);
                return (
            <div style={{ ...card, padding: "24px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "20px" }}>
                    <p style={{ fontSize: "15px", fontWeight: 600, color: TEXT, margin: 0 }}>Order History</p>
                    <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
                        {o.length > ORDER_PS && (
                            <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                                <span style={{ fontSize: "11px", color: TEXT_DIM }}>{firstItem}–{lastItem} of {o.length}</span>
                                <button onClick={() => setOrderPage(p => Math.max(0, p - 1))} disabled={op === 0} style={{ background: "none", border: `1px solid ${BORDER}`, color: op === 0 ? TEXT_DIM : TEXT_SEC, cursor: op === 0 ? "default" : "pointer", fontSize: "12px", padding: "3px 9px", borderRadius: "4px", opacity: op === 0 ? 0.4 : 1 }}>‹</button>
                                <button onClick={() => setOrderPage(p => Math.min(totalOrderPages - 1, p + 1))} disabled={op === totalOrderPages - 1} style={{ background: "none", border: `1px solid ${BORDER}`, color: op === totalOrderPages - 1 ? TEXT_DIM : TEXT_SEC, cursor: op === totalOrderPages - 1 ? "default" : "pointer", fontSize: "12px", padding: "3px 9px", borderRadius: "4px", opacity: op === totalOrderPages - 1 ? 0.4 : 1 }}>›</button>
                            </div>
                        )}
                        <span style={{ fontSize: "12px", fontWeight: 600, padding: "3px 10px", borderRadius: "20px", background: `${INDIGO}18`, color: INDIGO_LT }}>
                            {o.length} orders
                        </span>
                    </div>
                </div>
                <div style={{ overflowX: "auto" }}>
                    <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "13px" }}>
                        <thead>
                            <tr style={{ borderBottom: `1px solid ${BORDER}` }}>
                                {["Order ID", "Type", "Side", "Qty", "Price", "Status", "Action"].map((h, i) => (
                                    <th key={i} style={{
                                        padding: "10px 16px", textAlign: ["Qty", "Price"].includes(h) ? "right" : h === "Action" ? "center" : "left",
                                        fontSize: "11px", fontWeight: 600, color: TEXT_DIM,
                                        textTransform: "uppercase", letterSpacing: "0.06em"
                                    }}>{h}</th>
                                ))}
                            </tr>
                        </thead>
                        <tbody>
                            {o.length > 0 ? (
                                pageOrders.map((order, idx) => (
                                    <tr key={idx}
                                        style={{ borderBottom: `1px solid ${BORDER}30`, transition: "background 0.1s" }}
                                        onMouseEnter={e => e.currentTarget.style.background = `${SURFACE}80`}
                                        onMouseLeave={e => e.currentTarget.style.background = "transparent"}>
                                        <td style={{ padding: "12px 16px", fontFamily: "monospace", fontSize: "11px", color: TEXT_DIM }}>
                                            {order.orderId}
                                        </td>
                                        <td style={{ padding: "12px 16px" }}>
                                            <span style={{
                                                fontSize: "11px", fontWeight: 600, padding: "2px 8px", borderRadius: "4px",
                                                ...(order.marketLimit
                                                    ? { background: `${INDIGO}18`, color: INDIGO_LT }
                                                    : { background: `${TEXT_DIM}18`, color: TEXT_SEC })
                                            }}>
                                                {order.marketLimit ? "MARKET" : "LIMIT"}
                                            </span>
                                        </td>
                                        <td style={{ padding: "12px 16px", fontWeight: 600, color: order.buySell ? GREEN_LT : RED_LT }}>
                                            {order.buySell ? "BUY" : "SELL"}
                                        </td>
                                        <td style={{ padding: "12px 16px", textAlign: "right", color: TEXT, fontVariantNumeric: "tabular-nums" }}>
                                            {order.initialShares ? (
                                                <span>
                                                    <span style={{ color: TEXT }}>{order.initialShares - order.shares}</span>
                                                    <span style={{ color: TEXT_DIM }}> / {order.initialShares}</span>
                                                </span>
                                            ) : (
                                                order.shares > 0 ? order.shares : <span style={{ color: TEXT_DIM, fontStyle: "italic" }}>--</span>
                                            )}
                                        </td>
                                        <td style={{ padding: "12px 16px", textAlign: "right", color: TEXT, fontVariantNumeric: "tabular-nums" }}>
                                            {order.finalPrice ? `₹${order.finalPrice.toLocaleString()}` : order.price ? `₹${order.price.toLocaleString()}` : "-"}
                                        </td>
                                        <td style={{ padding: "12px 16px" }}>
                                            <div style={{ display: "flex", alignItems: "center", gap: "7px" }}>
                                                <div style={{
                                                    width: "7px", height: "7px", borderRadius: "50%",
                                                    background: order.fillStatus === "FILLED" ? GREEN
                                                        : order.fillStatus === "CANCELLED" ? RED
                                                        : order.fillStatus === "PARTIALLY_FILLED" ? INDIGO
                                                        : TEXT_DIM
                                                }}></div>
                                                <span style={{
                                                    fontSize: "12px", fontWeight: 500,
                                                    color: order.fillStatus === "FILLED" ? GREEN_LT
                                                        : order.fillStatus === "CANCELLED" ? RED_LT
                                                        : order.fillStatus === "PARTIALLY_FILLED" ? INDIGO_LT
                                                        : TEXT_SEC
                                                }}>
                                                    {order.fillStatus || "PENDING"}
                                                </span>
                                            </div>
                                        </td>
                                        <td style={{ padding: "12px 16px", textAlign: "center" }}>
                                            {!order.status && !order.companyOrder && !(order.marketLimit && order.shares < order.initialShares) && (
                                                <button
                                                    onClick={() => handleCancelOrder(order.orderId)}
                                                    style={{
                                                        background: "none", border: `1px solid ${RED}40`,
                                                        color: RED_LT, cursor: "pointer", fontSize: "12px",
                                                        fontWeight: 500, padding: "4px 12px", borderRadius: "6px",
                                                        transition: "background 0.15s",
                                                        fontFamily: "'Inter', system-ui, sans-serif"
                                                    }}
                                                    onMouseEnter={e => e.currentTarget.style.background = `${RED}15`}
                                                    onMouseLeave={e => e.currentTarget.style.background = "none"}
                                                >
                                                    Cancel
                                                </button>
                                            )}
                                        </td>
                                    </tr>
                                ))
                            ) : (
                                <tr>
                                    <td colSpan="7" style={{ textAlign: "center", padding: "40px", fontSize: "13px", color: TEXT_DIM, fontStyle: "italic" }}>
                                        No orders yet
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
                );
            })()}
        </div>
    );
}

export default CompanyPage;
