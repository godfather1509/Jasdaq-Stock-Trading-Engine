import { useEffect, useState } from "react"
import { NavLink } from "react-router-dom"
import api from "../../api/apiConfig"


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
        <aside className="fixed top-0 left-0 z-40 w-72 h-screen transition-transform -translate-x-full sm:translate-x-0 border-r border-white/5 bg-[#0a0a0f]">
            <div className="h-full px-4 py-8 overflow-y-auto flex flex-col space-y-8">
                {/* Logo Section */}
                <NavLink to="/" className="px-4 group">
                    <div className="flex flex-col">
                        <span className="text-3xl font-black tracking-tighter text-white group-hover:text-indigo-400 transition-colors">
                            JAS<span className="text-indigo-500">DAQ</span>
                        </span>
                        <div className="flex items-center space-x-2 mt-1">
                            <div className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse"></div>
                            <span className="text-[10px] uppercase tracking-[0.2em] font-bold text-gray-500">System Live</span>
                        </div>
                    </div>
                </NavLink>

                {/* Navigation Section */}
                <div className="flex-1">
                    <p className="px-4 text-[10px] font-black text-gray-600 uppercase tracking-[0.2em] mb-4">Market Assets</p>
                    <ul className="space-y-1.5">
                        {companies.map((company, index) => (
                            <li key={index}>
                                <NavLink
                                    to={`/company/${company.symbol}/${company.companyId}`}
                                    className={({ isActive }) =>
                                        `flex items-center justify-between px-4 py-3 rounded-2xl transition-all duration-300 group ${isActive
                                            ? "bg-indigo-600/10 border-l-4 border-indigo-500 text-white"
                                            : "text-gray-400 hover:text-white hover:bg-white/5"
                                        }`
                                    }
                                >
                                    <div className="flex items-center space-x-3">
                                        <div className="w-8 h-8 rounded-lg bg-white/5 flex items-center justify-center font-bold text-[10px] text-gray-400 group-hover:bg-indigo-500 group-hover:text-white transition-all">
                                            {company.symbol[0]}
                                        </div>
                                        <div className="flex flex-col">
                                            <span className="text-sm font-bold tracking-tight">{company.symbol}</span>
                                            <span className="text-[10px] text-gray-600 group-hover:text-gray-400 transition-colors font-medium">{company.name}</span>
                                        </div>
                                    </div>
                                    <svg className="w-4 h-4 opacity-0 group-hover:opacity-100 -translate-x-2 group-hover:translate-x-0 transition-all text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7"></path>
                                    </svg>
                                </NavLink>
                            </li>
                        ))}
                    </ul>
                </div>

                {/* Account/Status Footer */}
                <div className="px-4 pt-6 border-t border-white/5">
                    <div className="bg-white/5 rounded-2xl p-4 flex items-center space-x-3">
                        <div className="w-10 h-10 rounded-full bg-gradient-to-r from-indigo-500 to-purple-600 flex items-center justify-center font-black text-white text-xs">
                            JD
                        </div>
                        <div className="flex flex-col">
                            <span className="text-xs font-black text-white">Dev Console</span>
                            <span className="text-[10px] text-gray-500 font-bold uppercase tracking-widest">Operator-01</span>
                        </div>
                    </div>
                </div>
            </div>
        </aside>
    )
}

export default Sidebar