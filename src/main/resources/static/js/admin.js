document.addEventListener('DOMContentLoaded', () => {
    loadStats();
    loadUsers();
    loadFiles();
    loadQueries();
    loadActions();
    loadSessions();
});

// Încarcă statisticile globale
function loadStats() {
    fetch('/api/admin/stats')
        .then(res => res.json())
        .then(stats => {
            document.getElementById('statUseri').textContent = stats.totalUseri;
            document.getElementById('statFisiere').textContent = stats.totalFisiere;
            document.getElementById('statInterogari').textContent = stats.totalInterogari;
        })
        .catch(err => console.error("Eroare statistici:", err));
}

// Încarcă lista de utilizatori + populează tabelul și graficul
function loadUsers() {
    fetch('/api/admin/users')
        .then(res => res.json())
        .then(users => {
            const tbody = document.getElementById('usersTableBody');
            tbody.innerHTML = '';

            const labels = [];
            const values = [];

            users.forEach(u => {
                labels.push(u.username);
                values.push(u.nrInterogari);

                const tr = document.createElement('tr');
                tr.className = 'border-b border-slate-100 dark:border-slate-800/50';
                tr.innerHTML = `
                    <td class="py-3 px-2 font-medium">${u.username}</td>
                    <td class="py-3 px-2 text-slate-500 dark:text-slate-400">${u.email}</td>
                    <td class="py-3 px-2">
                        <span class="text-xs font-semibold px-2 py-1 rounded-lg ${u.role === 'ADMIN' ? 'bg-indigo-100 text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-400' : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400'}">${u.role}</span>
                    </td>
                    <td class="py-3 px-2 text-center">${u.nrFisiere}</td>
                    <td class="py-3 px-2 text-center">${u.nrInterogari}</td>
                    <td class="py-3 px-2 text-center">
                        <button onclick="deleteUser(${u.id}, '${u.username}')" class="text-rose-500 hover:text-rose-400 transition-colors" title="Șterge utilizator">
                            <i class="fa-solid fa-trash"></i>
                        </button>
                    </td>
                `;
                tbody.appendChild(tr);
            });

            renderQueriesChart(labels, values);
        })
        .catch(err => console.error("Eroare utilizatori:", err));
}

// Încarcă lista de fișiere
function loadFiles() {
    fetch('/api/admin/files')
        .then(res => res.json())
        .then(files => {
            const tbody = document.getElementById('filesTableBody');
            tbody.innerHTML = '';

            files.forEach(f => {
                const tr = document.createElement('tr');
                tr.className = 'border-b border-slate-100 dark:border-slate-800/50';
                tr.innerHTML = `
                    <td class="py-3 px-2 font-medium"><i class="fa-solid fa-file-csv text-emerald-500 mr-2"></i>${f.fileName}</td>
                    <td class="py-3 px-2 text-slate-500 dark:text-slate-400">${f.owner}</td>
                    <td class="py-3 px-2 text-center">
                        <button onclick="deleteFile(${f.id}, '${f.fileName}')" class="text-rose-500 hover:text-rose-400 transition-colors" title="Șterge fișier">
                            <i class="fa-solid fa-trash"></i>
                        </button>
                    </td>
                `;
                tbody.appendChild(tr);
            });
        })
        .catch(err => console.error("Eroare fișiere:", err));
}

// Încarcă istoricul întrebărilor utilizatorilor
function loadQueries() {
    fetch('/api/admin/queries')
        .then(res => res.json())
        .then(queries => {
            const tbody = document.getElementById('queriesTableBody');
            tbody.innerHTML = '';

            if (queries.length === 0) {
                tbody.innerHTML = '<tr><td colspan="3" class="py-4 px-2 text-center text-slate-400">Nicio întrebare înregistrată.</td></tr>';
                return;
            }

            queries.forEach(q => {
                const tr = document.createElement('tr');
                tr.className = 'border-b border-slate-100 dark:border-slate-800/50';
                tr.innerHTML = `
                    <td class="py-3 px-2 font-medium">${q.username}</td>
                    <td class="py-3 px-2 text-slate-600 dark:text-slate-300">${escapeHtml(q.question)}</td>
                    <td class="py-3 px-2 text-slate-500 dark:text-slate-400 whitespace-nowrap">${q.createdAt}</td>
                `;
                tbody.appendChild(tr);
            });
        })
        .catch(err => console.error("Eroare istoric întrebări:", err));
}

// Încarcă istoricul acțiunilor administratorilor
function loadActions() {
    fetch('/api/admin/actions')
        .then(res => res.json())
        .then(actions => {
            const tbody = document.getElementById('actionsTableBody');
            tbody.innerHTML = '';

            if (actions.length === 0) {
                tbody.innerHTML = '<tr><td colspan="3" class="py-4 px-2 text-center text-slate-400">Nicio acțiune înregistrată.</td></tr>';
                return;
            }

            actions.forEach(a => {
                const tr = document.createElement('tr');
                tr.className = 'border-b border-slate-100 dark:border-slate-800/50';
                tr.innerHTML = `
                    <td class="py-3 px-2 font-medium">${a.admin}</td>
                    <td class="py-3 px-2 text-slate-600 dark:text-slate-300">${escapeHtml(a.action)}</td>
                    <td class="py-3 px-2 text-slate-500 dark:text-slate-400 whitespace-nowrap">${a.createdAt}</td>
                `;
                tbody.appendChild(tr);
            });
        })
        .catch(err => console.error("Eroare istoric acțiuni:", err));
}

// Încarcă istoricul conectărilor
function loadSessions() {
    fetch('/api/admin/sessions')
        .then(res => res.json())
        .then(sessions => {
            const tbody = document.getElementById('sessionsTableBody');
            tbody.innerHTML = '';

            if (sessions.length === 0) {
                tbody.innerHTML = '<tr><td colspan="5" class="py-4 px-2 text-center text-slate-400">Nicio conectare înregistrată.</td></tr>';
                return;
            }

            sessions.forEach(s => {
                const tr = document.createElement('tr');
                tr.className = 'border-b border-slate-100 dark:border-slate-800/50';
                tr.innerHTML = `
                    <td class="py-3 px-2 font-medium">${s.username}</td>
                    <td class="py-3 px-2">
                        <span class="text-xs font-semibold px-2 py-1 rounded-lg ${s.role === 'ADMIN' ? 'bg-indigo-100 text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-400' : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400'}">${s.role}</span>
                    </td>
                    <td class="py-3 px-2 text-slate-500 dark:text-slate-400 whitespace-nowrap">${s.loginTime}</td>
                    <td class="py-3 px-2 text-slate-500 dark:text-slate-400 whitespace-nowrap">${s.logoutTime}</td>
                    <td class="py-3 px-2 text-slate-600 dark:text-slate-300 whitespace-nowrap">${s.durata}</td>
                `;
                tbody.appendChild(tr);
            });
        })
        .catch(err => console.error("Eroare istoric conectări:", err));
}

// Protecție împotriva caracterelor speciale în text (evită stricarea HTML)
function escapeHtml(text) {
    if (text == null) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

let queriesChartInstance = null;

function renderQueriesChart(labels, values) {
    const ctx = document.getElementById('queriesChart').getContext('2d');
    if (queriesChartInstance) queriesChartInstance.destroy();

    queriesChartInstance = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                label: 'Număr interogări',
                data: values,
                backgroundColor: '#6366f1',
                borderRadius: 6
            }]
        },
        options: {
            responsive: true,
            plugins: { legend: { display: false } },
            scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
        }
    });
}

// Ștergere utilizator
function deleteUser(id, username) {
    if (!confirm(`Sigur vrei să ștergi utilizatorul "${username}"? Se vor șterge și fișierele și interogările lui.`)) return;

    fetch(`/api/admin/users/${id}`, { method: 'DELETE' })
        .then(res => res.text())
        .then(() => {
            loadStats();
            loadUsers();
            loadFiles();
            loadActions();
        })
        .catch(err => alert("Eroare la ștergere: " + err));
}

// Ștergere fișier
function deleteFile(id, fileName) {
    if (!confirm(`Sigur vrei să ștergi fișierul "${fileName}"?`)) return;

    fetch(`/api/admin/files/${id}`, { method: 'DELETE' })
        .then(res => res.text())
        .then(() => {
            loadStats();
            loadUsers();
            loadFiles();
            loadActions();
        })
        .catch(err => alert("Eroare la ștergere: " + err));
}