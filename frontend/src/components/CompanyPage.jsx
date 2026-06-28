import { useState, useEffect, useRef } from "react";
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
    const [metrics, setMetrics] = useState({
        totalBuyShares: 0, totalSellShares: 0,
        buyLimitOrders: 0, sellLimitOrders: 0,
        buyMarketOrders: 0, sellMarketOrders: 0,
        currentPrice: 0
    });
    const [form, setForm] = useState({ quantity: "", price: "", type: "market", side: "buy" });

    const stompClient = useRef(null);
    const toastCounterRef = useRef(0);
    const pendingOrderRef = useRef(null);

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
                    const time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
                    setChartData(prev => [...prev, { time, price: update.price }].slice(-20));
                }
            });

            client.subscribe("/topic/orders", (msg) => {
                const updatedOrder = JSON.parse(msg.body);
                if (updatedOrder.symbol !== companySymbol) return;
                if (pendingOrderRef.current) {
                    clearTimeout(pendingOrderRef.current);
                    pendingOrderRef.current = null;
                }
                setO(prev => {
                    const exists = prev.find(item => item.orderId === updatedOrder.orderId);
                    if (exists) return prev.map(item => item.orderId === updatedOrder.orderId ? updatedOrder : item);
                    return [updatedOrder, ...prev];
                });
            });

            client.subscribe("/topic/cancel", (msg) => {
                const canceledOrder = JSON.parse(msg.body);
                setO(prev => prev.filter(item => item.orderId !== canceledOrder.orderId));
            });

            client.subscribe("/topic/order-rejected", (msg) => {
                const rejection = JSON.parse(msg.body);
                showToast(rejection.reason || "Order was rejected by the matching engine.", "error");
            });
        });

        return () => { if (stompClient.current) stompClient.current.disconnect(); };
    }, [companyId]);

    const handleChange = (e) => {
        const { name, value } = e.target;
        setForm(prev => ({ ...prev, [name]: value }));
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (stompClient.current && stompClient.current.connected) {
            const isMkt = form.type === "market";
            const orderPayload = {
                symbol: companySymbol,
                buySell: form.side === "buy",
                marketLimit: isMkt,
                shares: parseInt(form.quantity),
                price: isMkt ? 0 : parseInt(form.price),
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
                if (res.data) {
                    setC(res.data);
                    setO(res.data.orders || []);
                    if (chartData.length === 0 && res.data.currentPrice) {
                        const time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
                        setChartData([{ time, price: res.data.currentPrice }]);
                    }
                }
            } catch (err) { console.error(err); }
        };

        const fetchMetrics = async () => {
            try {
                const res = await api.get(`${companyId}/metrics`);
                setMetrics(res.data);
            } catch (err) { console.warn("Failed to fetch metrics", err); }
        };

        const fetchTrades = async () => {
            try {
                const res = await api.get(`${companyId}/trades`);
                const trades = res.data?.content ?? res.data;
                if (trades && trades.length > 0) {
                    const mapped = trades.map(t => ({
                        time: new Date(t.tradeTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
                        price: t.price
                    }));
                    setChartData(mapped.slice(-20));
                }
            } catch (err) { console.warn("Failed to fetch historical trades", err); }
        };

        fetchCompany();
        fetchMetrics();
        fetchTrades();
        const interval = setInterval(fetchMetrics, 3000);
        return () => clearInterval(interval);
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

            {/* Toast notifications */}
            <div style={{
                position: "fixed", top: "20px", right: "20px", zIndex: 9999,
                display: "flex", flexDirection: "column", gap: "10px", maxWidth: "400px", width: "100%"
            }}>
                {toasts.map(toast => (
                    <div key={toast.id} style={{
                        background: SURFACE, border: `1px solid ${RED}40`,
                        borderLeft: `4px solid ${RED}`,
                        borderRadius: "10px", padding: "14px 16px",
                        display: "flex", alignItems: "flex-start", gap: "12px",
                        boxShadow: "0 4px 20px rgba(0,0,0,0.4)",
                        animation: "slideInRight 0.2s ease-out",
                    }}>
                        <div style={{ flex: 1, minWidth: 0 }}>
                            <p style={{ color: RED_LT, fontWeight: 600, fontSize: "12px", margin: "0 0 4px 0" }}>
                                Order Rejected
                            </p>
                            <p style={{ color: TEXT_SEC, fontSize: "13px", margin: 0, lineHeight: 1.5 }}>
                                {toast.reason}
                            </p>
                        </div>
                        <button onClick={() => dismissToast(toast.id)} style={{
                            background: "none", border: "none", cursor: "pointer",
                            color: TEXT_DIM, fontSize: "16px", lineHeight: 1, padding: "2px", flexShrink: 0
                        }}>✕</button>
                    </div>
                ))}
            </div>

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
                    <p style={{ fontSize: "11px", color: TEXT_DIM, margin: "0 0 4px 0", fontWeight: 500, textTransform: "uppercase", letterSpacing: "0.06em" }}>Current Price</p>
                    <p style={{ fontSize: "28px", fontWeight: 700, color: GREEN, margin: 0, fontVariantNumeric: "tabular-nums" }}>
                        ${c.currentPrice?.toLocaleString()}
                    </p>
                </div>
            </div>

            {/* Top row: metrics + form + chart */}
            <div style={{ display: "grid", gridTemplateColumns: "340px 1fr", gap: "20px", marginBottom: "20px" }}>

                {/* Left: metrics + order form */}
                <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>

                    {/* Bid / Ask metrics */}
                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
                        <div style={{ ...card, padding: "16px" }}>
                            <p style={{ fontSize: "11px", fontWeight: 600, color: GREEN, textTransform: "uppercase", letterSpacing: "0.07em", margin: "0 0 8px 0" }}>
                                Bid Volume
                            </p>
                            <p style={{ fontSize: "20px", fontWeight: 700, color: TEXT, margin: "0 0 8px 0", fontVariantNumeric: "tabular-nums" }}>
                                {metrics.totalBuyShares?.toLocaleString()}
                            </p>
                            <div style={{ display: "flex", gap: "6px" }}>
                                <span style={{ fontSize: "10px", fontWeight: 600, padding: "2px 8px", borderRadius: "4px", background: `${GREEN}18`, color: GREEN_LT }}>
                                    {metrics.buyLimitOrders} LMT
                                </span>
                                <span style={{ fontSize: "10px", fontWeight: 600, padding: "2px 8px", borderRadius: "4px", background: `${INDIGO}18`, color: INDIGO_LT }}>
                                    {metrics.buyMarketOrders} MKT
                                </span>
                            </div>
                        </div>
                        <div style={{ ...card, padding: "16px" }}>
                            <p style={{ fontSize: "11px", fontWeight: 600, color: RED, textTransform: "uppercase", letterSpacing: "0.07em", margin: "0 0 8px 0" }}>
                                Ask Volume
                            </p>
                            <p style={{ fontSize: "20px", fontWeight: 700, color: TEXT, margin: "0 0 8px 0", fontVariantNumeric: "tabular-nums" }}>
                                {metrics.totalSellShares?.toLocaleString()}
                            </p>
                            <div style={{ display: "flex", gap: "6px" }}>
                                <span style={{ fontSize: "10px", fontWeight: 600, padding: "2px 8px", borderRadius: "4px", background: `${RED}18`, color: RED_LT }}>
                                    {metrics.sellLimitOrders} LMT
                                </span>
                                <span style={{ fontSize: "10px", fontWeight: 600, padding: "2px 8px", borderRadius: "4px", background: `${INDIGO}18`, color: INDIGO_LT }}>
                                    {metrics.sellMarketOrders} MKT
                                </span>
                            </div>
                        </div>
                    </div>

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
                            </div>

                            <div>
                                <label style={label}>Quantity (shares)</label>
                                <input
                                    type="number" name="quantity" value={form.quantity}
                                    onChange={handleChange} placeholder="0"
                                    style={inputStyle}
                                    onFocus={e => e.target.style.borderColor = INDIGO}
                                    onBlur={e => e.target.style.borderColor = BORDER}
                                    required
                                />
                            </div>

                            {form.type === "limit" && (
                                <div>
                                    <label style={label}>Limit Price</label>
                                    <input
                                        type="number" name="price" value={form.price}
                                        onChange={handleChange} placeholder="0"
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

                {/* Right: chart */}
                <div style={{ ...card, padding: "20px 24px" }}>
                    <p style={{ fontSize: "13px", fontWeight: 600, color: TEXT_SEC, margin: "0 0 16px 0" }}>Price Chart</p>
                    <ResponsiveContainer width="100%" height={280}>
                        <LineChart data={chartData}>
                            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={`${BORDER}80`} />
                            <XAxis dataKey="time" axisLine={false} tickLine={false}
                                tick={{ fill: TEXT_DIM, fontSize: 11, fontFamily: "'Inter', sans-serif" }} />
                            <YAxis domain={["auto", "auto"]} axisLine={false} tickLine={false}
                                tick={{ fill: TEXT_DIM, fontSize: 11, fontFamily: "'Inter', sans-serif" }} />
                            <Tooltip
                                contentStyle={{
                                    background: SURFACE, border: `1px solid ${BORDER}`,
                                    borderRadius: "8px", color: TEXT, fontSize: "13px",
                                    fontFamily: "'Inter', sans-serif"
                                }}
                                labelStyle={{ color: TEXT_SEC }}
                            />
                            <Line
                                type="monotone" dataKey="price" stroke={INDIGO}
                                strokeWidth={2} dot={false}
                                activeDot={{ r: 4, strokeWidth: 0, fill: INDIGO_LT }}
                                animationDuration={300}
                            />
                        </LineChart>
                    </ResponsiveContainer>
                </div>
            </div>

            {/* Order Book */}
            <div style={{ ...card, padding: "24px", marginBottom: "20px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "20px" }}>
                    <p style={{ fontSize: "15px", fontWeight: 600, color: TEXT, margin: 0 }}>Order Book</p>
                    <span style={{ fontSize: "12px", color: TEXT_DIM }}>Click a row to pre-fill the order form</span>
                </div>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "24px" }}>

                    {/* Asks */}
                    <div>
                        <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "12px" }}>
                            <div style={{ width: "8px", height: "8px", borderRadius: "2px", background: RED }}></div>
                            <span style={{ fontSize: "12px", fontWeight: 600, color: RED_LT }}>Asks (Sell Orders)</span>
                        </div>
                        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "13px" }}>
                            <thead>
                                <tr style={{ borderBottom: `1px solid ${BORDER}` }}>
                                    {["Price", "Shares", ""].map((h, i) => (
                                        <th key={i} style={{ padding: "8px 12px", textAlign: i === 1 ? "right" : i === 2 ? "center" : "left", fontSize: "11px", fontWeight: 600, color: TEXT_DIM, textTransform: "uppercase", letterSpacing: "0.06em" }}>{h}</th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {o.filter(ord => !ord.buySell && !ord.status && ord.shares > 0)
                                    .sort((a, b) => a.price - b.price)
                                    .map((ord, idx) => (
                                        <tr key={idx} style={{ borderBottom: `1px solid ${BORDER}30`, cursor: "pointer", transition: "background 0.1s" }}
                                            onMouseEnter={e => e.currentTarget.style.background = `${RED}08`}
                                            onMouseLeave={e => e.currentTarget.style.background = "transparent"}
                                            onClick={() => setForm(prev => ({ ...prev, side: "buy", type: "limit", price: String(ord.price), quantity: String(ord.shares) }))}>
                                            <td style={{ padding: "10px 12px", color: RED_LT, fontWeight: 600, fontVariantNumeric: "tabular-nums" }}>
                                                ${ord.price.toLocaleString()}
                                            </td>
                                            <td style={{ padding: "10px 12px", textAlign: "right", color: TEXT_SEC, fontVariantNumeric: "tabular-nums" }}>
                                                {ord.shares.toLocaleString()}
                                            </td>
                                            <td style={{ padding: "10px 12px", textAlign: "center" }}>
                                                <span className="buy-hint" style={{ fontSize: "10px", fontWeight: 600, padding: "2px 8px", borderRadius: "4px", background: `${GREEN}18`, color: GREEN }}>BUY</span>
                                            </td>
                                        </tr>
                                    ))}
                                {o.filter(ord => !ord.buySell && !ord.status && ord.shares > 0).length === 0 && (
                                    <tr><td colSpan="3" style={{ padding: "24px 12px", textAlign: "center", fontSize: "12px", color: TEXT_DIM, fontStyle: "italic" }}>No sell orders</td></tr>
                                )}
                            </tbody>
                        </table>
                    </div>

                    {/* Bids */}
                    <div>
                        <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "12px" }}>
                            <div style={{ width: "8px", height: "8px", borderRadius: "2px", background: GREEN }}></div>
                            <span style={{ fontSize: "12px", fontWeight: 600, color: GREEN_LT }}>Bids (Buy Orders)</span>
                        </div>
                        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "13px" }}>
                            <thead>
                                <tr style={{ borderBottom: `1px solid ${BORDER}` }}>
                                    {["Price", "Shares", ""].map((h, i) => (
                                        <th key={i} style={{ padding: "8px 12px", textAlign: i === 1 ? "right" : i === 2 ? "center" : "left", fontSize: "11px", fontWeight: 600, color: TEXT_DIM, textTransform: "uppercase", letterSpacing: "0.06em" }}>{h}</th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {o.filter(ord => ord.buySell && !ord.status && ord.shares > 0)
                                    .sort((a, b) => b.price - a.price)
                                    .map((ord, idx) => (
                                        <tr key={idx} style={{ borderBottom: `1px solid ${BORDER}30`, cursor: "pointer", transition: "background 0.1s" }}
                                            onMouseEnter={e => e.currentTarget.style.background = `${GREEN}08`}
                                            onMouseLeave={e => e.currentTarget.style.background = "transparent"}
                                            onClick={() => setForm(prev => ({ ...prev, side: "sell", type: "limit", price: String(ord.price), quantity: String(ord.shares) }))}>
                                            <td style={{ padding: "10px 12px", color: GREEN_LT, fontWeight: 600, fontVariantNumeric: "tabular-nums" }}>
                                                ${ord.price.toLocaleString()}
                                            </td>
                                            <td style={{ padding: "10px 12px", textAlign: "right", color: TEXT_SEC, fontVariantNumeric: "tabular-nums" }}>
                                                {ord.shares.toLocaleString()}
                                            </td>
                                            <td style={{ padding: "10px 12px", textAlign: "center" }}>
                                                <span style={{ fontSize: "10px", fontWeight: 600, padding: "2px 8px", borderRadius: "4px", background: `${RED}18`, color: RED }}>SELL</span>
                                            </td>
                                        </tr>
                                    ))}
                                {o.filter(ord => ord.buySell && !ord.status && ord.shares > 0).length === 0 && (
                                    <tr><td colSpan="3" style={{ padding: "24px 12px", textAlign: "center", fontSize: "12px", color: TEXT_DIM, fontStyle: "italic" }}>No buy orders</td></tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>

            {/* Order History */}
            <div style={{ ...card, padding: "24px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "20px" }}>
                    <p style={{ fontSize: "15px", fontWeight: 600, color: TEXT, margin: 0 }}>Order History</p>
                    <span style={{ fontSize: "12px", fontWeight: 600, padding: "3px 10px", borderRadius: "20px", background: `${INDIGO}18`, color: INDIGO_LT }}>
                        {o.length} orders
                    </span>
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
                                o.map((order, idx) => (
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
                                            {order.finalPrice ? `$${order.finalPrice.toLocaleString()}` : order.price ? `$${order.price.toLocaleString()}` : "-"}
                                        </td>
                                        <td style={{ padding: "12px 16px" }}>
                                            <div style={{ display: "flex", alignItems: "center", gap: "7px" }}>
                                                <div style={{
                                                    width: "7px", height: "7px", borderRadius: "50%",
                                                    background: order.status ? GREEN : order.fillStatus === "PARTIALLY_FILLED" ? INDIGO : TEXT_DIM
                                                }}></div>
                                                <span style={{
                                                    fontSize: "12px", fontWeight: 500,
                                                    color: order.status ? GREEN_LT : order.fillStatus === "PARTIALLY_FILLED" ? INDIGO_LT : TEXT_SEC
                                                }}>
                                                    {order.fillStatus || (order.status ? "Filled" : "Pending")}
                                                </span>
                                            </div>
                                        </td>
                                        <td style={{ padding: "12px 16px", textAlign: "center" }}>
                                            {!order.status && (
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
        </div>
    );
}

export default CompanyPage;
