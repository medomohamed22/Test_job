export const config = { runtime: 'edge' };

export default async function handler(req) {
  try {
    // الاتصال المباشر بـ OKX API لجلب سعر PI-USDT
    const res = await fetch('https://www.okx.com/api/v5/market/ticker?instId=PI-USDT');
    const data = await res.json();
    
    // استخراج السعر الحالي
    const currentPrice = parseFloat(data.data[0].last);
    
    return new Response(JSON.stringify({ price: currentPrice }), {
      headers: { 'Content-Type': 'application/json' }
    });
  } catch (error) {
    // في حالة فشل الاتصال بالمنصة، نضع سعر افتراضي تقريبي
    return new Response(JSON.stringify({ price: 40.0 }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    });
  }
}
