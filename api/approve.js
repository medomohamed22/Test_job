const axios = require('axios');

const PI_API_KEY = "gnpdg0e5gxou6yojiegw2o3yzuqbctomll32amllpxp7x0wuq4apjcqrzqkk2wqa"; 

module.exports = async (req, res) => {
    // تفعيل إعدادات CORS للسماح بالاتصال من الواجهة الأمامية للموقع
    res.setHeader('Access-Control-Allow-Credentials', true);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,PATCH,DELETE,POST,PUT');
    res.setHeader('Access-Control-Allow-Headers', 'X-CSRF-Token, X-Requested-With, Accept, Accept-Version, Content-Length, Content-MD5, Content-Type, Date, X-Api-Version');

    if (req.method === 'OPTIONS') {
        return res.status(200).end();
    }

    if (req.method !== 'POST') {
        return res.status(405).json({ error: 'Method Not Allowed' });
    }

    const { paymentId } = req.body;
    if (!paymentId) {
        return res.status(400).json({ error: 'Missing paymentId' });
    }

    try {
        const response = await axios.post(
            `https://api.minepi.com/v2/payments/${paymentId}/approve`,
            {},
            {
                headers: {
                    Authorization: `Key ${PI_API_KEY}`,
                    'Content-Type': 'application/json'
                }
            }
        );
        return res.status(200).json(response.data);
    } catch (error) {
        console.error("Vercel Approve Error:", error.response?.data || error.message);
        return res.status(500).json({ error: 'فشلت عملية الـ Approve من السيرفر' });
    }
};
