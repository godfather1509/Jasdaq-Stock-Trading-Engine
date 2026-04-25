import { useState } from "react";
import api from "../../api/apiConfig";

function AddAssetModal({ isOpen, onClose, onRefresh }) {
    const [formData, setFormData] = useState({
        symbol: "",
        name: "",
        totalShares: 1000,
        initialPrice: 100
    });
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    if (!isOpen) return null;

    const handleSubmit = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError("");
        try {
            const resp = await api.post("companies", formData);
            if (resp.data.status === "ok") {
                onRefresh();
                onClose();
                setFormData({ symbol: "", name: "", totalShares: 1000, initialPrice: 100 });
            } else {
                setError(resp.data.message || "Failed to create asset");
            }
        } catch (err) {
            setError(err.response?.data?.message || "Connection failed");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4">
            <div className="absolute inset-0 bg-black/80 backdrop-blur-sm" onClick={onClose}></div>
            <div className="relative bg-[#16161e] border border-white/10 rounded-[2.5rem] w-full max-w-md overflow-hidden shadow-2xl animate-in fade-in zoom-in duration-300">
                <div className="p-10 space-y-8">
                    <div className="text-center">
                        <h2 className="text-3xl font-black text-white tracking-tight">Provision <span className="text-indigo-500">Asset</span></h2>
                        <p className="text-xs font-bold text-gray-500 uppercase tracking-widest mt-2">Initialize new company on Jasdaq</p>
                    </div>

                    <form onSubmit={handleSubmit} className="space-y-6">
                        <div className="space-y-4">
                            <div>
                                <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest ml-4 mb-2 block">Ticker Symbol</label>
                                <input 
                                    type="text" 
                                    placeholder="e.g. AAPL"
                                    className="w-full bg-[#1c1c27] border border-white/5 rounded-2xl px-6 py-4 text-white font-bold focus:border-indigo-500/50 focus:outline-none transition-all placeholder:text-gray-700"
                                    value={formData.symbol}
                                    onChange={(e) => setFormData({...formData, symbol: e.target.value.toUpperCase()})}
                                    required
                                />
                            </div>
                            <div>
                                <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest ml-4 mb-2 block">Company Name</label>
                                <input 
                                    type="text" 
                                    placeholder="e.g. Apple Inc."
                                    className="w-full bg-[#1c1c27] border border-white/5 rounded-2xl px-6 py-4 text-white font-bold focus:border-indigo-500/50 focus:outline-none transition-all placeholder:text-gray-700"
                                    value={formData.name}
                                    onChange={(e) => setFormData({...formData, name: e.target.value})}
                                    required
                                />
                            </div>
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest ml-4 mb-2 block">Issue Size</label>
                                    <input 
                                        type="number" 
                                        className="w-full bg-[#1c1c27] border border-white/5 rounded-2xl px-6 py-4 text-white font-bold focus:border-indigo-500/50 focus:outline-none transition-all"
                                        value={formData.totalShares}
                                        onChange={(e) => setFormData({...formData, totalShares: parseInt(e.target.value)})}
                                        min="1"
                                        required
                                    />
                                </div>
                                <div>
                                    <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest ml-4 mb-2 block">IPO Price</label>
                                    <input 
                                        type="number" 
                                        className="w-full bg-[#1c1c27] border border-white/5 rounded-2xl px-6 py-4 text-white font-bold focus:border-indigo-500/50 focus:outline-none transition-all"
                                        value={formData.initialPrice}
                                        onChange={(e) => setFormData({...formData, initialPrice: parseInt(e.target.value)})}
                                        min="1"
                                        required
                                    />
                                </div>
                            </div>
                        </div>

                        {error && (
                            <div className="bg-red-500/10 border border-red-500/20 text-red-500 text-[10px] font-bold uppercase tracking-widest p-4 rounded-xl text-center animate-pulse">
                                {error}
                            </div>
                        )}

                        <div className="pt-4 flex gap-4">
                            <button 
                                type="button" 
                                onClick={onClose}
                                className="flex-1 px-8 py-5 rounded-2xl bg-white/5 text-gray-400 font-black text-xs uppercase tracking-widest hover:bg-white/10 transition-all"
                            >
                                Cancel
                            </button>
                            <button 
                                type="submit" 
                                disabled={loading}
                                className="flex-[2] px-8 py-5 rounded-2xl bg-indigo-600 text-white font-black text-xs uppercase tracking-widest hover:bg-indigo-500 transition-all shadow-lg shadow-indigo-600/20 disabled:opacity-50"
                            >
                                {loading ? "Initializing..." : "Register Asset"}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    );
}

export default AddAssetModal;
