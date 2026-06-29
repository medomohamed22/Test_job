const axios = require('axios');

// إعدادات مطوري Pi Network
const PI_CLIENT_ID = process.env.PI_CLIENT_ID || 'DE9gV41Op9HQV9I8kLDKibDLTJp41g6wmHqesVMpylc';
const PI_CLIENT_SECRET = process.env.PI_CLIENT_SECRET || 'your-pi-app-client-secret';

module.exports = async (req, res) => {
    // تفعيل الـ CORS لتجنب مشاكل الاتصال
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');

    if (req.method === 'OPTIONS') {
        return res.status(200).end();
    }

    if (req.method !== 'POST') {
        return res.status(405).json({ error: 'Method not allowed' });
    }

    const { code, redirect_uri } = req.body;

    if (!code) {
        return res.status(400).json({ error: 'كود التحقق الممرر غير موجود' });
    }

    try {
        // طلب الـ Access Token من خوادم Pi
        const tokenResponse = await axios.post('https://api.minepi.com/oauth2/token', new URLSearchParams({
            grant_type: 'authorization_code',
            client_id: PI_CLIENT_ID,
            client_secret: PI_CLIENT_SECRET,
            code: code,
            redirect_uri: redirect_uri
        }), {
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
        });

        const accessToken = tokenResponse.data.access_token;

        // جلب بيانات الـ Profile للمستخدم
        const userResponse = await axios.get('https://api.minepi.com/v2/me', {
            headers: { Authorization: `Bearer ${accessToken}` }
        });

        return res.status(200).json({
            username: userResponse.data.username,
            uid: userResponse.data.uid
        });

    } catch (error) {
        console.error('خطأ Pi OIDC:', error.response ? error.response.data : error.message);
        return res.status(500).json({ 
            error: 'فشل توثيق الحساب مع شبكة Pi Network',
            details: error.response ? error.response.data : error.message
        });
    }
};
