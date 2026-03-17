import { useState, useEffect, useRef } from "react";
import { useParams } from "react-router-dom";
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts";
import api from "../../api/apiConfig";
import SockJS from "sockjs-client";
import Stomp from "stompjs";

function CompanyPage() {
    const { companyId, companySymbol } = useParams();
    const [c, setC] = useState({});
    const [o, setO] = useState([]);
    const [chartData, setChartData] = useState([]);
    const [form, setForm] = useState({
        quantity: "",
        price: "",
        type: "market",
        side: "buy",
    });

    const stompClient = useRef(null);

    useEffect(() => {
        const socket = new SockJS("http://localhost:8080/ws");
        const client = Stomp.over(socket);

        client.connect({}, () => {
            console.log("Connected to WebSocket");
            stompClient.current = client;

            client.subscribe("/topic/market-updates", (msg) => {
                const update = JSON.parse(msg.body);
                if (update.companyId === companyId) {
                    setC(prev => ({ ...prev, currentPrice: update.price }));
                    
                    const time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
                    setChartData(prev => {
                        const newData = [...prev, { time, price: update.price }];
                        return newData.slice(-20); // Keep last 20 points
                    });
                }
            });

            client.subscribe("/user/queue/orders", (msg) => {
                const updatedOrder = JSON.parse(msg.body);
                setO(prev => {
                    const exists = prev.find(item => item.orderId === updatedOrder.orderId);
                    if (exists) {
                        return prev.map(item => item.orderId === updatedOrder.orderId ? updatedOrder : item);
                    }
                    return [updatedOrder, ...prev];
                });
            });

            // Subscription for cancel confirmation
            client.subscribe("/user/queue/cancel", (msg) => {
                const canceledOrder = JSON.parse(msg.body);
                setO(prev => prev.filter(item => item.orderId !== canceledOrder.orderId));
            });
        });

        return () => {
            if (stompClient.current) stompClient.current.disconnect();
        };
    }, [companyId]);

    const handleChange = (e) => {
        const { name, value } = e.target;
        setForm((prev) => ({ ...prev, [name]: value }));
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (stompClient.current && stompClient.current.connected) {
            const orderPayload = {
                symbol: companySymbol,
                buySell: form.side === "buy",
                marketLimit: form.type === "market",
                shares: parseInt(form.quantity),
                price: form.type === "market" ? 0 : parseInt(form.price),
                companyId: companyId
            };
            
            stompClient.current.send("/app/placeOrder", {}, JSON.stringify(orderPayload));
        } else {
            alert("WebSocket not connected. Please refresh.");
        }
    };

    const handleCancelOrder = (orderId) => {
        if (stompClient.current && stompClient.current.connected) {
            const cancelPayload = {
                orderId: orderId,
                companyId: companyId
            };
            stompClient.current.send("/app/cancelOrder", {}, JSON.stringify(cancelPayload));
        }
    };

    useEffect(() => {
        const fetchCompany = async () => {
            try {
                const res = await api.get(companyId);
                const payload = res.data;
                setC(payload);
                setO(payload.orders || []);
                
                // Initialize chart with some data points if available
                if (payload.currentPrice) {
                    const now = new Date();
                    const initialPoints = [];
                    for(let i=5; i>=0; i--) {
                        initialPoints.push({
                            time: new Date(now - i*60000).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
                            price: payload.currentPrice + (Math.random() * 10 - 5)
                        });
                    }
                    setChartData(initialPoints);
                }
            } catch (err) {
                console.error(err);
            }
        };
        fetchCompany();
    }, [companyId]);

    return (
        <div className="p-6 text-black space-y-8 bg-gray-50 min-h-screen">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="bg-white p-8 rounded-3xl shadow-xl border border-gray-100 flex flex-col items-center justify-center">
                    <h1 className="text-4xl font-extrabold text-indigo-900">{c.name}</h1>
                    <p className="text-indigo-600 font-mono tracking-widest text-lg">{c.symbol}</p>
                    <p className="text-3xl font-bold mt-4 text-green-600">
                        ${c.currentPrice?.toLocaleString()}
                    </p>

                    <div className="mt-8 w-full max-w-sm">
                        <div className="p-6 rounded-2xl bg-gray-50 border border-gray-200 shadow-inner">
                            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                                <div className="space-y-1">
                                    <label className="text-xs font-bold text-gray-500 uppercase ml-1">Quantity</label>
                                    <input
                                        type="number"
                                        name="quantity"
                                        value={form.quantity}
                                        onChange={handleChange}
                                        placeholder="Number of shares"
                                        className="w-full border-0 bg-white p-3 rounded-xl shadow-sm focus:ring-2 focus:ring-indigo-500"
                                        required
                                    />
                                </div>
                                
                                {form.type === "limit" && (
                                    <div className="space-y-1">
                                        <label className="text-xs font-bold text-gray-500 uppercase ml-1">Limit Price</label>
                                        <input
                                            type="number"
                                            name="price"
                                            value={form.price}
                                            onChange={handleChange}
                                            placeholder="Price per share"
                                            className="w-full border-0 bg-white p-3 rounded-xl shadow-sm focus:ring-2 focus:ring-indigo-500"
                                            required
                                        />
                                    </div>
                                )}

                                <div className="grid grid-cols-2 gap-3">
                                    <select
                                        name="type"
                                        value={form.type}
                                        onChange={handleChange}
                                        className="border-0 bg-white p-3 rounded-xl shadow-sm focus:ring-2 focus:ring-indigo-500"
                                    >
                                        <option value="market">Market</option>
                                        <option value="limit">Limit</option>
                                    </select>
                                    <select
                                        name="side"
                                        value={form.side}
                                        onChange={handleChange}
                                        className={`border-0 p-3 rounded-xl shadow-sm text-white font-bold transition-colors ${form.side === 'buy' ? 'bg-green-500' : 'bg-red-500'}`}
                                    >
                                        <option value="buy" className="bg-white text-black">BUY</option>
                                        <option value="sell" className="bg-white text-black">SELL</option>
                                    </select>
                                </div>
                                
                                <button
                                    type="submit"
                                    className="mt-2 bg-indigo-600 text-white py-4 rounded-xl font-bold text-lg shadow-lg hover:bg-indigo-700 active:scale-95 transition-all transform"
                                >
                                    Place Order
                                </button>
                            </form>
                        </div>
                    </div>
                </div>

                <div className="bg-white p-6 rounded-3xl shadow-xl border border-gray-100 h-96">
                    <h3 className="text-gray-400 text-xs font-bold uppercase mb-4 ml-2">Market Activity</h3>
                    <ResponsiveContainer width="100%" height="90%">
                        <LineChart data={chartData}>
                            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f0f0f0" />
                            <XAxis dataKey="time" axisLine={false} tickLine={false} tick={{fill: '#999', fontSize: 12}} />
                            <YAxis domain={['auto', 'auto']} axisLine={false} tickLine={false} tick={{fill: '#999', fontSize: 12}} />
                            <Tooltip contentStyle={{borderRadius: '12px', border: 'none', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)'}} />
                            <Line
                                type="monotone"
                                dataKey="price"
                                stroke="#4F46E5"
                                strokeWidth={4}
                                dot={false}
                                activeDot={{ r: 8, strokeWidth: 0 }}
                                animationDuration={300}
                            />
                        </LineChart>
                    </ResponsiveContainer>
                </div>
            </div>

            <div className="bg-white p-6 rounded-3xl shadow-xl border border-gray-100">
                <div className="flex justify-between items-center mb-6 px-2">
                    <h2 className="text-2xl font-bold text-indigo-900">Your Orders</h2>
                    <span className="bg-indigo-100 text-indigo-700 text-xs font-bold px-3 py-1 rounded-full">{o.length} Active</span>
                </div>
                <div className="overflow-x-auto">
                    <table className="w-full">
                        <thead>
                            <tr className="text-left text-gray-400 text-xs font-bold uppercase tracking-wider">
                                <th className="px-6 py-3">ID</th>
                                <th className="px-6 py-3">Type</th>
                                <th className="px-6 py-3">Side</th>
                                <th className="px-6 py-3 text-right">Qty</th>
                                <th className="px-6 py-3 text-right">Price</th>
                                <th className="px-6 py-3">Status</th>
                                <th className="px-6 py-3 text-center">Action</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                            {o.length > 0 ? (
                                o.map((order, idx) => (
                                    <tr key={idx} className="hover:bg-gray-50 transition-colors">
                                        <td className="px-6 py-4 font-mono text-xs text-gray-500">{order.orderId}</td>
                                        <td className="px-6 py-4">
                                            <span className={`px-2 py-1 rounded-md text-[10px] font-black ${order.marketLimit ? 'bg-orange-100 text-orange-700' : 'bg-blue-100 text-blue-700'}`}>
                                                {order.marketLimit ? "MARKET" : "LIMIT"}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4">
                                            <span className={`font-bold ${order.buySell ? 'text-green-600' : 'text-red-600'}`}>
                                                {order.buySell ? "BUY" : "SELL"}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4 text-right font-semibold">{order.shares}</td>
                                        <td className="px-6 py-4 text-right font-mono text-indigo-600">
                                            {order.finalPrice ? `$${order.finalPrice.toLocaleString()}` : order.price ? `$${order.price.toLocaleString()}` : "-"}
                                        </td>
                                        <td className="px-6 py-4">
                                            <div className="flex items-center gap-2">
                                                <div className={`w-2 h-2 rounded-full ${order.status ? 'bg-green-500' : 'bg-yellow-500 anima-pulse'}`}></div>
                                                <span className={`text-sm font-medium ${order.status ? 'text-green-700' : 'text-yellow-700'}`}>
                                                    {order.status === true ? "Executed" : "Pending"}
                                                </span>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 text-center">
                                            {!order.status && (
                                                <button 
                                                    onClick={() => handleCancelOrder(order.orderId)}
                                                    className="text-red-500 hover:text-red-700 hover:bg-red-50 font-bold text-xs p-2 rounded-lg transition-all"
                                                >
                                                    Cancel
                                                </button>
                                            )}
                                        </td>
                                    </tr>
                                ))
                            ) : (
                                <tr>
                                    <td colSpan="7" className="text-center py-12 text-gray-400 italic">
                                        Your order book is empty
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}

export default CompanyPage;