import { RouterProvider, createBrowserRouter } from 'react-router-dom'
import Home from './components/Home'
import Sidebar from './components/Sidebar'
import CompanyPage from './components/CompanyPage'

const topBar = {
  position: "fixed", top: 0, left: 0, right: 0, zIndex: 50,
  height: "52px", background: "#0f172a", borderBottom: "1px solid #334155",
  display: "flex", alignItems: "center", padding: "0 28px",
};

function App() {

  const router = createBrowserRouter(
    [
      {
        path: "/",
        element: <Home />
      },
      {
        path: "/company/:companySymbol/:companyId",
        element: (
          <div style={{ background: "#0f172a", minHeight: "100vh" }}>
            <div style={topBar}>
              <a href="/" style={{
                fontSize: "18px", fontWeight: 700, color: "#f1f5f9",
                textDecoration: "none", letterSpacing: "-0.3px",
                fontFamily: "'Inter', system-ui, sans-serif",
              }}>Jasdaq</a>
            </div>
            <Sidebar />
            <div style={{ marginLeft: "260px", paddingTop: "52px" }}>
              <CompanyPage />
            </div>
          </div>
        )
      }
    ]
  )

  return <RouterProvider router={router} />
}

export default App
