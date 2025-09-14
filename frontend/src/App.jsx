import { RouterProvider, createBrowserRouter } from 'react-router-dom'
import Home from './components/Home'
import Sidebar from './components/Sidebar'
import CompanyPage from './components/CompanyPage'

function App() {

  const rounter = createBrowserRouter(
    [
      {
        path: "/",
        element:
          <>
            <Sidebar />
            <Home />
          </>
      },
      {
        path: "/company/:companySymbol/:companyId",
        element:
          <div className="flex">
            {/* Sidebar (fixed width) */}
            <div className="w-64">
              <Sidebar />
            </div>

            {/* Main content expands to fill remaining space */}
            <div className="flex-1">
              <CompanyPage />
            </div>
          </div>
      }
    ]
  )

  return (
    <>
      <RouterProvider router={rounter} />
    </>
  )
}

export default App
