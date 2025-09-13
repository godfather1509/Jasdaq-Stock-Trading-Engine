import { useState, useEffect } from "react";
import { useParams } from "react-router-dom";
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts";
import api from "../../api/apiConfig"

function CompanyPage() {

    const { companyId } = useParams()
    const [c, setC] = useState({})
    const [o, setO] = useState({})

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
        <div className="p-6 text-black">
            {/* Company Info */}
            <div className="mb-6 text-center">
                <h1 className="text-3xl font-bold text-black">{c.name}</h1>
                <p className="text-black">{c.symbol}</p>
                <p className="text-xl font-semibold mt-2 text-black">
                    Current Price: ${c.currentPrice}
                </p>
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