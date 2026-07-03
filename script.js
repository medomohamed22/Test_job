// =============================
// Pi Network SDK Configuration
// =============================

const API_URL = '/api/chat';
const PI_PRICE_API = '/api/pi-price';

let tokens = 0;
let piUser = null;
let currentPiPriceUSD = 0;
const PRICE_FOR_50_TOKENS_USD = 0.50;
let costInPi = 0;

// =============================
// Elements
// =============================

const loginModal = document.getElementById('login-modal');
const buyModal = document.getElementById('buy-modal');
const piLoginBtn = document.getElementById('pi-login-btn');
const tokenCountElement = document.getElementById('token-count');
const usernameDisplay = document.getElementById('username-display');
const chatBox = document.getElementById('chat-box');
const userInput = document.getElementById('user-input');
const sendBtn = document.getElementById('send-btn');

// =============================
// Initialize Pi SDK
// =============================

if (!window.Pi) {
    console.error("Pi SDK not loaded");
    alert("يجب فتح التطبيق من داخل Pi Browser.");
} else {
    try {
        Pi.init({
            version: "2.0",
            sandbox: false
        });
        
        console.log("✅ Pi SDK initialized");
    } catch (err) {
        console.error("Pi init error:", err);
    }
}

// =============================
// Incomplete Payments
// =============================

const onIncompletePaymentFound = (payment) => {
    console.log("Incomplete payment:", payment);
};

// =============================
// Login System
// =============================

piLoginBtn.addEventListener("click", async () => {
    
    if (!window.Pi) {
        alert("افتح التطبيق من داخل Pi Browser");
        return;
    }
    
    try {
        
        piLoginBtn.innerHTML =
            '<i class="fas fa-spinner fa-spin"></i> جاري الاتصال...';
        
        piLoginBtn.disabled = true;
        piLoginBtn.style.opacity = "0.7";
        
        const scopes = [
            "username",
            "payments"
        ];
        
        const auth = await Pi.authenticate(
            scopes,
            onIncompletePaymentFound
        );
        
        console.log("Auth:", auth);
        
        if (!auth || !auth.user) {
            throw new Error("لم يتم استلام بيانات المستخدم");
        }
        
        piUser = auth.user;
        
        usernameDisplay.innerText =
            "@" + piUser.username;
        
        const saved =
            localStorage.getItem(
                `tokens_${piUser.username}`
            );
        
        if (saved === null) {
            tokens = 5;
            localStorage.setItem(
                `tokens_${piUser.username}`,
                tokens
            );
        } else {
            tokens = parseInt(saved);
        }
        
        updateTokenDisplay();
        
        loginModal.style.display = "none";
        
        console.log(
            "✅ Login Success:",
            piUser.username
        );
        
    } catch (err) {
        
        console.error("Login Error:", err);
        
        piLoginBtn.innerHTML =
            '<i class="fab fa-pi"></i> تسجيل الدخول بـ Pi';
        
        piLoginBtn.disabled = false;
        piLoginBtn.style.opacity = "1";
        
        if (
            err.message &&
            err.message
            .toLowerCase()
            .includes("browser")
        ) {
            alert(
                "يجب فتح التطبيق من داخل Pi Browser."
            );
        } else {
            alert(
                "فشل تسجيل الدخول:\n" +
                (err.message || err)
            );
        }
    }
});

// =============================
// Update Tokens
// =============================

function updateTokenDisplay() {
    
    if (tokenCountElement) {
        tokenCountElement.innerText = tokens;
    }
    
    if (piUser) {
        localStorage.setItem(
            `tokens_${piUser.username}`,
            tokens
        );
    }
}
