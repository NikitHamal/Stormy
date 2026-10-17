        // ============================================================================
        // PROJECT EDITOR FLOW — open from project tap; Editor + Chat tabs,
        // file tabs, code area, Files drawer, New File dialog
        // ============================================================================
        state.editor = {
            projectId: null,
            tab: 'chat',                // 'editor' | 'chat' (chat opens by default)
            files: [],                  // { name, content, dirty }
            activeFile: 'index.html',
            branchesOpen: false
        };

        function editorProject() {
            return state.projects.find((p) => p.id === state.editor.projectId) || state.projects[0];
        }

        function openEditor(projId) {
            state.editor.projectId = projId;
            state.editor.tab = 'chat';
            state.editor.branchesOpen = false;
            const proj = editorProject();
            state.editor.files = [{ name: 'index.html', content: 'test', dirty: true }];
            state.editor.activeFile = 'index.html';
            document.getElementById('editorProjectTitle').textContent = proj ? proj.name : 'Test';
            ['filesDrawerWrap', 'assetsDrawerWrap', 'sourceDrawerWrap', 'newFileOverlay', 'chatSettingsWrap', 'selectModelWrap']
                .forEach((id) => document.getElementById(id).classList.add('hidden'));
            renderEditorScreen();
            document.getElementById('editorScreen').classList.remove('hidden');
            document.getElementById('editorScreen').classList.add('flex');
        }

        function closeEditor() {
            document.getElementById('editorScreen').classList.add('hidden');
            document.getElementById('editorScreen').classList.remove('flex');
        }

        function renderEditorScreen() {
            switchEditorTab(state.editor.tab, true);
            renderEditorTabs();
            renderEditorCode();
            renderEditorFiles();
            renderChat();
            updateBranches();
        }

        function switchEditorTab(tab, silent = false) {
            state.editor.tab = tab;
            const chatBtn = document.getElementById('editorTabChat');
            const edBtn = document.getElementById('editorTabEditor');
            const on = ['ed-segbtn-on'];
            if (tab === 'chat') {
                chatBtn.classList.add(...on);
                edBtn.classList.remove(...on);
            } else {
                edBtn.classList.add(...on);
                chatBtn.classList.remove(...on);
            }
            const editorPane = document.getElementById('editorPane');
            const chatPane = document.getElementById('chatPane');
            const fab = document.getElementById('editorSaveFab');
            if (tab === 'chat') {
                editorPane.classList.add('hidden');
                editorPane.classList.remove('flex');
                chatPane.classList.remove('hidden');
                chatPane.classList.add('flex');
                fab.classList.add('hidden');
                fab.classList.remove('flex');
            } else {
                chatPane.classList.add('hidden');
                chatPane.classList.remove('flex');
                editorPane.classList.remove('hidden');
                editorPane.classList.add('flex');
                fab.classList.remove('hidden');
                fab.classList.add('flex');
            }
            if (!silent) renderEditorScreen();
        }

        // ---- file model -------------------------------------------------------
        function activeEditorFile() {
            return state.editor.files.find((f) => f.name === state.editor.activeFile) || state.editor.files[0];
        }

        function extBadgeClass(name) {
            const ext = (name.split('.').pop() || '').toLowerCase();
            if (ext === 'html' || ext === 'htm') return 'ed-badge-ht';
            if (ext === 'css') return 'ed-badge-cs';
            if (ext === 'js' || ext === 'ts') return 'ed-badge-js';
            return 'ed-badge-ot';
        }

        function extBadgeLabel(name) {
            const ext = (name.split('.').pop() || '').toLowerCase();
            if (ext === 'html' || ext === 'htm') return 'HT';
            if (ext === 'css') return 'CS';
            if (ext === 'js') return 'JS';
            return (ext.slice(0, 2) || '??').toUpperCase();
        }

        function fileIcon(name, cls) {
            return `<span class="material-symbols-rounded ${cls}" style="color:#A8A29A">description</span>`;
        }

        function renderEditorTabs() {
            const strip = document.getElementById('editorFileTabs');
            strip.innerHTML = state.editor.files.map((f) => {
                const active = f.name === state.editor.activeFile;
                return `
                    <button onclick="openEditorFile('${escapeHtml(f.name)}')"
                        class="flex items-center gap-2 h-12 px-3.5 shrink-0 text-[14.5px] font-medium ${active ? 'ed-tab-on' : 'ed-tab'} expressive-btn transition-colors">
                        <span class="ed-badge ${extBadgeClass(f.name)}">${extBadgeLabel(f.name)}</span>
                        ${f.dirty ? '<span class="ed-dot text-[16px] leading-none">•</span>' : ''}
                        <span class="truncate max-w-[130px]">${escapeHtml(f.name)}</span>
                        <span onclick="event.stopPropagation(); closeEditorFileTab('${escapeHtml(f.name)}')"
                            class="w-6 h-6 rounded-full flex items-center justify-center">
                            <span class="material-symbols-rounded text-[17px]">close</span>
                        </span>
                    </button>`;
            }).join('');
        }

        function openEditorFile(name) {
            state.editor.activeFile = name;
            renderEditorTabs();
            renderEditorCode();
        }

        function openEditorFileFromList(name) {
            closeEditorDrawer('filesDrawerWrap');
            switchEditorTab('editor');
            openEditorFile(name);
        }

        function closeEditorFileTab(name) {
            if (state.editor.files.length <= 1) {
                showToast('One file stays open');
                return;
            }
            state.editor.files = state.editor.files.filter((f) => f.name !== name);
            if (state.editor.activeFile === name) state.editor.activeFile = state.editor.files[0].name;
            renderEditorTabs();
            renderEditorCode();
            renderEditorFiles();
        }

        // ---- code area ----------------------------------------------------------
        function renderEditorCode() {
            const file = activeEditorFile();
            const ta = document.getElementById('editorTextarea');
            const gutter = document.getElementById('editorLines');
            if (!file) return;
            ta.value = file.content;
            const size = state.editorFontSize || 14;
            ta.style.fontSize = size + 'px';
            ta.style.lineHeight = '1.7';
            gutter.style.fontSize = Math.max(12, size - 1) + 'px';
            gutter.style.display = state.lineNumbers === false ? 'none' : 'block';
            updateEditorLines();
        }

        function updateEditorLines() {
            const ta = document.getElementById('editorTextarea');
            const gutter = document.getElementById('editorLines');
            const count = ta.value.split('\n').length;
            let html = '';
            for (let i = 1; i <= count; i++) html += `<div>${i}</div>`;
            gutter.innerHTML = html;
        }

        function onEditorInput(val) {
            const file = activeEditorFile();
            if (!file) return;
            file.content = val;
            if (!file.dirty) {
                file.dirty = true;
                renderEditorTabs();
            }
            updateEditorLines();
        }

        function saveEditorFile() {
            const file = activeEditorFile();
            if (!file) return;
            file.dirty = false;
            renderEditorTabs();
            showToast(`Saved ${file.name}`);
        }

        // ---- files drawer ---------------------------------------------------------
        function renderEditorFiles() {
            const list = document.getElementById('editorFilesList');
            list.innerHTML = state.editor.files.map((f) => {
                const active = f.name === state.editor.activeFile;
                return `
                    <button onclick="openEditorFileFromList('${escapeHtml(f.name)}')"
                        class="w-full px-4 h-14 flex items-center gap-3 ${active ? 'bg-white/5' : ''} active:bg-white/5 expressive-btn transition-colors text-left">
                        ${fileIcon(f.name, 'text-[24px]')}
                        <span class="text-[16px] text-[#EDE9E2] truncate flex-1">${escapeHtml(f.name)}${f.dirty ? ' •' : ''}</span>
                        <span onclick="event.stopPropagation(); showToast('File options — rename, delete, duplicate')"
                            class="w-9 h-9 rounded-full flex items-center justify-center text-[#8D877D]">
                            <span class="material-symbols-rounded text-[21px]">more_vert</span>
                        </span>
                    </button>`;
            }).join('');
        }

        // ---- drawers (generic open/close; re-trigger entrance on each open) ---------
        function openEditorDrawer(wrapId) {
            const wrap = document.getElementById(wrapId);
            wrap.classList.remove('hidden');
            const panel = wrap.querySelector('.animate-drawer-in');
            const scrim = wrap.querySelector('.animate-fade-in');
            [panel, scrim].forEach((el) => {
                if (!el) return;
                el.style.animation = 'none';
                void el.offsetWidth;
                el.style.animation = '';
            });
        }

        function closeEditorDrawer(wrapId) {
            document.getElementById(wrapId).classList.add('hidden');
        }

        // ---- branches --------------------------------------------------------------
        function toggleBranches() {
            state.editor.branchesOpen = !state.editor.branchesOpen;
            updateBranches();
        }

        function updateBranches() {
            const chev = document.getElementById('branchesChevron');
            const empty = document.getElementById('branchesEmpty');
            if (!chev || !empty) return;
            chev.style.transform = state.editor.branchesOpen ? 'rotate(90deg)' : 'rotate(0deg)';
            empty.classList.toggle('hidden', !state.editor.branchesOpen);
        }

        // ---- new file dialog ----------------------------------------------------------
        function openNewFileDialog() {
            const input = document.getElementById('newFileNameInput');
            input.value = '';
            onNewFileInput('');
            document.getElementById('newFileOverlay').classList.remove('hidden');
            setTimeout(() => input.focus(), 120);
        }

        function closeNewFileDialog() {
            document.getElementById('newFileOverlay').classList.add('hidden');
        }

        function onNewFileInput(val) {
            const btn = document.getElementById('newFileCreateBtn');
            const ready = val.trim().length > 0;
            btn.classList.toggle('opacity-40', !ready);
            btn.classList.toggle('opacity-100', ready);
        }

        function submitNewFile() {
            const input = document.getElementById('newFileNameInput');
            const name = input.value.trim();
            if (!name) return;
            if (state.editor.files.some((f) => f.name === name)) {
                showToast('A file with that name exists');
                return;
            }
            state.editor.files.push({ name, content: '', dirty: false });
            state.editor.activeFile = name;
            closeNewFileDialog();
            renderEditorTabs();
            renderEditorCode();
            renderEditorFiles();
            showToast(`Created ${name}`);
        }

        // ---- escape closes topmost editor layer -----------------------------------------
        window.addEventListener('keydown', (e) => {
            if (e.key !== 'Escape') return;
            const screen = document.getElementById('editorScreen');
            if (!screen || screen.classList.contains('hidden')) return;
            if (!document.getElementById('newFileOverlay').classList.contains('hidden')) closeNewFileDialog();
            else if (!document.getElementById('chatSettingsWrap').classList.contains('hidden')) closeChatSettingsSheet();
            else if (!document.getElementById('selectModelWrap').classList.contains('hidden')) closeSelectModelSheet();
            else if (!document.getElementById('filesDrawerWrap').classList.contains('hidden')) closeEditorDrawer('filesDrawerWrap');
            else if (!document.getElementById('assetsDrawerWrap').classList.contains('hidden')) closeEditorDrawer('assetsDrawerWrap');
            else if (!document.getElementById('sourceDrawerWrap').classList.contains('hidden')) closeEditorDrawer('sourceDrawerWrap');
            else closeEditor();
        });
