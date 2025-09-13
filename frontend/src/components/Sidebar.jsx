import { useEffect, useState } from "react"
import { NavLink } from "react-router-dom"
import api from "../../api/apiConfig"


function Sidebar() {

    const [companies, setCompanies] = useState([])

    useEffect(() => {
        const fetchData = async () => {
            try {
                const res = await api.get("/allCompanies");
                console.log(res.data);
                console.log(typeof(res.data))
                setCompanies(res.data)
            } catch (error) {
                console.error(error);
            }
        };

        fetchData();
    }, []);

    return (
        <>
            <aside
                id="default-sidebar"
                className="fixed top-0 left-0 z-40 w-64 h-screen transition-transform -translate-x-full sm:translate-x-0"
                aria-label="Sidebar"
            >
                <div className="h-full px-3 py-4 overflow-y-auto bg-black text-white">
                    {/* Sidebar Title */}
                    <h2 className="text-2xl font-bold mb-6 px-2 text-center">JASDAQ</h2>

                    <ul className="space-y-2 font-medium">
                        {companies.map((company, index) => (
                            <li key={index}>
                                <NavLink
                                    to={`/company/${company.symbol}/${company.companyId}`}
                                    className={({ isActive }) =>
                                        `flex items-center p-2 rounded-lg group ${isActive
                                            ? "bg-gray-700 text-white"
                                            : "text-white hover:bg-gray-800"
                                        }`
                                    }
                                >
                                    {/* Example icon, you can replace with your own */}
                                    <svg
                                        className="shrink-0 w-5 h-5 text-gray-400 transition duration-75 group-hover:text-white"
                                        xmlns="http://www.w3.org/2000/svg"
                                        fill="currentColor"
                                        viewBox="0 0 20 20"
                                    >
                                        <path d="M10 2a8 8 0 100 16 8 8 0 000-16z" />
                                    </svg>
                                    <span className="ms-3">{company.name}</span>
                                    {/* <span className="ms-3">{company.symbol}</span> */}
                                </NavLink>
                            </li>
                        ))}
                    </ul>
                </div>
            </aside>
        </>

    )

}

export default Sidebar