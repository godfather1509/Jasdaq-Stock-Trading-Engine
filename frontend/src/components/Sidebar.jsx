import { useEffect, useState } from "react"
import { NavLink } from "react-router-dom"
import api from "../../api/apiConfig"

const BG      = "#0f172a";
const SURFACE = "#1e293b";
const BORDER  = "#334155";
const TEXT    = "#f1f5f9";
const TEXT_SEC= "#94a3b8";
const TEXT_DIM= "#64748b";
const INDIGO  = "#6366f1";
const GREEN   = "#10b981";

function Sidebar() {
    const [companies, setCompanies] = useState([])

    useEffect(() => {
        const fetchData = async () => {
            try {
                const res = await api.get("allCompanies");
                setCompanies(res.data)
            } catch (error) {
                console.error(error);
            }
        };
        fetchData();
    }, []);

    return (
        <aside style={{
            position: "fixed", top: 0, left: 0, zIndex: 40,
            width: "260px", height: "100vh",
            background: BG, borderRight: `1px solid ${BORDER}`,
            display: "flex", flexDirection: "column",
            fontFamily: "'Inter', system-ui, sans-serif"
        }}>
            {/* Logo */}
            <div style={{ padding: "24px 20px", borderBottom: `1px solid ${BORDER}` }}>
                <NavLink to="/" style={{ textDecoration: "none" }}>
                    <p style={{ fontSize: "18px", fontWeight: 700, color: TEXT, margin: "0 0 4px 0", letterSpacing: "-0.3px" }}>
                        Jasdaq
                    </p>
                </NavLink>
                <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                    <div style={{ width: "6px", height: "6px", borderRadius: "50%", background: GREEN }}></div>
                    <span style={{ fontSize: "11px", color: TEXT_DIM, fontWeight: 500 }}>System Live</span>
                </div>
            </div>

            {/* Nav list */}
            <div style={{ flex: 1, overflowY: "auto", padding: "16px 12px" }}>
                <p style={{ fontSize: "10px", fontWeight: 600, color: TEXT_DIM, textTransform: "uppercase", letterSpacing: "0.1em", padding: "0 8px", marginBottom: "8px" }}>
                    Market Assets
                </p>
                <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column", gap: "2px" }}>
                    {companies.map((company, index) => (
                        <li key={index}>
                            <NavLink
                                to={`/company/${company.symbol}/${company.companyId}`}
                                style={({ isActive }) => ({
                                    display: "flex", alignItems: "center", gap: "12px",
                                    padding: "10px 12px", borderRadius: "8px",
                                    textDecoration: "none", transition: "background 0.15s",
                                    background: isActive ? `${INDIGO}18` : "transparent",
                                    borderLeft: isActive ? `3px solid ${INDIGO}` : "3px solid transparent",
                                    color: isActive ? TEXT : TEXT_SEC,
                                })}
                                onMouseEnter={e => { if (!e.currentTarget.style.borderLeftColor.includes("99")) e.currentTarget.style.background = `${SURFACE}`; }}
                                onMouseLeave={e => { e.currentTarget.style.background = e.currentTarget.getAttribute("data-active") === "true" ? `${INDIGO}18` : "transparent"; }}
                            >
                                <div style={{
                                    width: "32px", height: "32px", borderRadius: "8px",
                                    background: `${INDIGO}15`, border: `1px solid ${INDIGO}30`,
                                    display: "flex", alignItems: "center", justifyContent: "center",
                                    fontSize: "12px", fontWeight: 700, color: INDIGO, flexShrink: 0
                                }}>
                                    {company.symbol[0]}
                                </div>
                                <div style={{ minWidth: 0 }}>
                                    <p style={{ fontSize: "13px", fontWeight: 600, color: "inherit", margin: 0 }}>{company.symbol}</p>
                                    <p style={{ fontSize: "11px", color: TEXT_DIM, margin: "1px 0 0 0", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                                        {company.name}
                                    </p>
                                </div>
                            </NavLink>
                        </li>
                    ))}
                </ul>
            </div>

            {/* Footer */}
            <div style={{ padding: "16px 20px", borderTop: `1px solid ${BORDER}` }}>
                <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                    <div style={{
                        width: "32px", height: "32px", borderRadius: "8px",
                        background: `${INDIGO}20`, border: `1px solid ${INDIGO}40`,
                        display: "flex", alignItems: "center", justifyContent: "center",
                        fontSize: "11px", fontWeight: 700, color: INDIGO
                    }}>
                        JD
                    </div>
                    <div>
                        <p style={{ fontSize: "12px", fontWeight: 600, color: TEXT, margin: 0 }}>Dev Console</p>
                        <p style={{ fontSize: "11px", color: TEXT_DIM, margin: 0 }}>Operator-01</p>
                    </div>
                </div>
            </div>
        </aside>
    )
}

export default Sidebar
