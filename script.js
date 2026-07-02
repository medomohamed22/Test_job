// ==========================================
// 1. إعدادات Pi Network (تسجيل الدخول)
// ==========================================

// تهيئة Pi SDK (sandbox: false للإنتاج)
Pi.init({ version: "2.0", sandbox: false });

const loginScreen = document.getElementById('login-screen');
const mainChatContainer = document.getElementById('main-chat-container');
const piLoginBtn = document.getElementById('pi-login-btn');
const usernameDisplay = document.getElementById('username-display');
const loginError = document.getElementById('login-error');

// دالة لمعالجة الدفع غير المكتمل (مطلوبة بواسطة Pi SDK)
function onIncompletePaymentFound(payment) {
    console.log("تم العثور على عملية دفع غير مكتملة:", payment);
}

// دالة تسجيل الدخول
async function authenticatePiUser() {
    piLoginBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> جاري تسجيل الدخول...';
    piLoginBtn.disabled = true;
    loginError.style.display = 'none';

    try {
        // طلب صلاحية قراءة اسم المستخدم
        const scopes = ['username'];
        const authResults = await Pi.authenticate(scopes, onIncompletePaymentFound);
        
        // استخراج اسم المستخدم
        const piUsername = authResults.user.username;
        
        // الانتقال إلى الشات
        loginScreen.style.display = 'none';
        mainChatContainer.style.display = 'flex';
        
        // عرض اسم المستخدم في الرأس
        usernameDisplay.innerHTML = `<i class="fas fa-user-circle"></i> @${piUsername}`;

        // يمكننا إضافة رسالة ترحيبية من البوت باسم المستخدم
        conversationHistory.push({ 
            role: "user", 
            parts: [{ text: `اسمي هو ${piUsername}، استخدم هذا الاسم عند التحدث معي إذا احتجت لذلك.` }] 
        });
        conversationHistory.push({ 
            role: "model", 
            parts: [{ text: `أهلاً بك يا ${piUsername}! كيف يمكنني مساعدتك اليوم؟` }] 
        });

    } catch (error) {
        console.error("خطأ في تسجيل دخول Pi:", error);
        piLoginBtn.innerHTML = '<i class="fab fa-product-hunt"></i> تسجيل الدخول بواسطة Pi';
        piLoginBtn.disabled = false;
        loginError.innerText = "فشل تسجيل الدخول. تأكد من فتح التطبيق عبر متصفح Pi Browser.";
        loginError.style.display = 'block';
    }
}

// ربط زر الدخول بالدالة
piLoginBtn.addEventListener('click', authenticatePiUser);


// ==========================================
// 2. إعدادات الذكاء الاصطناعي والمحادثة
// ==========================================

const API_KEY = 'AQ.Ab8RN6LydL4Q2G2FBK0GajAcQGRKvJbjvRS88aW7F1vikHO3pg'; // ضع مفتاح Gemini هنا
const API_URL = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse&key=${API_KEY}`;

marked.setOptions({
    highlight: function(code, lang) {
        if (lang && hljs.getLanguage(lang)) { return hljs.highlight(code, { language: lang }).value; }
        return hljs.highlightAuto(code).value;
    }
});

const chatBox = document.getElementById('chat-box');
const userInput = document.getElementById('user-input');
const sendBtn = document.getElementById('send-btn');
const stopBtn = document.getElementById('stop-btn');
const suggestions = document.getElementById('suggestions');
const personaSelect = document.getElementById('persona-select');

let conversationHistory = []; 
let currentAbortController = null; 

// الوضع الليلي
const themeToggleBtn = document.getElementById('theme-toggle');
if (localStorage.getItem('theme') === 'dark') {
    document.body.classList.add('dark-mode');
    themeToggleBtn.innerHTML = '<i class="fas fa-sun"></i>';
}
themeToggleBtn.addEventListener('click', () => {
    document.body.classList.toggle('dark-mode');
    if (document.body.classList.contains('dark-mode')) {
        localStorage.setItem('theme', 'dark');
        themeToggleBtn.innerHTML = '<i class="fas fa-sun"></i>';
    } else {
        localStorage.setItem('theme', 'light');
        themeToggleBtn.innerHTML = '<i class="fas fa-moon"></i>';
    }
});

function createMessageElement(sender, initialText = "") {
    const messageDiv = document.createElement('div');
    messageDiv.classList.add('message');
    
    if (sender === 'user') {
        messageDiv.classList.add('user-message');
        messageDiv.innerHTML = `<div class="avatar"><i class="fas fa-user"></i></div><div class="text">${escapeHTML(initialText)}</div>`;
    } else {
        messageDiv.classList.add('bot-message');
        messageDiv.innerHTML = `<div class="avatar"><i class="fas fa-robot"></i></div><div class="text markdown-body"></div>`;
    }
    
    chatBox.appendChild(messageDiv);
    chatBox.scrollTop = chatBox.scrollHeight;
    return messageDiv.querySelector('.text');
}

async function fetchAIResponseStream(userText) {
    const botTextElement = createMessageElement('bot');
    botTextElement.innerHTML = '<span style="color:var(--text-muted);"><i class="fas fa-circle-notch fa-spin"></i> جاري التفكير...</span>';
    
    conversationHistory.push({ role: "user", parts: [{ text: userText }] });
    const systemInstruction = personaSelect.value;

    let fullResponseText = "";
    currentAbortController = new AbortController();
    stopBtn.style.display = 'flex'; 
    sendBtn.classList.add('pulse'); 

    try {
        const response = await fetch(API_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            signal: currentAbortController.signal,
            body: JSON.stringify({
                systemInstruction: { parts: [{ text: systemInstruction }] },
                contents: conversationHistory
            })
        });

        if (!response.ok) throw new Error("API Error");

        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        botTextElement.innerHTML = ""; 

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            
            const chunk = decoder.decode(value, { stream: true });
            const lines = chunk.split('\n');
            
            for (const line of lines) {
                if (line.startsWith('data: ')) {
                    const dataStr = line.substring(6);
                    if (dataStr === '[DONE]') continue;
                    try {
                        const dataObj = JSON.parse(dataStr);
                        if (dataObj.candidates && dataObj.candidates[0].content.parts[0].text) {
                            fullResponseText += dataObj.candidates[0].content.parts[0].text;
                            botTextElement.innerHTML = marked.parse(fullResponseText);
                            chatBox.scrollTop = chatBox.scrollHeight;
                        }
                    } catch (e) {}
                }
            }
        }
        
        addCopyButtons(botTextElement);
        conversationHistory.push({ role: "model", parts: [{ text: fullResponseText }] });

    } catch (error) {
        if (error.name === 'AbortError') {
            botTextElement.innerHTML += '<br><span style="color:#ef4444; font-size:12px;">[تم إيقاف التوليد بواسطة المستخدم 🛑]</span>';
            conversationHistory.push({ role: "model", parts: [{ text: fullResponseText }] });
        } else {
            botTextElement.innerHTML = "⚠️ عذراً، حدث خطأ في الاتصال.";
            conversationHistory.pop(); 
        }
    } finally {
        stopBtn.style.display = 'none'; 
        sendBtn.classList.remove('pulse');
        currentAbortController = null;
    }
}

function addCopyButtons(container) {
    const codeBlocks = container.querySelectorAll('pre');
    codeBlocks.forEach((block) => {
        if(block.querySelector('.copy-btn')) return;
        const button = document.createElement('button');
        button.className = 'copy-btn';
        button.innerHTML = '<i class="far fa-copy"></i> نسخ';
        button.addEventListener('click', () => {
            const codeText = block.querySelector('code').innerText;
            navigator.clipboard.writeText(codeText).then(() => {
                button.innerHTML = '<i class="fas fa-check"></i> تم';
                button.style.backgroundColor = '#10a37f';
                setTimeout(() => {
                    button.innerHTML = '<i class="far fa-copy"></i> نسخ';
                    button.style.backgroundColor = 'rgba(255, 255, 255, 0.1)';
                }, 2000);
            });
        });
        block.appendChild(button);
    });
}

function handleSend() {
    const text = userInput.value.trim();
    if (text === '') return;
    if (suggestions) suggestions.style.display = 'none'; 

    createMessageElement('user', text);
    userInput.value = ''; 
    userInput.style.height = 'auto'; 
    fetchAIResponseStream(text);
}

function sendSuggestion(text) {
    userInput.value = text;
    handleSend();
}

stopBtn.addEventListener('click', () => {
    if (currentAbortController) currentAbortController.abort();
});

document.getElementById('export-btn').addEventListener('click', () => {
    if(conversationHistory.length <= 2) return alert("المحادثة فارغة!"); // نتجاهل رسائل التهيئة
    let exportText = "--- سجل محادثة المساعد الذكي ---\n\n";
    conversationHistory.forEach(msg => {
        // تجاهل رسائل التهيئة المخفية الخاصة باسم المستخدم
        if(msg.parts[0].text.includes('اسمي هو') && msg.role === 'user') return;
        if(msg.parts[0].text.includes('أهلاً بك يا') && msg.role === 'model') return;

        const role = msg.role === 'user' ? "أنت" : "المساعد الذكي";
        exportText += `${role}:\n${msg.parts[0].text}\n\n------------------------\n\n`;
    });
    const blob = new Blob([exportText], { type: "text/plain;charset=utf-8" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = "Chat_Export.txt";
    link.click();
});

sendBtn.addEventListener('click', handleSend);
userInput.addEventListener('keydown', function(e) {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); }
});
userInput.addEventListener('input', function() {
    this.style.height = 'auto';
    this.style.height = (this.scrollHeight) + 'px';
});

function escapeHTML(str) { return str.replace(/[&<>'"]/g, tag => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[tag] || tag)); }
