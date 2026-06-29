const express = require('express');
const axios = require('axios');
const path = require('path');
const app = express();
const PORT = process.env.PORT || 3000;

// إعدادات مطوري Pi Network (قم بوضع بياناتك الحقيقية المستخرجة من لوحة تحكم مطوري Pi)
const PI_CLIENT_ID = 'DE9gV41Op9HQV9I8kLDKibDLTJp41g6wmHqesVMpylc';
const PI_CLIENT_SECRET = 'your-pi-app-client-secret';

// تفعيل قراءة صيغ الملفات والـ JSON
app.use(express.json());
app.use(express.static(path.join(__dirname))); // يخدم ملف index.html بشكل تلقائي

// ============================================
// 1. مسار تسجيل الدخول الموثق الجديد لـ Pi OIDC
// ============================================
app.post('/api/pi-login', async (req, res) => {
    const { code, redirect_uri } = req.body;

    if (!code) {
        return res.status(400).json({ error: 'كود التحقق الممرر غير موجود' });
    }

    try {
        // الخطوة أ: إرسال الكود لخوادم Pi لاستبداله بالـ Access Token بكل أمان
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

        // الخطوة ب: استخدام الـ Access Token لجلب بيانات الـ Profile للمستخدم
        const userResponse = await axios.get('https://api.minepi.com/v2/me', {
            headers: { Authorization: `Bearer ${accessToken}` }
        });

        // إرجاع اسم المستخدم ومعرفه الفريد للفرونت إند للترحيب به وحفظ جلسته
        return res.json({
            username: userResponse.data.username,
            uid: userResponse.data.uid
        });

    } catch (error) {
        console.error('خطأ أثناء مطابقة بيانات Pi:', error.response ? error.response.data : error.message);
        return res.status(500).json({ 
            error: 'فشل توثيق الحساب مع شبكة Pi Network', 
            details: error.response ? error.response.data : error.message 
        });
    }
});

// ============================================
// 2. مسار البحث الحالي الخاص بمحرك الوظائف (كما هو)
// ============================================
app.get('/api/jobs', async (req, res) => {
    const query = req.query.q || 'Developer';
    const location = req.query.location || '';
    
    try {
        // هنا يتم وضع منطق جلب الوظائف الخاص بك أو الـ API الخارجي الذي تتصل به (مثل SerpAPI أو ما شابه)
        // مثال لرد افتراضي فارغ لضمان عدم توقف السيرفر:
        return res.json({ jobs: [] });
    } catch (error) {
        return res.status(500).json({ error: 'حدث خطأ أثناء جلب الوظائف من الخادم الحلي' });
    }
});

// تشغيل السيرفر
app.listen(PORT, () => {
    console.log(`السيرفر يعمل بنجاح على الرابط التالي: http://localhost:${PORT}`);
});
