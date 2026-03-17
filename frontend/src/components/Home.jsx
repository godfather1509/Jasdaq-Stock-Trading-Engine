import { useEffect, useState, useRef } from "react"
import { useNavigate } from "react-router-dom"
import api from "../../api/apiConfig"
import SockJS from "sockjs-client";
import Stomp from "stompjs";

function Home() {
    const [companies, setCompanies] = useState([])
    const navigate = useNavigate()
    const stompClient = useRef(null);

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

        const socket = new SockJS("http://localhost:8080/ws");
        const client = Stomp.over(socket);
        client.connect({}, () => {
            stompClient.current = client;
            client.subscribe("/topic/market-updates", (msg) => {
                const update = JSON.parse(msg.body);
                setCompanies(prev => prev.map(c => 
                    c.companyId === update.companyId ? { ...c, currentPrice: update.price } : c
                ));
            });
        });

        return () => {
            if (stompClient.current) stompClient.current.disconnect();
        };
    }, []);

    return (
        <div className="p-8 space-y-12 bg-[#0a0a0f] text-white min-h-screen font-sans">
            {/* Header Section */}
            <div className="flex flex-col items-center justify-center space-y-6 pt-12">
                <div className="relative">
                    <div className="absolute -inset-1 bg-gradient-to-r from-indigo-500 to-purple-600 rounded-lg blur opacity-25 group-hover:opacity-100 transition duration-1000 group-hover:duration-200"></div>
                    <h1 className="relative text-7xl font-black tracking-tighter text-white">
                        JAS<span className="text-indigo-500">DAQ</span>
                    </h1>
                </div>
                <div className="flex items-center space-x-4">
                    <span className="h-px w-12 bg-gray-700"></span>
                    <p className="text-gray-400 font-medium tracking-[0.3em] uppercase text-xs">
                        Institutional Grade Trading Engine
                    </p>
                    <span className="h-px w-12 bg-gray-700"></span>
                </div>
            </div>

            {/* Quick Stats Banner */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 max-w-6xl mx-auto">
                {[
                    { label: "Market Status", value: "Operational", color: "text-green-400" },
                    { label: "Total Volume", value: "$4.2B", color: "text-indigo-400" },
                    { label: "Avg Latency", value: "0.42ms", color: "text-purple-400" }
                ].map((stat, i) => (
                    <div key={i} className="bg-[#16161e]/50 backdrop-blur-xl border border-white/5 rounded-2xl p-6 flex flex-col items-center justify-center space-y-1 hover:border-indigo-500/30 transition-all duration-500">
                        <span className="text-[10px] font-bold text-gray-500 uppercase tracking-widest">{stat.label}</span>
                        <span className={`text-2xl font-black ${stat.color}`}>{stat.value}</span>
                    </div>
                ))}
            </div>

            {/* Main Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-8 max-w-7xl mx-auto px-4">
                {companies.length > 0 ? (
                    companies.map((company, index) => (
                        <div
                            key={index}
                            className="group relative bg-[#16161e] border border-white/5 rounded-[2.5rem] p-1 shadow-2xl transition-all duration-500 hover:scale-[1.02] cursor-pointer"
                            onClick={() => {
                                navigate(`/company/${company.symbol}/${company.companyId}`);
                            }}
                        >
                            <div className="bg-[#1c1c27] rounded-[2.3rem] p-8 h-full flex flex-col items-center space-y-6 overflow-hidden relative">
                                {/* Subtle Background Glow */}
                                <div className="absolute -top-24 -right-24 w-48 h-48 bg-indigo-500/10 rounded-full blur-3xl group-hover:bg-indigo-500/20 transition-all duration-500"></div>
                                
                                <div className="w-20 h-20 bg-gradient-to-br from-indigo-500 via-indigo-600 to-purple-700 rounded-3xl flex items-center justify-center text-white text-3xl font-black shadow-[0_0_30px_-5px_rgba(79,70,229,0.5)] group-hover:shadow-[0_0_40px_-5px_rgba(79,70,229,0.7)] transition-all">
                                    {company.symbol[0]}
                                </div>
                                
                                <div className="text-center z-10">
                                    <h3 className="text-3xl font-black text-white group-hover:text-indigo-400 transition-colors uppercase tracking-tight">
                                        {company.symbol}
                                    </h3>
                                    <p className="text-xs text-gray-500 font-bold mt-1 uppercase tracking-wider h-4 overflow-hidden line-clamp-1">
                                        {company.name}
                                    </p>
                                </div>

                                <div className="w-full bg-[#111119] rounded-3xl p-6 flex flex-col items-center justify-center border border-white/5 group-hover:border-indigo-500/20 transition-all">
                                    <span className="text-[10px] font-black text-gray-600 uppercase tracking-[0.2em] mb-1">Live Quote</span>
                                    <div className="flex items-baseline space-x-1">
                                        <span className="text-gray-500 text-lg font-bold">$</span>
                                        <p className="text-4xl font-black text-white lining-nums transition-all">
                                            {company.currentPrice?.toLocaleString()}
                                        </p>
                                    </div>
                                </div>

                                <div className="w-full pt-2">
                                    <div className="flex items-center justify-center gap-2 py-3 px-6 rounded-2xl bg-indigo-600 text-white font-black text-sm uppercase tracking-widest opacity-0 group-hover:opacity-100 translate-y-4 group-hover:translate-y-0 transition-all duration-500 shadow-lg shadow-indigo-500/20">
                                        Enter Floor <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 7l5 5m0 0l-5 5m5-5H6"></path></svg>
                                    </div>
                                </div>
                            </div>
                        </div>
                    ))
                ) : (
                    <div className="col-span-full py-20 flex flex-col items-center space-y-4">
                        <div className="w-16 h-16 border-4 border-indigo-500/30 border-t-indigo-500 rounded-full animate-spin"></div>
                        <p className="text-gray-500 font-bold tracking-widest uppercase text-sm animate-pulse">Initializing Terminal...</p>
                    </div>
                )}
            </div>
            
            {/* Footer Decoration */}
            <div className="max-w-7xl mx-auto px-4 pt-20 pb-10 flex border-t border-white/5 items-center justify-between text-[10px] font-bold text-gray-600 uppercase tracking-[0.4em]">
                <span>Secured Network</span>
                <span>Real-time Feed Active</span>
                <span>Node: 0x2A...4F</span>
            </div>
        </div>
    )
}

export default Home