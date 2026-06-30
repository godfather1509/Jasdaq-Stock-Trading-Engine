import { useEffect, useState, useRef } from "react"
import { useParams } from "react-router-dom"
import SockJS from "sockjs-client"
import Stomp from "stompjs"

const BG       = "#0f172a";
const ELEVATED = "#0d1625";
const BORDER   = "#334155";
const TEXT     = "#f1f5f9";
const TEXT_DIM = "#64748b";
const INDIGO   = "#6366f1";
const GREEN    = "#10b981";
const RED      = "#ef4444";
const RED_LT   = "#f87171";

function Sidebar() {
    const { companyId, companySymbol } = useParams();
    const [form, setForm] = useState({ quantity: "", price: "", type: "market", side: "buy" });
    const [toast, setToast] = useState(null);
    const stompClient  = useRef(null);
    const pendingTimer = useRef(null);
    const toastTimer   = useRef(null);

    const showToast = (msg) => {
        if (toastTimer.current) clearTimeout(toastTimer.current);
        setToast(msg);
        toastTimer.current = setTimeout(() => setToast(null), 5000);
    };

    useEffect(() => {
        const socket = new SockJS("http://localhost:8080/ws");
        const client = Stomp.over(socket);
        client.debug = null;
        client.connect({}, () => {
            stompClient.current = client;
            client.subscribe("/topic/order-rejected", (msg) => {
                const r = JSON.parse(msg.body);
                if (r.companyId === companyId) {
                    if (pendingTimer.current) { clearTimeout(pendingTimer.current); pendingTimer.current = null; }
                    showToast(r.reason || "Order rejected by the engine.");
                }
            });
        });
        return () => {
            if (stompClient.current) stompClient.current.disconnect();
            if (toastTimer.current)  clearTimeout(toastTimer.current);
            if (pendingTimer.current) clearTimeout(pendingTimer.current);
        };
    }, [companyId]);

    const handleChange = (e) => {
        const { name, value } = e.target;
        setForm(prev => ({ ...prev, [name]: value }));
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        const qty = parseInt(form.quantity);
        const px  = parseInt(form.price) || 0;
        if (!qty || qty < 1)                          { showToast("Quantity must be at least 1.");    return; }
        if (form.type === "limit" && px < 1)          { showToast("Limit price must be at least 1."); return; }
        if (!stompClient.current?.connected)          { showToast("Not connected — please refresh."); return; }

        const isMkt = form.type === "market";
        stompClient.current.send("/app/placeOrder", {}, JSON.stringify({
            symbol:      companySymbol,
            buySell:     form.side === "buy",
            marketLimit: isMkt,
            shares:      qty,
            price:       isMkt ? 0 : px,
            companyId,
        }));

        if (isMkt) {
            const side     = form.side.toUpperCase();
            const opposite = form.side === "buy" ? "SELL" : "BUY";
            pendingTimer.current = setTimeout(() => {
                pendingTimer.current = null;
                showToast(`${side} market order rejected: no ${opposite} orders available.`);
            }, 4000);
        }

        setForm(prev => ({ ...prev, quantity: "", price: "" }));
    };

    const isBuy = form.side === "buy";

    const input = {
        width: "100%", background: ELEVATED, border: `1px solid ${BORDER}`,
        borderRadius: "8px", padding: "9px 12px", color: TEXT, fontSize: "13px",
        outline: "none", boxSizing: "border-box",
        fontFamily: "'Inter', system-ui, sans-serif",
        transition: "border-color 0.15s",
    };

    const lbl = {
        fontSize: "10px", fontWeight: 600, color: TEXT_DIM,
        textTransform: "uppercase", letterSpacing: "0.07em",
        display: "block", marginBottom: "5px",
    };

    return (
        <aside style={{
            position: "fixed", top: "52px", left: 0, zIndex: 40,
            width: "260px", height: "calc(100vh - 52px)",
            background: BG, borderRight: `1px solid ${BORDER}`,
            display: "flex", flexDirection: "column",
            fontFamily: "'Inter', system-ui, sans-serif",
        }}>
            {/* Form */}
            <div style={{ flex: 1, overflowY: "auto", padding: "20px" }}>
                <p style={{ fontSize: "13px", fontWeight: 600, color: TEXT, margin: "0 0 16px 0" }}>
                    Place Order{companySymbol ? ` — ${companySymbol}` : ""}
                </p>

                {toast && (
                    <div style={{
                        background: `${RED}15`, border: `1px solid ${RED}40`,
                        borderLeft: `3px solid ${RED}`, borderRadius: "8px",
                        padding: "10px 12px", marginBottom: "14px",
                        fontSize: "12px", color: RED_LT, lineHeight: 1.5,
                    }}>
                        {toast}
                    </div>
                )}

                <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "12px" }}>

                    {/* Buy / Sell */}
                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", borderRadius: "8px", overflow: "hidden", border: `1px solid ${BORDER}` }}>
                        {["buy", "sell"].map(side => (
                            <button key={side} type="button"
                                onClick={() => setForm(prev => ({ ...prev, side }))}
                                style={{
                                    padding: "9px", border: "none", cursor: "pointer",
                                    fontWeight: 600, fontSize: "13px",
                                    fontFamily: "'Inter', system-ui, sans-serif",
                                    background: form.side === side ? (side === "buy" ? GREEN : RED) : ELEVATED,
                                    color: form.side === side ? "#fff" : TEXT_DIM,
                                    transition: "background 0.15s",
                                }}>
                                {side.toUpperCase()}
                            </button>
                        ))}
                    </div>

                    {/* Order type */}
                    <div>
                        <label style={lbl}>Order Type</label>
                        <select name="type" value={form.type} onChange={handleChange}
                            style={{ ...input, cursor: "pointer" }}>
                            <option value="market">Market</option>
                            <option value="limit">Limit</option>
                        </select>
                    </div>

                    {/* Quantity */}
                    <div>
                        <label style={lbl}>Quantity</label>
                        <input type="number" name="quantity" value={form.quantity}
                            onChange={handleChange} placeholder="0" min="1" step="1"
                            style={input}
                            onFocus={e => e.target.style.borderColor = INDIGO}
                            onBlur={e  => e.target.style.borderColor = BORDER}
                        />
                    </div>

                    {/* Price (limit only) */}
                    {form.type === "limit" && (
                        <div>
                            <label style={lbl}>Limit Price</label>
                            <input type="number" name="price" value={form.price}
                                onChange={handleChange} placeholder="0" min="1" step="1"
                                style={input}
                                onFocus={e => e.target.style.borderColor = INDIGO}
                                onBlur={e  => e.target.style.borderColor = BORDER}
                            />
                        </div>
                    )}

                    <button type="submit" style={{
                        background: isBuy ? GREEN : RED,
                        color: "#fff", border: "none", borderRadius: "8px",
                        padding: "11px", fontWeight: 600, fontSize: "13px",
                        cursor: "pointer", transition: "opacity 0.15s",
                        fontFamily: "'Inter', system-ui, sans-serif",
                    }}
                        onMouseEnter={e => e.currentTarget.style.opacity = "0.85"}
                        onMouseLeave={e => e.currentTarget.style.opacity = "1"}
                    >
                        {isBuy ? "Buy" : "Sell"} {companySymbol || ""}
                    </button>

                    <p style={{ fontSize: "11px", color: TEXT_DIM, margin: 0, lineHeight: 1.5 }}>
                        {form.type === "market"
                            ? "Executes immediately at the best available price."
                            : "Stays in the order book until matched at your price or better."}
                    </p>
                </form>
            </div>
        </aside>
    );
}

export default Sidebar
