import { useState, useEffect } from "react";
import { useParams } from "react-router-dom";
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts";
import api from "../../api/apiConfig"

function CompanyPage() {

    const { companyId } = useParams()
    const [c, setC] = useState({})
    const [o, setO] = useState({})
    const [showForm, setShowForm] = useState(false);
    const [form, setForm] = useState({
        quantity: "",
        price: "",
        type: "market",
        side: "buy",
    });

    const handleChange = (e) => {
        const { name, value } = e.target;
        setForm((prev) => ({ ...prev, [name]: value }));
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        console.log("Order Placed:", form);
        setShowForm(false);
    };

    const data = [
        { time: "09:00", price: 120 },
        { time: "10:00", price: 125 },
        { time: "11:00", price: 123 },
        { time: "12:00", price: 128 },
        { time: "13:00", price: 127 },
        { time: "14:00", price: 130 },
    ];

    useEffect(() => {
        const fetchMovie = async () => {
            try {
                const res = await api.get(`/${companyId}`);
                const payload = res.data;
                console.log(payload);
                setC(payload)
                setO(payload.orders)
            } catch (err) {
                console.error(err);
            }
        };
        fetchMovie();
    }, [companyId]);

    return (
        <div className="p-6 text-black grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Company Info */}
            <div className="text-center">
                <h1 className="text-3xl font-bold text-black">{c.name}</h1>
                <p className="text-black">{c.symbol}</p>
                <p className="text-xl font-semibold mt-2 text-black">
                    Current Price: ${c.currentPrice}
                </p>

                {/* Place Order */}
                <div className="mt-4">
                    <button
                        className="bg-indigo-600 text-white px-6 py-2 rounded-2xl shadow hover:bg-indigo-700 transition"
                        onClick={() => setShowForm(!showForm)}
                    >
                        {showForm ? "Cancel" : "Place Order"}
                    </button>

                    {showForm && (
                        <div className="mt-4 p-4 rounded-2xl shadow-md border bg-white">
                            <form
                                onSubmit={handleSubmit}
                                className="flex flex-col gap-3"
                            >
                                <input
                                    type="number"
                                    name="quantity"
                                    value={form.quantity}
                                    onChange={handleChange}
                                    placeholder="Quantity"
                                    className="border p-2 rounded-lg"
                                    required
                                />
                                {form.type === "limit" && (
                                    <input
                                        type="number"
                                        name="price"
                                        value={form.price}
                                        onChange={handleChange}
                                        placeholder="Price"
                                        className="border p-2 rounded-lg"
                                        required
                                    />
                                )}
                                <select
                                    name="type"
                                    value={form.type}
                                    onChange={handleChange}
                                    className="border p-2 rounded-lg"
                                >
                                    <option value="market">Market</option>
                                    <option value="limit">Limit</option>
                                </select>
                                <select
                                    name="side"
                                    value={form.side}
                                    onChange={handleChange}
                                    className="border p-2 rounded-lg"
                                >
                                    <option value="buy">Buy</option>
                                    <option value="sell">Sell</option>
                                </select>
                                <button
                                    type="submit"
                                    className="bg-green-600 text-white py-2 rounded-2xl shadow hover:bg-green-700 transition"
                                >
                                    Submit Order
                                </button>
                            </form>
                        </div>
                    )}
                </div>
            </div>

            {/* Graph */}
            <div className="bg-white p-4 rounded-2xl shadow-md h-80">
                <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={data}>
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis dataKey="time" />
                        <YAxis />
                        <Tooltip />
                        <Line
                            type="monotone"
                            dataKey="price"
                            stroke="#4F46E5"
                            strokeWidth={2}
                        />
                    </LineChart>
                </ResponsiveContainer>
            </div>
        </div>

    );

}

export default CompanyPage