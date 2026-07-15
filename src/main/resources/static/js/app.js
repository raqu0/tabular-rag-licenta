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
    const exportButtons = document.getElementById('exportButtons');
    const exportExcelBtn = document.getElementById('exportExcelBtn');
    const exportPdfBtn = document.getElementById('exportPdfBtn');
    const chartBtn = document.getElementById('chartBtn');
    const activeFilesBox = document.getElementById('activeFilesBox');
    const activeFilesList = document.getElementById('activeFilesList');
    const addSecondFileBtn = document.getElementById('addSecondFileBtn');

    // Variabilă globală internă - nu mai poate fi modificată din interfață de user
    let currentUsername = 'anonim';

    // Fișierele active în sesiunea curentă (max 2). Fiecare element: { id, fileName }
    let activeFiles = [];
    // Reține dacă următorul upload e pentru "al 2-lea fișier" (JOIN) sau înlocuiește totul
    let addingSecondFile = false;

// Aducem din backend numele utilizatorului logat real în sesiune
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

    // Verificăm rolul utilizatorului - dacă e ADMIN, afișăm butonul de Panou Admin
    fetch('/api/user/role')
        .then(res => res.text())
        .then(role => {
            if (role.trim().toUpperCase() === 'ADMIN') {
                const adminBtn = document.getElementById('adminBtn');
                if (adminBtn) {
                    adminBtn.classList.remove('hidden');
                    adminBtn.classList.add('flex');
                }
            }
        })
        .catch(err => console.error("Eroare la preluarea rolului:", err));

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

    // Butonul "Adaugă al 2-lea fișier" - declanșează un upload care se adaugă, nu înlocuiește
    addSecondFileBtn.addEventListener('click', () => {
        addingSecondFile = true;
        csvFile.click();
    });

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
            .then(res => res.json())
            .then(data => {
                fileStatus.textContent = "Sincronizat corect (SQL + RAG)";
                fileStatus.className = "text-xs text-emerald-500 font-medium";

                // data conține { id, fileName }
                if (addingSecondFile) {
                    // Adăugăm al 2-lea fișier (dacă nu e deja prezent)
                    if (activeFiles.length < 2 && !activeFiles.some(f => f.id === data.id)) {
                        activeFiles.push({ id: data.id, fileName: data.fileName });
                    }
                    addingSecondFile = false;
                } else {
                    // Upload normal (primul fișier) - resetăm și pornim cu acest fișier
                    activeFiles = [{ id: data.id, fileName: data.fileName }];
                }

                renderActiveFiles();
                appendSystemMessage(`Fișierul <strong>${data.fileName}</strong> a fost indexat și este acum activ.`);

                // Resetăm input-ul ca să putem reîncărca același fișier dacă vrem
                csvFile.value = '';
            })
            .catch(err => {
                fileStatus.textContent = "Eroare la trimitere";
                fileStatus.className = "text-xs text-rose-500 font-medium";
                addingSecondFile = false;
                console.error(err);
            });
    }

    // Afișează fișierele active (1 sau 2) cu opțiune de eliminare
    function renderActiveFiles() {
        activeFilesList.innerHTML = '';

        if (activeFiles.length === 0) {
            activeFilesBox.classList.add('hidden');
            return;
        }

        activeFilesBox.classList.remove('hidden');

        activeFiles.forEach((f, index) => {
            const item = document.createElement('div');
            item.className = 'flex items-center gap-2 p-2 rounded-lg bg-indigo-50/50 dark:bg-indigo-500/10 border border-indigo-100 dark:border-indigo-500/20';
            item.innerHTML = `
                <i class="fa-solid fa-file-csv text-indigo-500 text-sm"></i>
                <span class="text-xs font-medium truncate flex-1">${f.fileName}</span>
                <button class="remove-file-btn text-rose-500 hover:text-rose-400 transition-colors" data-index="${index}" title="Elimină">
                    <i class="fa-solid fa-xmark"></i>
                </button>
            `;
            activeFilesList.appendChild(item);
        });

        // Butoanele de eliminare
        activeFilesList.querySelectorAll('.remove-file-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const idx = parseInt(btn.getAttribute('data-index'));
                activeFiles.splice(idx, 1);
                renderActiveFiles();
            });
        });

        // Afișăm butonul "Adaugă al 2-lea fișier" doar dacă avem exact 1 fișier
        if (activeFiles.length === 1) {
            addSecondFileBtn.classList.remove('hidden');
        } else {
            addSecondFileBtn.classList.add('hidden');
        }
    }

    // Returnează ID-urile fișierelor active
    function getActiveFileIds() {
        return activeFiles.map(f => f.id);
    }

    // 3. COMUNICARE CHAT
    sendBtn.addEventListener('click', sendQuestion);
    userInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') sendQuestion();
    });

    function sendQuestion() {
        const text = userInput.value.trim();
        if (!text) return;

        // Trebuie să existe cel puțin un fișier activ
        if (activeFiles.length === 0) {
            appendSystemMessage("Încarcă mai întâi un fișier CSV pentru a putea pune întrebări.");
            return;
        }

        appendMessage(text, true);
        userInput.value = '';

        const loadingId = addLoadingBubble();

        // Construim URL-ul cu ID-urile fișierelor active
        const activeIds = getActiveFileIds();
        let url = `/api/chat/${currentUsername}?intrebare=${encodeURIComponent(text)}`;
        activeIds.forEach(id => {
            url += `&fileIds=${encodeURIComponent(id)}`;
        });

        // Interogăm modelul local pe baza tabelului acestui utilizator
        fetch(url)
            .then(res => res.text())
            .then(reply => {
                document.getElementById(loadingId).remove();
                appendMessage(reply, false);
                // Afișăm butoanele de export, deoarece avem acum un rezultat disponibil
                exportButtons.classList.remove('hidden');
            })
            .catch(err => {
                document.getElementById(loadingId).remove();
                appendMessage("Eroare de conexiune cu serverul Ollama local.", false);
                console.error(err);
            });
    }

    // 3.1 EXPORT EXCEL / PDF
    exportExcelBtn.addEventListener('click', () => {
        window.location.href = `/api/export/excel/${currentUsername}`;
    });

    exportPdfBtn.addEventListener('click', () => {
        window.location.href = `/api/export/pdf/${currentUsername}`;
    });

    // 3.2 GRAFIC - preluăm datele și deschidem modalul
    chartBtn.addEventListener('click', () => {
        fetch(`/api/export/data/${currentUsername}`)
            .then(res => {
                if (!res.ok) throw new Error("Nu există date pentru grafic.");
                return res.json();
            })
            .then(data => {
                openChartModal(data);
            })
            .catch(err => {
                alert("Nu există date disponibile pentru grafic. Pune mai întâi o întrebare.");
                console.error(err);
            });
    });

    // Logica butonului de Logout nativ din Spring Security
    logoutBtn.addEventListener('click', () => {
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

// --- LOGICA GRAFICELOR (Chart.js) ---
let barChartInstance = null;
let pieChartInstance = null;

window.openChartModal = function(data) {
    const modal = document.getElementById('chartModal');
    const hint = document.getElementById('chartHint');

    if (!data || data.length === 0) {
        alert("Nu există date pentru grafic.");
        return;
    }

    // Detectăm automat coloanele: una text (categorie) + una numerică (valoare)
    const columns = Object.keys(data[0]);
    let labelColumn = null;
    let valueColumn = null;

    for (const col of columns) {
        const sampleValue = data[0][col];
        const isNumeric = !isNaN(parseFloat(sampleValue)) && isFinite(sampleValue);
        if (isNumeric && valueColumn === null) {
            valueColumn = col;
        } else if (!isNumeric && labelColumn === null) {
            labelColumn = col;
        }
    }

    // Dacă nu există o coloană text, folosim indexul rândului ca etichetă
    // Dacă nu există coloană numerică, nu putem face grafic
    if (valueColumn === null) {
        alert("Rezultatul nu conține nicio coloană numerică, deci nu se poate genera un grafic.");
        return;
    }
    if (labelColumn === null) {
        labelColumn = columns[0];
    }

    const labels = data.map((row, i) => {
        const v = row[labelColumn];
        return v !== null && v !== undefined ? String(v) : `Rând ${i + 1}`;
    });
    const values = data.map(row => parseFloat(row[valueColumn]) || 0);

    hint.textContent = `Reprezentare: "${valueColumn}" în funcție de "${labelColumn}" (${data.length} înregistrări).`;

    // Paletă de culori
    const palette = [
        '#6366f1', '#10b981', '#f43f5e', '#f59e0b', '#3b82f6',
        '#8b5cf6', '#ec4899', '#14b8a6', '#eab308', '#06b6d4',
        '#a855f7', '#22c55e', '#ef4444', '#0ea5e9', '#d946ef'
    ];
    const colors = labels.map((_, i) => palette[i % palette.length]);

    // Distrugem instanțele vechi ca să nu se suprapună
    if (barChartInstance) barChartInstance.destroy();
    if (pieChartInstance) pieChartInstance.destroy();

    const barCtx = document.getElementById('barChart').getContext('2d');
    barChartInstance = new Chart(barCtx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                label: valueColumn,
                data: values,
                backgroundColor: colors,
                borderRadius: 6
            }]
        },
        options: {
            responsive: true,
            plugins: { legend: { display: false } },
            scales: { y: { beginAtZero: true } }
        }
    });

    const pieCtx = document.getElementById('pieChart').getContext('2d');
    pieChartInstance = new Chart(pieCtx, {
        type: 'pie',
        data: {
            labels: labels,
            datasets: [{
                label: valueColumn,
                data: values,
                backgroundColor: colors
            }]
        },
        options: {
            responsive: true,
            plugins: { legend: { position: 'bottom' } }
        }
    });

    modal.classList.remove('hidden');
    modal.classList.add('flex');
};

window.closeChartModal = function() {
    const modal = document.getElementById('chartModal');
    modal.classList.add('hidden');
    modal.classList.remove('flex');
};

// --- FUNCȚII GLOBALE PENTRU MODALUL DE SETĂRI ---
// Acestea trebuie să fie în afara DOMContentLoaded pentru a putea fi apelate direct din HTML (onclick="")

window.openSettingsModal = function() {
    const modal = document.getElementById('settingsModal');
    modal.classList.remove('hidden');
    modal.classList.add('flex');
};

window.closeSettingsModal = function() {
    const modal = document.getElementById('settingsModal');
    modal.classList.add('hidden');
    modal.classList.remove('flex');

    // Curățăm câmpurile la închidere pentru a nu rămâne date scrise
    document.getElementById('updateUser').value = '';
    document.getElementById('updateEmail').value = '';
    document.getElementById('updatePass').value = '';
};

window.saveSettings = async function() {
    const u = document.getElementById('updateUser').value.trim();
    const e = document.getElementById('updateEmail').value.trim();
    const p = document.getElementById('updatePass').value.trim();

    if(!u && !e && !p) {
        alert("Nu ai introdus nicio modificare.");
        return;
    }

    const btn = document.getElementById('saveSettingsBtn');
    const originalText = btn.innerHTML;
    btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin mr-2"></i> Se salvează...';
    btn.disabled = true;

    try {
        // Construim payload-ul pe care îl așteaptă Controllerul tău din Spring
        const formData = new URLSearchParams();
        if(u) formData.append('newUsername', u);
        if(e) formData.append('newEmail', e);
        if(p) formData.append('newPassword', p);

        const res = await fetch('/api/user/update', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: formData.toString()
        });

        const msg = await res.text();

        if (msg === "Succes") {
            alert("Datele au fost actualizate cu succes!");
            closeSettingsModal();
            location.reload(); // Reîmprospătăm pagina ca să aducă noile date
        } else if (msg === "Succes_Relogin") {
            alert("Datele critice au fost modificate! Din motive de securitate, te rugăm să te conectezi din nou.");
            // Executăm logica de deconectare
            const form = document.createElement('form');
            form.method = 'POST';
            form.action = '/logout';
            document.body.appendChild(form);
            form.submit();
        } else {
            alert(msg); // Afișăm eventualele mesaje de eroare din backend
        }
    } catch(err) {
        alert("Eroare de conexiune cu serverul.");
        console.error(err);
    } finally {
        btn.innerHTML = originalText;
        btn.disabled = false;
    }
};