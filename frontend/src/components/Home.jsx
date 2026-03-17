import { useEffect, useState } from "react"
import { NavLink, Navigate, useNavigate } from "react-router-dom"
import api from "../../api/apiConfig"

function Home() {

    const [companies, setCompanies] = useState([])
    const navigate = useNavigate()
    useEffect(() => {
        const fetchData = async () => {
            try {
                const res = await api.get("/allCompanies");
                console.log(res.data);
                setCompanies(res.data)
            } catch (error) {
                console.error(error);
            }
        };

        fetchData();
    }, []);


    return (
        <div className="p-4 space-y-8 bg-black min-h-screen">
            <h2 className="text-4xl text-white text-center">Jasdaq</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
                {companies.map((company, index) => (
                    <button
                        key={index}
                        className="text-center cursor-pointer bg-black border border-gray-700 rounded-lg shadow hover:shadow-md p-4 transition duration-200"
                        onClick={() => {
                            navigate(`/company/${company.symbol}/${company.companyId}`);
                        }}
                    >
                        <h3 className="text-lg font-semibold text-white text-center">
                            {company.symbol}
                        </h3>
                        <p className="text-sm text-gray-300 text-center">
                            ${company.currentPrice}
                        </p>
                        <p className="text-sm text-gray-300 text-center">
                            {company.name}
                        </p>
                    </button>
                ))}
            </div>
        </div>


    )
}

export default Home