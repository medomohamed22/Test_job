const API_URL = '/api/chat';
const PI_PRICE_API = '/api/pi-price';

let tokens = 0;
let piUser = null;
let isSending = false;
let currentPiPriceUSD = 0;
const PRICE_FOR_50_TOKENS_USD = 0.50;
let costInPi = 0;

const loginModal = document.getElementById('login-modal');
const buyModal = document.getElementById('buy-modal');
const piLoginBtn = document.getElementById('pi-login-btn');
const tokenCountElement = document.getElementById('token-count');
const usernameDisplay = document.getElementById('username-display');
const chatBox = document.getElementById('chat-box');
const userInput = document.getElementById('user-input');
const sendBtn = document.getElementById('send-btn');
const themeToggle = document.getElementById('theme-toggle');
const newChatBtn = document.getElementById('new-chat-btn');
const sidebar = document.getElementById('sidebar');
const sidebarOverlay = document.getElementById('sidebar-overlay');
const toggleSidebar = document.getElementById('toggle-sidebar');
const closeBuyModal = document.getElementById('close-buy-modal');
const buyTokensBtn = document.getElementById('buy-tokens-btn');
const priceLoader = document.getElementById('price-loader');
const livePiPrice = document.getElementById('live-pi-price');

function initPiSdk() {
    if (!window.Pi) {
        console.error('Pi SDK not loaded');
        return;
    }

    try {
        window.Pi.init({ version: '2.0', sandbox: false });
        console.log('✅ Pi SDK initialized');
    } catch (error) {
        console.error('Pi init error:', error);
    }
}

const onIncompletePaymentFound = (payment) => {
    console.log('Incomplete payment found:', payment);
    alert('يوجد دفع سابق غير مكتمل. راجع لوحة تحكم التطبيق لمعالجته.');
};

async function loginWithPi() {
    if (!window.Pi) {
        alert('يجب فتح التطبيق من داخل Pi Browser والتأكد من تحميل Pi SDK.');
        return;
    }

    setButtonLoading(piLoginBtn, true, 'جاري الاتصال...');

    try {
        const auth = await window.Pi.authenticate(['username', 'payments'], onIncompletePaymentFound);

        if (!auth || !auth.user) {
            throw new Error('لم يتم استلام بيانات المستخدم من Pi.');
        }

        piUser = auth.user;
        usernameDisplay.textContent = '@' + piUser.username;

        const savedTokens = localStorage.getItem(`tokens_${piUser.username}`);
        tokens = savedTokens === null ? 5 : Number.parseInt(savedTokens, 10);
        if (Number.isNaN(tokens)) tokens = 5;

        updateTokenDisplay();
        loginModal.style.display = 'none';
        addMessage('bot', `أهلاً @${piUser.username} 👋\nتم تسجيل الدخول بنجاح. رصيدك الحالي ${tokens} توكن.`);
    } catch (error) {
        console.error('Pi login error:', error);
        alert('فشل تسجيل الدخول. افتح الموقع من Pi Browser وتأكد أن رابط التطبيق مضبوط في Pi Developer Portal.\n\n' + (error.message || error));
    } finally {
        setButtonLoading(piLoginBtn, false, '<i class="fab fa-pi"></i> تسجيل الدخول بـ Pi');
    }
}

function updateTokenDisplay() {
    tokenCountElement.textContent = tokens;
    if (piUser) {
        localStorage.setItem(`tokens_${piUser.username}`, String(tokens));
    }
}

function removeWelcomePanel() {
    const welcome = chatBox.querySelector('.welcome-panel');
    if (welcome) welcome.remove();
}

function addMessage(role, content, options = {}) {
    removeWelcomePanel();

    const wrapper = document.createElement('article');
    wrapper.className = `message ${role === 'user' ? 'user-message' : 'bot-message'}`;

    const avatar = document.createElement('div');
    avatar.className = 'avatar';
    avatar.innerHTML = role === 'user' ? '<i class="fa-solid fa-user"></i>' : '<i class="fa-solid fa-robot"></i>';

    const text = document.createElement('div');
    text.className = 'text';

    if (options.rawHtml) {
        text.innerHTML = content;
    } else if (role === 'bot' && window.marked) {
        text.innerHTML = marked.parse(content || '');
        text.querySelectorAll('pre code').forEach((block) => {
            if (window.hljs) hljs.highlightElement(block);
        });
    } else {
        text.textContent = content;
    }

    wrapper.appendChild(avatar);
    wrapper.appendChild(text);
    chatBox.appendChild(wrapper);
    chatBox.scrollTop = chatBox.scrollHeight;
    return wrapper;
}

function getBotReplyFromResponse(data) {
    if (typeof data === 'string') return data;
    return data.reply || data.message || data.answer || data.content || data.text || 'تم استلام رد فارغ من السيرفر.';
}

async function sendMessage() {
    const message = userInput.value.trim();
    if (!message || isSending) return;

    if (!piUser) {
        alert('سجّل الدخول بـ Pi أولاً.');
        return;
    }

    if (tokens <= 0) {
        openBuyModal();
        return;
    }

    isSending = true;
    sendBtn.disabled = true;

    addMessage('user', message);
    userInput.value = '';
    resizeTextarea();

    const typing = addMessage('bot', '<span class="typing"><i></i><i></i><i></i></span>', { rawHtml: true });

    try {
        const response = await fetch(API_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                message,
                username: piUser.username,
                pi_uid: piUser.uid || piUser.id || null
            })
        });

        if (!response.ok) {
            throw new Error(`Server error ${response.status}`);
        }

        const contentType = response.headers.get('content-type') || '';
        const data = contentType.includes('application/json') ? await response.json() : await response.text();
        const reply = getBotReplyFromResponse(data);

        typing.remove();
        addMessage('bot', reply);

        tokens -= 1;
        updateTokenDisplay();
    } catch (error) {
        console.error('Send message error:', error);
        typing.remove();
        addMessage('bot', 'حدث خطأ أثناء الإرسال. تأكد أن API `/api/chat` يعمل ويرجع JSON مثل: `{ "reply": "..." }`.');
    } finally {
        isSending = false;
        sendBtn.disabled = false;
        userInput.focus();
    }
}

async function openBuyModal() {
    buyModal.style.display = 'flex';
    priceLoader.style.display = 'block';
    livePiPrice.style.display = 'none';
    buyTokensBtn.style.display = 'none';

    try {
        const response = await fetch(PI_PRICE_API);
        const data = await response.json();
        currentPiPriceUSD = Number(data.price || data.pi_price || data.usd || 0);

        if (!currentPiPriceUSD) throw new Error('Invalid Pi price');

        costInPi = PRICE_FOR_50_TOKENS_USD / currentPiPriceUSD;
        livePiPrice.textContent = `السعر: ${costInPi.toFixed(4)} Pi`;
    } catch (error) {
        console.warn('Price API failed, using fallback price.', error);
        costInPi = 0.01;
        livePiPrice.textContent = `السعر: ${costInPi.toFixed(4)} Pi`;
    } finally {
        priceLoader.style.display = 'none';
        livePiPrice.style.display = 'block';
        buyTokensBtn.style.display = 'flex';
    }
}

async function buyTokens() {
    if (!window.Pi || !piUser) {
        alert('سجّل الدخول أولاً من Pi Browser.');
        return;
    }

    try {
        setButtonLoading(buyTokensBtn, true, 'جاري فتح الدفع...');

        const paymentData = {
            amount: Number(costInPi.toFixed(4)),
            memo: 'Buy 50 AI Pro tokens',
            metadata: {
                username: piUser.username,
                tokens: 50
            }
        };

        const callbacks = {
            onReadyForServerApproval: (paymentId) => console.log('Ready for approval:', paymentId),
            onReadyForServerCompletion: (paymentId, txid) => console.log('Ready for completion:', paymentId, txid),
            onCancel: (paymentId) => console.log('Payment canceled:', paymentId),
            onError: (error, payment) => console.error('Payment error:', error, payment)
        };

        await window.Pi.createPayment(paymentData, callbacks);

        tokens += 50;
        updateTokenDisplay();
        buyModal.style.display = 'none';
        addMessage('bot', 'تمت إضافة 50 توكن إلى حسابك ✅');
    } catch (error) {
        console.error('Buy tokens error:', error);
        alert('فشل الدفع: ' + (error.message || error));
    } finally {
        setButtonLoading(buyTokensBtn, false, '<i class="fas fa-shopping-cart"></i> شراء 50 توكن');
    }
}

function setButtonLoading(button, loading, html) {
    if (!button) return;
    button.disabled = loading;
    button.style.opacity = loading ? '0.72' : '1';
    button.innerHTML = loading ? `<i class="fas fa-spinner fa-spin"></i> ${html}` : html;
}

function resizeTextarea() {
    userInput.style.height = 'auto';
    userInput.style.height = Math.min(userInput.scrollHeight, 150) + 'px';
}

function toggleTheme() {
    document.body.classList.toggle('dark-mode');
    const isDark = document.body.classList.contains('dark-mode');
    localStorage.setItem('theme', isDark ? 'dark' : 'light');
    themeToggle.innerHTML = isDark ? '<i class="fas fa-sun"></i>' : '<i class="fas fa-moon"></i>';
}

function loadTheme() {
    const savedTheme = localStorage.getItem('theme');
    if (savedTheme === 'dark') document.body.classList.add('dark-mode');
    themeToggle.innerHTML = document.body.classList.contains('dark-mode') ? '<i class="fas fa-sun"></i>' : '<i class="fas fa-moon"></i>';
}

function clearChat() {
    chatBox.innerHTML = `
        <div class="welcome-panel">
            <div class="welcome-icon"><i class="fa-solid fa-wand-magic-sparkles"></i></div>
            <h1>محادثة جديدة ✨</h1>
            <p>اكتب رسالتك واضغط إرسال للبدء.</p>
        </div>
    `;
}

function toggleMobileSidebar() {
    sidebar.classList.toggle('open');
    sidebarOverlay.classList.toggle('show');
}

piLoginBtn.addEventListener('click', loginWithPi);
sendBtn.addEventListener('click', sendMessage);
userInput.addEventListener('input', resizeTextarea);
userInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        sendMessage();
    }
});
themeToggle.addEventListener('click', toggleTheme);
newChatBtn.addEventListener('click', clearChat);
toggleSidebar.addEventListener('click', toggleMobileSidebar);
sidebarOverlay.addEventListener('click', toggleMobileSidebar);
closeBuyModal.addEventListener('click', () => buyModal.style.display = 'none');
buyTokensBtn.addEventListener('click', buyTokens);

loadTheme();
initPiSdk();
