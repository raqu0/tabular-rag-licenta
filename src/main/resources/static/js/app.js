document.addEventListener('DOMContentLoaded', () => {
    // Înregistrăm elementele din interfață
    const themeToggle = document.getElementById('themeToggle');
    const dropZone = document.getElementById('dropZone');
    const csvFile = document.getElementById('csvFile');
    const fileInfo = document.getElementById('fileInfo');
    const fileName = document.getElementById('fileName');
    const fileStatus = document.getElementById('fileStatus');
    const sendBtn = document.getElementById('sendBtn');
    const userInput = document.getElementById('userInput');
    const chatBox = document.getElementById('chatBox');
    const usernameDisplay = document.getElementById('usernameDisplay');
    const logoutBtn = document.getElementById('logoutBtn');

    // Variabilă globală internă - nu mai poate fi modificată din interfață de user
    let currentUsername = 'anonim';

    // NOU: Aducem din backend numele utilizatorului logat real în sesiune
    fetch('/api/user/me')
        .then(res => res.text())
        .then(name => {
            currentUsername = name;
            usernameDisplay.textContent = name; // Îl afișăm frumos în header
        })
        .catch(err => {
            console.error("Eroare la preluarea datelor sesiunii:", err);
            usernameDisplay.textContent = "Eroare sesiune";
        });

    // 1. GESTIONARE MOD ÎNTUNECAT / LUMINOS
    themeToggle.addEventListener('click', () => {
        document.documentElement.classList.toggle('dark');
    });

    // 2. EVENIMENTE UPLOAD (Drag & Drop sau Click)
    dropZone.addEventListener('click', () => csvFile.click());

    dropZone.addEventListener('dragover', (e) => {
        e.preventDefault();
        dropZone.classList.add('border-indigo-500', 'bg-indigo-50/10');
    });

    dropZone.addEventListener('dragleave', () => {
        dropZone.classList.remove('border-indigo-500', 'bg-indigo-50/10');
    });

    dropZone.addEventListener('drop', (e) => {
        e.preventDefault();
        dropZone.classList.remove('border-indigo-500', 'bg-indigo-50/10');
        if (e.dataTransfer.files.length > 0) {
            csvFile.files = e.dataTransfer.files;
            handleFileUpload();
        }
    });

    csvFile.addEventListener('change', handleFileUpload);

    function handleFileUpload() {
        const file = csvFile.files[0];
        if (!file) return;

        fileName.textContent = file.name;
        fileInfo.classList.remove('hidden');
        fileStatus.textContent = "Se procesează hibrid...";
        fileStatus.className = "text-xs text-amber-500 font-medium";

        const formData = new FormData();
        formData.append('file', file);

        // Trimitem fișierul în folderul dedicat utilizatorului curent
        fetch(`/api/upload/${currentUsername}`, {
            method: 'POST',
            body: formData
        })
            .then(res => res.text())
            .then(data => {
                fileStatus.textContent = "Sincronizat corect (SQL + RAG)";
                fileStatus.className = "text-xs text-emerald-500 font-medium";
                appendSystemMessage(`Sistemul a terminat indexarea! Datele au fost izolate complet în spațiul privat al utilizatorului: <strong>${currentUsername}</strong>.`);
            })
            .catch(err => {
                fileStatus.textContent = "Eroare la trimitere";
                fileStatus.className = "text-xs text-rose-500 font-medium";
                console.error(err);
            });
    }

    // 3. COMUNICARE CHAT
    sendBtn.addEventListener('click', sendQuestion);
    userInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') sendQuestion();
    });

    function sendQuestion() {
        const text = userInput.value.trim();
        if (!text) return;

        appendMessage(text, true);
        userInput.value = '';

        const loadingId = addLoadingBubble();

        // Interogăm modelul local pe baza tabelului acestui utilizator
        fetch(`/api/chat/${currentUsername}?intrebare=${encodeURIComponent(text)}`)
            .then(res => res.text())
            .then(reply => {
                document.getElementById(loadingId).remove();
                appendMessage(reply, false);
            })
            .catch(err => {
                document.getElementById(loadingId).remove();
                appendMessage("Eroare de conexiune cu serverul Ollama local.", false);
                console.error(err);
            });
    }

    // NOU: Logica butonului de Logout nativ din Spring Security
    logoutBtn.addEventListener('click', () => {
        // Spring Security cere un apel POST securizat pentru a distruge sesiunea curentă
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = '/logout';
        document.body.appendChild(form);
        form.submit();
    });

    // 4. FUNCȚII AUXILIARE PENTRU INTERFAȚĂ
    function appendMessage(text, isUser) {
        const formattedText = text.replace(/\n/g, '<br>');
        const messageHtml = `
            <div class="flex gap-4 ${isUser ? 'flex-row-reverse' : ''} msg-animate">
                <div class="w-10 h-10 rounded-full ${isUser ? 'bg-indigo-100 text-indigo-600 dark:bg-indigo-500/10 dark:text-indigo-400 border border-indigo-200/50 dark:border-indigo-500/20' : 'bg-indigo-600 text-white shadow-md shadow-indigo-500/10'} flex items-center justify-center shrink-0">
                    <i class="fa-solid ${isUser ? 'fa-user' : 'fa-robot'} text-sm"></i>
                </div>
                <div class="glass ${isUser ? 'bg-indigo-600 text-white rounded-tr-sm shadow-md' : 'bg-slate-100/80 dark:bg-slate-800/40 border border-slate-200/50 dark:border-slate-800/50'} px-5 py-4 rounded-2xl ${isUser ? 'rounded-tr-sm' : 'rounded-tl-sm'} max-w-[80%] leading-relaxed font-light text-[15px]">
                    ${formattedText}
                </div>
            </div>
        `;
        chatBox.insertAdjacentHTML('beforeend', messageHtml);
        chatBox.scrollTop = chatBox.scrollHeight;
    }

    function appendSystemMessage(text) {
        const html = `
            <div class="flex justify-center my-2 msg-animate">
                <div class="bg-slate-100 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700/60 text-xs px-4 py-2 rounded-xl text-slate-500 dark:text-slate-400 font-medium">
                    <i class="fa-solid fa-circle-info mr-1.5 text-indigo-500"></i>${text}
                </div>
            </div>
        `;
        chatBox.insertAdjacentHTML('beforeend', html);
        chatBox.scrollTop = chatBox.scrollHeight;
    }

    function addLoadingBubble() {
        const id = 'loading-' + Date.now();
        const bubbleHtml = `
            <div id="${id}" class="flex gap-4 msg-animate">
                <div class="w-10 h-10 rounded-full bg-indigo-100 dark:bg-indigo-500/10 border border-indigo-200/50 dark:border-indigo-500/20 text-indigo-600 dark:text-indigo-400 flex items-center justify-center shrink-0">
                    <i class="fa-solid fa-robot text-sm"></i>
                </div>
                <div class="glass bg-slate-100/80 dark:bg-slate-800/40 border border-slate-200/50 dark:border-slate-800/50 px-6 py-5 rounded-2xl rounded-tl-sm flex items-center">
                    <div class="dots-loader"><div></div><div></div><div></div></div>
                </div>
            </div>
        `;
        chatBox.insertAdjacentHTML('beforeend', bubbleHtml);
        chatBox.scrollTop = chatBox.scrollHeight;
        return id;
    }
});