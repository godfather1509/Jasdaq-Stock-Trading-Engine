import { useState } from 'react'
import { RouterProvider, createBrowserRouter } from 'react-router-dom'
import Home from './components/Home'
import Sidebar from './components/Sidebar'
import CompanyPage from './components/CompanyPage'

const topBar = {
  position: "fixed", top: 0, left: 0, right: 0, zIndex: 50,
  height: "52px", background: "#0f172a", borderBottom: "1px solid #334155",
  display: "flex", alignItems: "center", padding: "0 28px",
};

const staleBadge = {
  marginLeft: "auto", display: "inline-flex", alignItems: "center", gap: "6px",
  fontSize: "11px", fontWeight: 600, color: "#f59e0b",
  background: "#f59e0b18", border: "1px solid #f59e0b40", borderRadius: "7px",
  padding: "4px 10px", textTransform: "uppercase", letterSpacing: "0.05em",
  fontFamily: "'Inter', system-ui, sans-serif",
};

// Company route layout — owns the "metrics feed stale" flag so the reconnecting
// indicator can live in the top-right of the global header rather than buried in a card.
function CompanyLayout() {
  const [stale, setStale] = useState(false);
  return (
    <div style={{ background: "#0f172a", minHeight: "100vh" }}>
      <div style={topBar}>
        <a href="/" style={{
          fontSize: "18px", fontWeight: 700, color: "#f1f5f9",
          textDecoration: "none", letterSpacing: "-0.3px",
          fontFamily: "'Inter', system-ui, sans-serif",
        }}>Jasdaq</a>
        {stale && (
          <span style={staleBadge}>
            <span style={{ width: "7px", height: "7px", borderRadius: "50%", background: "#f59e0b" }} />
            Reconnecting…
          </span>
        )}
      </div>
      <Sidebar />
      <div style={{ marginLeft: "260px", paddingTop: "52px" }}>
        <CompanyPage onConnectionChange={setStale} />
      </div>
    </div>
  );
}

function App() {
  const router = createBrowserRouter(
    [
      {
        path: "/",
        element: <Home />
      },
      {
        path: "/company/:companySymbol/:companyId",
        element: <CompanyLayout />
      }
    ]
  )

  return <RouterProvider router={router} />
}

export default App
