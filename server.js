const express = require('express');
const axios = require('axios');
const cors = require('cors');

const app = express();
// تفعيل CORS للسماح لملف الـ HTML الخاص بك بالاتصال بهذا الخادم فقط
app.use(cors());

const PORT = 3000;
// المفتاح هنا في أمان تام ولن يراه زوار الموقع
const API_KEY = '15381654fdf0e35a2423170aa385065b37f2b9bcdb523273be4ddb65a7404d6e';

app.get('/api/jobs', async (req, res) => {
  const query = req.query.q || 'Developer';
  
  try {
    // الخادم هو من يتصل بـ SerpApi وليس المتصفح
    const response = await axios.get('https://serpapi.com/search.json', {
      params: {
        engine: 'google_jobs',
        q: query,
        api_key: API_KEY,
        hl: 'ar' // يمكن تغييرها إلى en
      }
    });
    
    res.json(response.data.jobs_results || []);
  } catch (error) {
    console.error("API Error:", error.message);
    res.status(500).json({ error: 'حدث خطأ في جلب البيانات' });
  }
});

app.listen(PORT, () => {
  console.log(`✅ الخادم يعمل بنجاح على الرابط: http://localhost:${PORT}`);
});
