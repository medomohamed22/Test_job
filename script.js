// --- الإعدادات ---
const API_URL = '/api/chat';
const PI_PRICE_API = '/api/pi-price';

let tokens = 0;
let piUser = null;
let currentPiPriceUSD = 0;
const PRICE_FOR_50_TOKENS_USD = 0.50;
let costInPi = 0;

// --- العناصر ---
const loginModal = document.getElementById('login-modal');
const buyModal = document.getElementById('buy-modal');
const piLoginBtn = document.getElementById('pi-login-btn');
const tokenCountElement = document.getElementById('token-count');
const usernameDisplay = document.getElementById('username-display');
const chatBox = document.getElementById('chat-box');
const userInput = document.getElementById('user-input');
const sendBtn = document.getElementById('send-btn');

// --- 1. تهيئة Pi Network باحترافية ---
// التحقق من أن مكتبة Pi تم تحميلها من الـ HTML
if (typeof window.Pi === 'undefined') {
    console.error("⚠️ لم يتم تحميل مكتبة Pi Network. تأكد من وجود سكريبت pi.js في ملف HTML.");
} else {
    try {
        // نصيحة: للتجربة اجعلها sandbox: true، وعند الإطلاق الفعلي للجمهور اجعلها sandbox: false
        Pi.init({ version: "2.0", sandbox: false });
        console.log("✅ تم تهيئة Pi Network بنجاح.");
    } catch (e) {
        console.error("⚠️ خطأ في تهيئة Pi:", e);
    }
}

// تعريف دالة المدفوعات المعلقة (يجب تعريفها قبل تمريرها لتسجيل الدخول)
const onIncompletePaymentFound = (payment) => {
    console.log("مدفوعات معلقة تحتاج لمعالجة:", payment);
};

// --- 2. نظام تسجيل الدخول بـ Pi (مضاد للأخطاء) ---
piLoginBtn.addEventListener('click', async () => {
    try {
        // تغيير شكل الزر لإظهار حالة "التحميل"
        piLoginBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> جاري الاتصال...';
        piLoginBtn.style.opacity = '0.7';
        piLoginBtn.disabled = true;
        
        // طلب صلاحيات الدخول
        const scopes = ['username', 'payments'];
        
        // استدعاء نافذة تسجيل الدخول الخاصة بـ Pi
        const auth = await Pi.authenticate(scopes, onIncompletePaymentFound);
        
        if (auth && auth.user) {
            piUser = auth.user;
            usernameDisplay.innerText = `@${piUser.username}`;
            
            // جلب التوكينات من الذاكرة المحلية
            const storedTokens = localStorage.getItem(`tokens_${piUser.username}`);
            
            if (storedTokens === null) {
                tokens = 5; // هدية أول استخدام
                localStorage.setItem(`tokens_${piUser.username}`, tokens);
            } else {
                tokens = parseInt(storedTokens);
            }
            
            updateTokenDisplay();
            
            // إخفاء نافذة تسجيل الدخول بنجاح
            loginModal.style.display = 'none';
        }
        
    } catch (err) {
        // إعادة الزر لحالته الطبيعية في حالة الخطأ
        piLoginBtn.innerHTML = '<i class="fab fa-pi"></i> تسجيل الدخول بـ Pi';
        piLoginBtn.style.opacity = '1';
        piLoginBtn.disabled = false;
        
        // تحليل الخطأ وعرض رسالة مفيدة للمستخدم
        console.error("خطأ تسجيل الدخول:", err);
        
        if (err.message && err.message.toLowerCase().includes("not within pi browser")) {
            alert("⚠️ عذراً! يجب فتح هذا الموقع من داخل تطبيق متصفح (Pi Browser) لكي يعمل تسجيل الدخول.");
        } else {
            alert("⚠️ فشل الاتصال بشبكة Pi. \nالسبب: " + (err.message || err) + "\nتأكد من فتح الموقع في Pi Browser.");
        }
    }
});

// تحديث عرض التوكينات
function updateTokenDisplay() {
    if (tokenCountElement) {
        tokenCountElement.innerText = tokens;
    }
    if (piUser) {
        localStorage.setItem(`tokens_${piUser.username}`, tokens);
    }
}

// --- باقي الأكواد كما هي (جلب السعر، الشراء، الإرسال للذكاء الاصطناعي، الخ) ---
// ... (ضع باقي الدوال الخاصة بك هنا)
