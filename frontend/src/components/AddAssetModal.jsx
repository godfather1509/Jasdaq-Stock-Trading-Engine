import { useState } from "react";
import api from "../../api/apiConfig";

const SURFACE = "#1e293b";
const ELEVATED = "#0d1625";
const BORDER  = "#334155";
const TEXT    = "#f1f5f9";
const TEXT_SEC= "#94a3b8";
const TEXT_DIM= "#64748b";
const INDIGO  = "#6366f1";
const INDIGO_LT="#818cf8";
const RED     = "#ef4444";
const RED_LT  = "#f87171";

function AddAssetModal({ isOpen, onClose, onRefresh }) {
    const [formData, setFormData] = useState({
        symbol: "",
        name: "",
        totalShares: 1000,
        initialPrice: 100
    });
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    if (!isOpen) return null;

    const handleSubmit = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError("");
        try {
            const resp = await api.post("companies", formData);
            if (resp.data.status === "ok") {
                onRefresh();
                onClose();
                setFormData({ symbol: "", name: "", totalShares: 1000, initialPrice: 100 });
            } else {
                setError(resp.data.message || "Failed to create asset");
            }
        } catch (err) {
            setError(err.response?.data?.message || "Connection failed");
        } finally {
            setLoading(false);
        }
    };

    const inputStyle = {
        width: "100%", background: ELEVATED, border: `1px solid ${BORDER}`,
        borderRadius: "8px", padding: "10px 14px", color: TEXT,
        fontSize: "14px", fontWeight: 400, outline: "none", transition: "border-color 0.15s",
        fontFamily: "'Inter', system-ui, sans-serif"
    };

    const labelStyle = {
        display: "block", fontSize: "11px", fontWeight: 600, color: TEXT_DIM,
        textTransform: "uppercase", letterSpacing: "0.07em", marginBottom: "6px"
    };

    return (
        <div style={{
            position: "fixed", inset: 0, zIndex: 100,
            display: "flex", alignItems: "center", justifyContent: "center", padding: "16px"
        }}>
            <div style={{ position: "absolute", inset: 0, background: "rgba(0,0,0,0.6)" }} onClick={onClose}></div>
            <div style={{
                position: "relative", width: "100%", maxWidth: "440px",
                background: SURFACE, border: `1px solid ${BORDER}`,
                borderRadius: "14px", padding: "32px",
                fontFamily: "'Inter', system-ui, sans-serif"
            }}>
                <div style={{ marginBottom: "24px" }}>
                    <h2 style={{ fontSize: "18px", fontWeight: 700, color: TEXT, margin: "0 0 4px 0" }}>
                        List New Company
                    </h2>
                    <p style={{ fontSize: "13px", color: TEXT_DIM, margin: 0 }}>
                        Register a company on the Jasdaq exchange
                    </p>
                </div>

                <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
                    <div>
                        <label style={labelStyle}>Ticker Symbol</label>
                        <input
                            type="text" placeholder="e.g. AAPL" style={inputStyle}
                            value={formData.symbol}
                            onChange={(e) => setFormData({ ...formData, symbol: e.target.value.toUpperCase() })}
                            onFocus={e => e.target.style.borderColor = INDIGO}
                            onBlur={e => e.target.style.borderColor = BORDER}
                            required
                        />
                    </div>
                    <div>
                        <label style={labelStyle}>Company Name</label>
                        <input
                            type="text" placeholder="e.g. Apple Inc." style={inputStyle}
                            value={formData.name}
                            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                            onFocus={e => e.target.style.borderColor = INDIGO}
                            onBlur={e => e.target.style.borderColor = BORDER}
                            required
                        />
                    </div>
                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
                        <div>
                            <label style={labelStyle}>Total Shares</label>
                            <input
                                type="number" style={inputStyle}
                                value={formData.totalShares}
                                onChange={(e) => setFormData({ ...formData, totalShares: parseInt(e.target.value) })}
                                onFocus={e => e.target.style.borderColor = INDIGO}
                                onBlur={e => e.target.style.borderColor = BORDER}
                                min="1" required
                            />
                        </div>
                        <div>
                            <label style={labelStyle}>IPO Price</label>
                            <input
                                type="number" style={inputStyle}
                                value={formData.initialPrice}
                                onChange={(e) => setFormData({ ...formData, initialPrice: parseInt(e.target.value) })}
                                onFocus={e => e.target.style.borderColor = INDIGO}
                                onBlur={e => e.target.style.borderColor = BORDER}
                                min="1" required
                            />
                        </div>
                    </div>

                    {error && (
                        <div style={{
                            padding: "12px 14px", borderRadius: "8px",
                            background: `${RED}12`, border: `1px solid ${RED}30`,
                            color: RED_LT, fontSize: "13px"
                        }}>
                            {error}
                        </div>
                    )}

                    <div style={{ display: "flex", gap: "10px", marginTop: "8px" }}>
                        <button
                            type="button" onClick={onClose}
                            style={{
                                flex: 1, padding: "11px", borderRadius: "8px", border: `1px solid ${BORDER}`,
                                background: "none", color: TEXT_SEC, cursor: "pointer", fontSize: "14px",
                                fontWeight: 500, transition: "background 0.15s",
                                fontFamily: "'Inter', system-ui, sans-serif"
                            }}
                            onMouseEnter={e => e.currentTarget.style.background = `${BORDER}40`}
                            onMouseLeave={e => e.currentTarget.style.background = "none"}
                        >
                            Cancel
                        </button>
                        <button
                            type="submit" disabled={loading}
                            style={{
                                flex: 2, padding: "11px", borderRadius: "8px", border: "none",
                                background: loading ? `${INDIGO}60` : INDIGO, color: "#fff",
                                cursor: loading ? "not-allowed" : "pointer", fontSize: "14px", fontWeight: 600,
                                transition: "opacity 0.15s",
                                fontFamily: "'Inter', system-ui, sans-serif"
                            }}
                            onMouseEnter={e => !loading && (e.currentTarget.style.opacity = "0.85")}
                            onMouseLeave={e => e.currentTarget.style.opacity = "1"}
                        >
                            {loading ? "Listing..." : "List Company"}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

export default AddAssetModal;
