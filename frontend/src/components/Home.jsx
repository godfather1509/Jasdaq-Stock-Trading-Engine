import { useEffect, useState, useRef } from "react"
import { useNavigate } from "react-router-dom"
import api from "../../api/apiConfig"
import SockJS from "sockjs-client";
import Stomp from "stompjs";

const BG      = "#0f172a";
const SURFACE = "#1e293b";
const BORDER  = "#334155";
const TEXT    = "#f1f5f9";
const TEXT_SEC= "#94a3b8";
const TEXT_DIM= "#64748b";
const INDIGO  = "#6366f1";
const GREEN   = "#10b981";
const RED     = "#ef4444";

function Home() {
    const [companies, setCompanies] = useState([])
    const [loading, setLoading] = useState(true)
    const [marketStats, setMarketStats] = useState({
        marketStatus: "Operational",
        totalVolume: 0,
        avgLatency: 0
    })
    const navigate = useNavigate()
    const stompClient = useRef(null);

    const formatVolume = (val) => {
        if (!val) return "$0";
        if (val >= 1e12) return `$${(val / 1e12).toFixed(2)}T`;
        if (val >= 1e9)  return `$${(val / 1e9).toFixed(2)}B`;
        if (val >= 1e6)  return `$${(val / 1e6).toFixed(2)}M`;
        if (val >= 1e3)  return `$${(val / 1e3).toFixed(1)}K`;
        return `$${val.toLocaleString()}`;
    };

    useEffect(() => {
        const fetchData = async () => {
            try {
                const res = await api.get("allCompanies");
                const validCompanies = res.data.filter(c => c.symbol && c.name);
                setCompanies(validCompanies);
            } catch (error) {
                console.error(error);
            } finally {
                setLoading(false);
            }
        };

        const fetchStats = async () => {
            try {
                const statsRes = await api.get("market-stats");
                setMarketStats(statsRes.data);
            } catch (error) {
                console.error("Failed to fetch market stats", error);
            }
        };

        fetchData();
        fetchStats();
        const statsInterval = setInterval(fetchStats, 5000);

        const socket = new SockJS("http://localhost:8080/ws");
        const client = Stomp.over(socket);
        client.debug = null;
        client.connect({}, () => {
            stompClient.current = client;
            client.subscribe("/topic/market-updates", (msg) => {
                const update = JSON.parse(msg.body);
                setCompanies(prev => prev.map(c =>
                    c.companyId === update.companyId ? { ...c, currentPrice: update.price } : c
                ));
            });
            client.subscribe("/topic/market-stats", (msg) => {
                const stats = JSON.parse(msg.body);
                setMarketStats(stats);
            });
        });

        return () => {
            if (stompClient.current) stompClient.current.disconnect();
            clearInterval(statsInterval);
        };
    }, []);

    return (
        <div style={{ background: BG, color: TEXT, minHeight: "100vh", fontFamily: "'Inter', system-ui, sans-serif" }}>

            {/* Top bar */}
            <div style={{ borderBottom: `1px solid ${BORDER}`, padding: "20px 48px", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                <div>
                    <h1 style={{ fontSize: "22px", fontWeight: 700, color: TEXT, margin: 0, letterSpacing: "-0.3px" }}>
                        Jasdaq
                    </h1>
                    <p style={{ fontSize: "12px", color: TEXT_DIM, margin: "2px 0 0 0" }}>Institutional Trading Engine</p>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                    <div style={{ width: "7px", height: "7px", borderRadius: "50%", background: GREEN }}></div>
                    <span style={{ fontSize: "12px", color: TEXT_SEC, fontWeight: 500 }}>Market Open</span>
                </div>
            </div>

            <div style={{ padding: "40px 48px", maxWidth: "1400px", margin: "0 auto" }}>

                {/* Stats row */}
                <div style={{ marginBottom: "48px" }}>
                    <div style={{
                        display: "inline-flex", alignItems: "center", gap: "8px",
                        background: SURFACE, border: `1px solid ${BORDER}`,
                        borderRadius: "12px", padding: "16px 24px"
                    }}>
                        <p style={{ fontSize: "11px", fontWeight: 600, color: TEXT_DIM, textTransform: "uppercase", letterSpacing: "0.08em", margin: 0 }}>
                            Avg Latency
                        </p>
                        <p style={{ fontSize: "18px", fontWeight: 700, color: TEXT_SEC, margin: 0 }}>
                            {marketStats.avgLatency?.toFixed(2)}ms
                        </p>
                    </div>
                </div>

                {/* Section heading */}
                <div style={{ marginBottom: "24px", display: "flex", alignItems: "baseline", justifyContent: "space-between" }}>
                    <h2 style={{ fontSize: "18px", fontWeight: 600, color: TEXT, margin: 0 }}>Listed Companies</h2>
                    {!loading && <span style={{ fontSize: "13px", color: TEXT_DIM }}>{companies.length} listed</span>}
                </div>

                {/* Company grid */}
                {loading ? (
                    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: "80px 0", gap: "16px" }}>
                        <div style={{
                            width: "36px", height: "36px", borderRadius: "50%",
                            border: `3px solid ${BORDER}`, borderTopColor: INDIGO,
                            animation: "spin 0.8s linear infinite"
                        }}></div>
                        <p style={{ fontSize: "13px", color: TEXT_DIM, fontWeight: 500 }}>Loading market data...</p>
                    </div>
                ) : companies.length > 0 ? (
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))", gap: "16px" }}>
                        {companies.map((company, index) => (
                            <div
                                key={index}
                                onClick={() => navigate(`/company/${company.symbol}/${company.companyId}`)}
                                style={{
                                    background: SURFACE, border: `1px solid ${BORDER}`,
                                    borderRadius: "12px", padding: "24px",
                                    cursor: "pointer", transition: "border-color 0.15s, transform 0.15s",
                                }}
                                onMouseEnter={e => {
                                    e.currentTarget.style.borderColor = INDIGO;
                                    e.currentTarget.style.transform = "translateY(-2px)";
                                }}
                                onMouseLeave={e => {
                                    e.currentTarget.style.borderColor = BORDER;
                                    e.currentTarget.style.transform = "translateY(0)";
                                }}
                            >
                                <div style={{ display: "flex", alignItems: "center", gap: "14px", marginBottom: "20px" }}>
                                    <div style={{
                                        width: "42px", height: "42px", borderRadius: "10px",
                                        background: `${INDIGO}20`, border: `1px solid ${INDIGO}40`,
                                        display: "flex", alignItems: "center", justifyContent: "center",
                                        fontSize: "16px", fontWeight: 700, color: INDIGO
                                    }}>
                                        {company.symbol[0]}
                                    </div>
                                    <div>
                                        <p style={{ fontSize: "15px", fontWeight: 700, color: TEXT, margin: 0 }}>{company.symbol}</p>
                                        <p style={{ fontSize: "12px", color: TEXT_DIM, margin: "2px 0 0 0", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", maxWidth: "160px" }}>
                                            {company.name}
                                        </p>
                                    </div>
                                </div>

                                <div style={{ borderTop: `1px solid ${BORDER}`, paddingTop: "16px" }}>
                                    <p style={{ fontSize: "11px", color: TEXT_DIM, fontWeight: 500, margin: "0 0 4px 0", textTransform: "uppercase", letterSpacing: "0.06em" }}>
                                        Current Price
                                    </p>
                                    <p style={{ fontSize: "24px", fontWeight: 700, color: GREEN, margin: 0, fontVariantNumeric: "tabular-nums" }}>
                                        ${company.currentPrice?.toLocaleString()}
                                    </p>
                                </div>
                            </div>
                        ))}
                    </div>
                ) : (
                    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: "80px 0", gap: "12px" }}>
                        <div style={{ width: "48px", height: "48px", borderRadius: "12px", background: SURFACE, border: `1px solid ${BORDER}`, display: "flex", alignItems: "center", justifyContent: "center", fontSize: "22px" }}>
                            📋
                        </div>
                        <p style={{ fontSize: "14px", color: TEXT_SEC, fontWeight: 500, margin: 0 }}>No companies listed yet</p>
                        <p style={{ fontSize: "12px", color: TEXT_DIM, margin: 0 }}>Add a company from the admin panel to get started.</p>
                    </div>
                )}
            </div>
        </div>
    )
}

export default Home
