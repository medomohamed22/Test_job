export const config = {
  runtime: 'edge' // استخدام بيئة Edge لسرعة البث المباشر كالبرق
};

export default async function handler(req) {
  if (req.method !== 'POST') {
    return new Response('Method Not Allowed', { status: 405 });
  }
  
  try {
    // قراءة المفتاح من إعدادات Vercel السرية
    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) {
      return new Response(JSON.stringify({ error: "API Key is missing in Vercel Environment Variables" }), { status: 500 });
    }
    
    const body = await req.json();
    
    // رابط جوجل الأصلي مع المفتاح السري
    const googleUrl = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse&key=${apiKey}`;
    
    // إرسال الطلب لجوجل
    const response = await fetch(googleUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    
    // تمرير البث المباشر (Stream) فوراً للمستخدم بدون كشف المفتاح
    return new Response(response.body, {
      status: response.status,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive'
      }
    });
    
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500 });
  }
}
