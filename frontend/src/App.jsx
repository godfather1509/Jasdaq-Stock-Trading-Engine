import { RouterProvider, createBrowserRouter } from 'react-router-dom'
import Home from './components/Home'
import Sidebar from './components/Sidebar'
import CompanyPage from './components/CompanyPage'

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
          <div className="flex bg-gray-50 min-h-screen">
            <Sidebar />
            <div className="flex-1 ml-64">
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
