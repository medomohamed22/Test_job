// --- الاتصال بملف Vercel المخفي بدلاً من جوجل مباشرة ---
const API_URL = '/api/chat'; 

marked.setOptions({
    highlight: function(code, lang) {
        return (lang && hljs.getLanguage(lang)) ? hljs.highlight(code, { language: lang }).value : hljs.highlightAuto(code).value;
    }
});

let chatHistory = [];
let abortController = null;
let currentImageBase64 = null;

// --- العناصر ---
const chatBox = document.getElementById('chat-box');
const userInput = document.getElementById('user-input');
const sendBtn = document.getElementById('send-btn');
const stopBtn = document.getElementById('stop-btn');
const themeToggle = document.getElementById('theme-toggle');
const sidebar = document.getElementById('sidebar');
const toggleSidebarBtn = document.getElementById('toggle-sidebar');
const closeSidebarBtn = document.getElementById('close-sidebar-mobile');
const sidebarOverlay = document.getElementById('sidebar-overlay');
const personaSelect = document.getElementById('persona-select');
const fileUpload = document.getElementById('file-upload');
const imagePreviewContainer = document.getElementById('image-preview-container');
const imagePreview = document.getElementById('image-preview');
const removeImageBtn = document.getElementById('remove-image');
const suggestions = document.getElementById('suggestions');

// --- الوضع الليلي ---
if (localStorage.getItem('theme') === 'dark') document.body.classList.add('dark-mode');
themeToggle.addEventListener('click', () => {
    document.body.classList.toggle('dark-mode');
    localStorage.setItem('theme', document.body.classList.contains('dark-mode') ? 'dark' : 'light');
});

// --- فتح وغلق القائمة في الهواتف ---
function openSidebar() {
    sidebar.classList.add('mobile-open');
    sidebar.classList.remove('hidden');
    sidebarOverlay.classList.add('active');
}
function closeSidebar() {
    sidebar.classList.remove('mobile-open');
    if(window.innerWidth > 768) sidebar.classList.add('hidden');
    sidebarOverlay.classList.remove('active');
}
toggleSidebarBtn.addEventListener('click', () => {
    if(window.innerWidth <= 768) openSidebar();
    else sidebar.classList.toggle('hidden');
});
closeSidebarBtn.addEventListener('click', closeSidebar);
sidebarOverlay.addEventListener('click', closeSidebar);

// محادثة جديدة
document.getElementById('new-chat-btn').addEventListener('click', () => {
    chatBox.innerHTML = '';
    chatBox.appendChild(suggestions);
    suggestions.style.display = 'block';
    chatHistory = [];
    if(window.innerWidth <= 768) closeSidebar();
});

// --- رفع الصور ---
fileUpload.addEventListener('change', function(e) {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (event) => {
        currentImageBase64 = event.target.result.split(',')[1];
        imagePreview.src = event.target.result;
        imagePreviewContainer.style.display = 'inline-block';
    };
    reader.readAsDataURL(file);
});
removeImageBtn.addEventListener('click', () => {
    currentImageBase64 = null;
    imagePreviewContainer.style.display = 'none';
    fileUpload.value = '';
});

// إنشاء عنصر رسالة
function createMessageElement(sender, initialText = "", imgSrc = null) {
    const messageDiv = document.createElement('div');
    messageDiv.classList.add('message', sender === 'user' ? 'user-message' : 'bot-message');
    let imgHTML = imgSrc ? `<img src="${imgSrc}" class="user-image">` : '';
    
    if (sender === 'user') {
        messageDiv.innerHTML = `<div class="avatar"><i class="fas fa-user"></i></div><div class="text">${escapeHTML(initialText)} ${imgHTML}</div>`;
    } else {
        messageDiv.innerHTML = `<div class="avatar"><i class="fas fa-robot"></i></div><div class="text markdown-body"><div class="content"></div></div>`;
    }
    chatBox.appendChild(messageDiv);
    chatBox.scrollTop = chatBox.scrollHeight;
    return messageDiv.querySelector(sender === 'user' ? '.text' : '.content');
}

// الاتصال بالخادم (Vercel Backend)
async function fetchAIResponseStream(userText) {
    suggestions.style.display = 'none';
    const botTextElement = createMessageElement('bot');
    botTextElement.innerHTML = '<span style="color:var(--text-muted);"><i class="fas fa-circle-notch fa-spin"></i> جاري التفكير...</span>';
    
    sendBtn.style.display = 'none';
    stopBtn.style.display = 'block';
    
    let userParts = [{ text: userText }];
    if (currentImageBase64) userParts.push({ inlineData: { mimeType: "image/jpeg", data: currentImageBase64 } });
    
    chatHistory.push({ role: "user", parts: userParts });

    let systemInstructionText = "أنت مساعد ذكي ومفيد.";
    if (personaSelect.value === 'programmer') systemInstructionText = "أنت مبرمج خبير.";
    if (personaSelect.value === 'teacher') systemInstructionText = "أنت معلم.";

    abortController = new AbortController();
    let fullResponseText = "";

    try {
        const response = await fetch(API_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ systemInstruction: { parts: [{ text: systemInstructionText }] }, contents: chatHistory }),
            signal: abortController.signal
        });

        if (!response.ok) throw new Error("خطأ من السيرفر");

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
        
        chatHistory.push({ role: "model", parts: [{ text: fullResponseText }] });
        addCopyButtons(botTextElement.parentNode);

    } catch (error) {
        if (error.name === 'AbortError') botTextElement.innerHTML = marked.parse(fullResponseText + " \n\n*[تم إيقاف التوليد]*");
        else botTextElement.innerHTML = "⚠️ فشل الاتصال بالخادم. تأكد من إعدادات Vercel.";
    } finally {
        sendBtn.style.display = 'block';
        stopBtn.style.display = 'none';
        currentImageBase64 = null;
        imagePreviewContainer.style.display = 'none';
        fileUpload.value = '';
    }
}

stopBtn.addEventListener('click', () => { if (abortController) abortController.abort(); });

function addCopyButtons(container) {
    container.querySelectorAll('pre').forEach((block) => {
        if(block.querySelector('.copy-btn')) return;
        const button = document.createElement('button');
        button.className = 'copy-btn';
        button.innerHTML = 'نسخ';
        button.addEventListener('click', () => {
            navigator.clipboard.writeText(block.querySelector('code').innerText).then(() => {
                button.innerHTML = 'تم'; button.style.background = '#10a37f';
                setTimeout(() => { button.innerHTML = 'نسخ'; button.style.background = 'rgba(255, 255, 255, 0.1)'; }, 2000);
            });
        });
        block.appendChild(button);
    });
}

function handleSend(textOverride = null) {
    const text = textOverride || userInput.value.trim();
    if (text === '' && !currentImageBase64) return;
    createMessageElement('user', text, currentImageBase64 ? imagePreview.src : null);
    userInput.value = ''; userInput.style.height = 'auto'; 
    fetchAIResponseStream(text);
}

sendBtn.addEventListener('click', () => handleSend());
userInput.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); }});
userInput.addEventListener('input', function() { this.style.height = 'auto'; this.style.height = (this.scrollHeight) + 'px'; });
document.querySelectorAll('.sugg-btn').forEach(btn => { btn.addEventListener('click', () => handleSend(btn.innerText)); });

function escapeHTML(str) { return str.replace(/[&<>'"]/g, tag => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[tag])); }
