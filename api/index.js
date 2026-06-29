const express = require('express');
const axios = require('axios');
const cors = require('cors');

const app = express();
// السماح لملف HTML بالاتصال بالخادم
app.use(cors());

const PORT = 3000;
// مفتاح الـ API الخاص بك (آمن هنا ولن يراه الزوار)
const API_KEY = '15381654fdf0e35a2423170aa385065b37f2b9bcdb523273be4ddb65a7404d6e';

app.get('/api/jobs', async (req, res) => {
  // استلام البيانات من الواجهة الأمامية
  const query = req.query.q || 'Developer';
  const location = req.query.location || '';
  
  // تجهيز المعطيات لـ SerpApi
  const params = {
    engine: 'google_jobs',
    q: query,
    api_key: API_KEY,
    hl: 'ar' // اللغة العربية
  };
  
  // إضافة الدولة إذا اختارها المستخدم
  if (location) {
    params.location = location;
  }
  
  try {
    const response = await axios.get('https://serpapi.com/search.json', { params });
    
    // التحقق مما إذا كان API قد أرسل رسالة خطأ (مثل نفاذ الرصيد)
    if (response.data.error) {
      return res.status(400).json({ error: response.data.error });
    }
    
    // إرسال الوظائف إلى الواجهة
    res.json({ jobs: response.data.jobs_results || [] });
    
  } catch (error) {
    console.error("API Error:", error.message);
    
    // التقاط الأخطاء القادمة من موقع SerpApi وإرسالها للواجهة
    if (error.response && error.response.data && error.response.data.error) {
      return res.status(400).json({ error: error.response.data.error });
    }
    
    res.status(500).json({ error: 'حدث خطأ في الخادم أثناء الاتصال بـ API.' });
  }
});

module.exports = app;
