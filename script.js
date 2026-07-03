// --- الإعدادات ---
const API_URL = '/api/chat';
const PI_PRICE_API = '/api/pi-price';

let tokens = 0;
let piUser = null;
let currentPiPriceUSD = 0;
// نفترض أن 50 توكن سعرها 0.50 دولار
const PRICE_FOR_50_TOKENS_USD = 0.50;
let costInPi = 0;

// --- تهيئة Pi Network (Mainnet) ---
Pi.init({ version: "2.0", sandbox: false }); // false = حقيقي

// --- العناصر ---
const loginModal = document.getElementById('login-modal');
const buyModal = document.getElementById('buy-modal');
const piLoginBtn = document.getElementById('pi-login-btn');
const tokenCountElement = document.getElementById('token-count');
const usernameDisplay = document.getElementById('username-display');
const chatBox = document.getElementById('chat-box');
const userInput = document.getElementById('user-input');
const sendBtn = document.getElementById('send-btn');

// --- 1. نظام تسجيل الدخول بـ Pi ---
piLoginBtn.addEventListener('click', async () => {
    try {
        const scopes = ['username', 'payments'];
        const auth = await Pi.authenticate(scopes, onIncompletePaymentFound);
        
        piUser = auth.user;
        usernameDisplay.innerText = `@${piUser.username}`;
        
        // التحقق من قاعدة بيانات المتصفح (في التطبيقات الحقيقية يفضل استخدام قاعدة بيانات خارجية)
        const storedTokens = localStorage.getItem(`tokens_${piUser.username}`);
        
        if (storedTokens === null) {
            // مستخدم جديد = 5 توكن مجاني
            tokens = 5;
            localStorage.setItem(`tokens_${piUser.username}`, tokens);
        } else {
            tokens = parseInt(storedTokens);
        }
        
        updateTokenDisplay();
        loginModal.style.display = 'none';
        
    } catch (err) {
        alert("فشل تسجيل الدخول: " + err.message);
    }
});

function updateTokenDisplay() {
    tokenCountElement.innerText = tokens;
    localStorage.setItem(`tokens_${piUser.username}`, tokens);
}

// --- 2. جلب سعر Pi المباشر من OKX ---
async function fetchLivePiPrice() {
    try {
        document.getElementById('price-loader').style.display = 'block';
        document.getElementById('live-pi-price').style.display = 'none';
        document.getElementById('buy-tokens-btn').style.display = 'none';
        
        // طلب السعر من سيرفر Vercel الخاص بنا (الذي يتصل بـ OKX)
        const res = await fetch(PI_PRICE_API);
        const data = await res.json();
        currentPiPriceUSD = data.price;
        
        // حساب التكلفة (0.50 دولار قسمة سعر الباي الحالي)
        costInPi = (PRICE_FOR_50_TOKENS_USD / currentPiPriceUSD).toFixed(5);
        
        document.getElementById('price-loader').style.display = 'none';
        document.getElementById('live-pi-price').innerText = `السعر: ${costInPi} π (لـ 50 توكن)`;
        document.getElementById('live-pi-price').style.display = 'block';
        document.getElementById('buy-tokens-btn').style.display = 'block';
        
    } catch (error) {
        alert("فشل جلب سعر Pi الحالي.");
    }
}

// --- 3. نظام الشراء بـ Pi Network ---
document.getElementById('buy-tokens-btn').addEventListener('click', () => {
    Pi.createPayment({
        amount: costInPi,
        memo: "Buy 50 AI Tokens",
        metadata: { type: "buy_tokens", tokens_amount: 50 }
    }, {
        onReadyForServerApproval: (paymentId) => {
            // في التطبيقات الحقيقية: نرسل الـ paymentId לסيرفر Vercel للموافقة
            // للتبسيط هنا (Front-end):
            console.log("جاهز للموافقة", paymentId);
        },
        onReadyForServerCompletion: (paymentId, txid) => {
            // بعد اكتمال الدفع، نضيف التوكينات
            tokens += 50;
            updateTokenDisplay();
            buyModal.style.display = 'none';
            alert("تم شراء التوكينات بنجاح!");
        },
        onCancel: (paymentId) => { alert("تم إلغاء الدفع."); },
        onError: (error, payment) => { alert("حدث خطأ في الدفع: " + error.message); }
    });
});

document.getElementById('close-buy-modal').addEventListener('click', () => {
    buyModal.style.display = 'none';
});

function onIncompletePaymentFound(payment) {
    // هذه الدالة تتعامل مع المدفوعات المعلقة
    console.log("مدفوعات معلقة:", payment);
}

// --- 4. إرسال الرسالة (وخصم التوكن) ---
function handleSend() {
    if (!piUser) {
        loginModal.style.display = 'flex';
        return;
    }
    
    if (tokens <= 0) {
        buyModal.style.display = 'flex';
        fetchLivePiPrice(); // جلب السعر عند ظهور النافذة
        return;
    }
    
    const text = userInput.value.trim();
    if (text === '') return;
    
    // خصم توكن واحد
    tokens -= 1;
    updateTokenDisplay();
    
    // عرض رسالة المستخدم
    createMessageElement('user', text);
    userInput.value = '';
    userInput.style.height = 'auto';
    
    // إرسال للـ API (نفس دالة fetchAIResponseStream السابقة)
    fetchAIResponseStream(text);
}

// --- باقي وظائف الدردشة ---
// (ضع هنا دالة createMessageElement و fetchAIResponseStream المكتوبة في الرد السابق بدون تغيير)
// ...
// ...
