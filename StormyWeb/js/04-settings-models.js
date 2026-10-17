        // ============================================================================
        // FULL-SCREEN SETTINGS & SUB-SCREENS LOGIC (SCREENSHOTS 1–8)
        // ============================================================================
        function openSettingsSheet() {
            closeSpeedDial();
            updateFontSizeSlider(state.editorFontSize);
            document.getElementById('settingsScreen').classList.remove('hidden');
            document.getElementById('settingsScreen').classList.add('flex');
        }

        function closeSettingsSheet() {
            closeAiModelsScreen();
            closeMemoriesScreen();
            closeAiProvidersScreen();
            document.getElementById('gitSettingsScreen').classList.add('hidden');
            document.getElementById('gitSettingsScreen').classList.remove('flex');
            document.getElementById('settingsScreen').classList.add('hidden');
            document.getElementById('settingsScreen').classList.remove('flex');
        }

        function applyThemeToPhone() {
            const root = document.documentElement;
            let theme = state.themeMode;
            if (theme === 'system') {
                const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
                theme = prefersDark ? 'dark' : 'light';
            }
            root.setAttribute('data-theme', theme);
        }

        // Follow the OS theme while "System" is selected
        (function watchSystemTheme() {
            if (!window.matchMedia) return;
            const mq = window.matchMedia('(prefers-color-scheme: dark)');
            const onChange = () => { if (state.themeMode === 'system') applyThemeToPhone(); };
            if (mq.addEventListener) mq.addEventListener('change', onChange);
            else if (mq.addListener) mq.addListener(onChange);
        })();

        function setThemeMode(mode) {
            state.themeMode = mode;
            ['system', 'light', 'dark'].forEach(m => {
                const btn = document.getElementById(`themeBtn-${m}`);
                if (!btn) return;
                if (m === mode) {
                    btn.className = "w-[46px] h-[44px] rounded-[15px] bg-m3-primaryContainer text-m3-onPrimaryContainer flex items-center justify-center expressive-btn transition-all";
                } else {
                    btn.className = "w-[46px] h-[44px] rounded-[15px] bg-[#24211D] text-[#B8B2A8] hover:text-white flex items-center justify-center expressive-btn transition-all";
                }
            });
            applyThemeToPhone();
            showToast(`Theme set to ${mode.charAt(0).toUpperCase() + mode.slice(1)}`);
        }

        function toggleSettingSwitch(key) {
            state[key] = !state[key];
            const btn = document.getElementById(`switch-${key}`);
            if (!btn) return;
            if (state[key]) {
                btn.setAttribute('aria-checked', 'true');
                btn.className = "w-[52px] h-[32px] rounded-full bg-m3-primary p-1 flex items-center justify-end transition-all duration-200 shrink-0";
                btn.innerHTML = `<span class="w-6 h-6 rounded-full bg-m3-onPrimary transition-all duration-200 shadow-sm"></span>`;
            } else {
                btn.setAttribute('aria-checked', 'false');
                btn.className = "w-[52px] h-[32px] rounded-full bg-[#292622] border-2 border-[#6E6A63] p-1 flex items-center justify-start transition-all duration-200 shrink-0";
                btn.innerHTML = `<span class="w-4 h-4 rounded-full bg-[#6E6A63] ml-0.5 transition-all duration-200"></span>`;
            }
        }

        function updateFontSizeSlider(val) {
            const num = parseInt(val, 10);
            state.editorFontSize = num;
            const label = document.getElementById('fontSizeValueLabel');
            const activeTrack = document.getElementById('sliderActiveTrack');
            const ticksOverlay = document.getElementById('sliderTicksOverlay');
            if (label) label.textContent = `${num}sp`;

            const pct = ((num - 10) / (24 - 10)) * 100;
            if (activeTrack) {
                activeTrack.style.width = `calc(${pct}% - 7px)`;
            }

            if (ticksOverlay) {
                let dotsHtml = '';
                for (let i = 10; i <= 24; i++) {
                    const isPassed = i <= num;
                    const dotColor = isPassed ? 'bg-[#141210]' : 'bg-[#EDE9E2]/65';
                    dotsHtml += `<span class="w-[4px] h-[4px] rounded-full ${dotColor}"></span>`;
                }
                ticksOverlay.innerHTML = dotsHtml;
            }
        }

        // --- AI MODELS SUB-SCREEN (Screenshots 3 & 4) ---
        const AI_MODEL_CATEGORIES = ['All', 'Pollinations', 'Google Gemini', 'NVIDIA NIM', 'OpenRouter'];

        function openAiModelsScreen() {
            state.aiModelsQuery = '';
            const input = document.getElementById('aiModelsSearchInput');
            if (input) input.value = '';
            resetAiModelsSearchLayers();
            renderAiModelsScreen();
            document.getElementById('aiModelsScreen').classList.remove('hidden');
            document.getElementById('aiModelsScreen').classList.add('flex');
        }

        function resetAiModelsSearchLayers() {
            const def = document.getElementById('aiModelsHeaderDefault');
            const layer = document.getElementById('aiModelsSearchLayer');
            if (def) {
                def.style.opacity = '1';
                def.style.transform = 'translateY(0) scale(1)';
                def.style.pointerEvents = 'auto';
            }
            if (layer) {
                layer.style.opacity = '0';
                layer.style.transform = 'translateY(12px) scale(0.96)';
                layer.style.pointerEvents = 'none';
            }
        }

        function openAiModelsSearch() {
            const def = document.getElementById('aiModelsHeaderDefault');
            const layer = document.getElementById('aiModelsSearchLayer');
            const input = document.getElementById('aiModelsSearchInput');
            if (def) {
                def.style.opacity = '0';
                def.style.transform = 'translateY(-12px) scale(0.98)';
                def.style.pointerEvents = 'none';
            }
            if (layer) {
                layer.style.opacity = '1';
                layer.style.transform = 'translateY(0) scale(1)';
                layer.style.pointerEvents = 'auto';
            }
            setTimeout(() => { if (input) input.focus(); }, 120);
        }

        function closeAiModelsSearch() {
            resetAiModelsSearchLayers();
            const input = document.getElementById('aiModelsSearchInput');
            if (input) input.value = '';
            filterAiModels('');
        }

        function closeAiModelsScreen() {
            document.getElementById('aiModelsScreen').classList.add('hidden');
            document.getElementById('aiModelsScreen').classList.remove('flex');
        }

        function selectAiModelCategory(cat) {
            state.aiModelsActiveCategory = cat;
            state.aiModelsQuery = '';
            const input = document.getElementById('aiModelsSearchInput');
            if (input) input.value = '';
            const clearBtn = document.getElementById('aiModelsClearBtn');
            if (clearBtn) { clearBtn.classList.add('hidden'); clearBtn.classList.remove('flex'); }
            renderAiModelsScreen();
        }

        function refreshAiModels() {
            const icon = document.getElementById('aiModelsRefreshIcon');
            if (icon) {
                icon.style.transform = 'rotate(360deg)';
                setTimeout(() => { icon.style.transform = 'rotate(0deg)'; }, 520);
            }
            showToast('Models list refreshed');
        }

        function togglePollinationsModel(modelId) {
            const item = state.pollinationsModels.find(m => m.id === modelId);
            if (item) {
                item.enabled = !item.enabled;
                renderAiModelsScreen();
            }
        }

        function toggleAllPollinationsModels() {
            const allOn = state.pollinationsModels.every(m => m.enabled);
            state.pollinationsModels.forEach(m => { m.enabled = !allOn; });
            renderAiModelsScreen();
        }

        function renderAiModelsScreen() {
            const chipsRow = document.getElementById('aiModelsChipsRow');
            const contentArea = document.getElementById('aiModelsContentArea');

            chipsRow.innerHTML = AI_MODEL_CATEGORIES.map(cat => {
                const isSelected = state.aiModelsActiveCategory === cat;
                return `
                    <button type="button" onclick="selectAiModelCategory('${cat}')"
                        class="m3-chip ${isSelected ? 'selected-tonal' : 'bg-transparent text-[#B8B2A8] border-m3-outline hover:border-white/40'} px-4 py-1.5 text-[13.5px] border shrink-0 transition-all">
                        ${escapeHtml(cat)}
                    </button>
                `;
            }).join('');

            updateAiModelsSubtitle();

            const cat = state.aiModelsActiveCategory;

            // Keyed providers (Gemini / NVIDIA / OpenRouter) get a dedicated setup state
            if (cat !== 'All' && cat !== 'Pollinations') {
                contentArea.innerHTML = aiModelsProviderEmptyHtml(cat);
                return;
            }

            const showProviders = cat === 'All' && !state.aiModelsQuery.trim();

            contentArea.innerHTML = `
                ${aiModelsHeroHtml()}
                <div class="flex items-center justify-between pt-4 pb-2.5 px-1">
                    <span class="text-[11px] font-bold uppercase tracking-wider text-m3-primary">Models • 154</span>
                    <button onclick="toggleAllPollinationsModels()" class="text-[13px] font-semibold text-m3-primary">
                        ${state.pollinationsModels.every(m => m.enabled) ? 'Turn all off' : 'Turn all on'}
                    </button>
                </div>
                <div id="aiModelsListContainer" class="space-y-2.5"></div>
                ${showProviders ? `<div id="aiModelsProvidersSection"></div>` : ''}
            `;
            renderAiModelsList();
            if (showProviders) renderAiModelsProviders();
        }

        function aiModelsHeroHtml() {
            const total = state.pollinationsModels.length;
            const enabled = state.pollinationsModels.filter(m => m.enabled).length;
            const totalDisplay = enabled === total ? '154 / 154 on' : `${147 + enabled} / 154 on`;
            return `
                <div class="rounded-[26px] bg-m3-surface border border-white/[0.03] p-4 flex items-center gap-3.5 mt-1">
                    <div class="relative w-[52px] h-[52px] flex items-center justify-center shrink-0">
                        <svg class="w-full h-full text-m3-primaryContainer" viewBox="0 0 100 100" fill="currentColor">
                            <path d="M 50 12 C 62 4 82 4 90 16 C 98 28 96 42 86 50 C 96 58 98 72 90 84 C 82 96 62 96 50 88 C 38 96 18 96 10 84 C 2 72 4 58 14 50 C 4 42 2 28 10 16 C 18 4 38 4 50 12 Z"/>
                        </svg>
                        <span class="material-symbols-rounded filled absolute text-[22px] text-m3-primary">bolt</span>
                    </div>
                    <div class="flex-1 min-w-0">
                        <div class="text-[17px] font-bold text-[#EDE9E2] leading-tight">Pollinations</div>
                        <div class="text-[12.5px] text-[#8D877D] mt-0.5">Free inference • no key needed</div>
                    </div>
                    <div class="text-right shrink-0">
                        <div id="aiModelsEnabledCount" class="text-[18px] font-bold text-m3-primary leading-tight">${enabled}/${total}</div>
                        <div class="text-[10.5px] text-[#8D877D]">enabled</div>
                    </div>
                </div>
                <div class="px-1 pt-2 text-[12.5px] text-[#8D877D]">${totalDisplay} across the catalog</div>
            `;
        }

        function getVisiblePollinationsModels() {
            const q = state.aiModelsQuery.trim().toLowerCase();
            if (!q) return [...state.pollinationsModels];
            return state.pollinationsModels.filter(m =>
                m.name.toLowerCase().includes(q) || (m.meta && m.meta.toLowerCase().includes(q))
            );
        }

        function aiModelsSwitchHtml(enabled) {
            return enabled ? `
                <span class="w-[52px] h-[32px] rounded-full bg-m3-primary p-1 flex items-center justify-end transition-all shrink-0">
                    <span class="w-6 h-6 rounded-full bg-m3-onPrimary shadow-sm"></span>
                </span>
            ` : `
                <span class="w-[52px] h-[32px] rounded-full bg-[#292622] border-2 border-[#6E6A63] p-1 flex items-center justify-start transition-all shrink-0">
                    <span class="w-4 h-4 rounded-full bg-[#6E6A63] ml-0.5"></span>
                </span>
            `;
        }

        function renderAiModelsList() {
            const list = document.getElementById('aiModelsListContainer');
            if (!list) return;
            const visible = getVisiblePollinationsModels();

            if (visible.length === 0) {
                list.innerHTML = `
                    <div class="flex flex-col items-center justify-center text-center py-12 px-6">
                        <span class="material-symbols-rounded text-[44px] text-[#8D877D] mb-2">search_off</span>
                        <h3 class="text-[16px] font-semibold text-[#EDE9E2] mb-1">No models match "${escapeHtml(state.aiModelsQuery.trim())}"</h3>
                        <button onclick="clearAiModelsSearch()" class="mt-2 px-4 py-2 rounded-full bg-m3-primaryContainer text-m3-onPrimaryContainer text-xs font-semibold">
                            Clear search
                        </button>
                    </div>
                `;
                return;
            }

            list.innerHTML = visible.map(m => `
                <div onclick="togglePollinationsModel('${m.id}')"
                    class="bg-m3-surface rounded-[22px] px-4 py-3 flex items-center justify-between gap-3 cursor-pointer">
                    <div class="flex items-center gap-3.5 min-w-0">
                        <div class="w-10 h-10 rounded-full bg-[#2B2823] flex items-center justify-center shrink-0">
                            <span class="material-symbols-rounded filled text-[18px] text-[#EDE9E2]">bolt</span>
                        </div>
                        <div class="min-w-0">
                            <div class="text-[15.5px] font-medium text-[#EDE9E2] truncate">${escapeHtml(m.name)}</div>
                            <div class="text-[12px] text-[#8D877D] truncate mt-0.5">${escapeHtml(m.meta || '')}</div>
                        </div>
                    </div>
                    ${aiModelsSwitchHtml(m.enabled)}
                </div>
            `).join('');
        }

        function renderAiModelsProviders() {
            const section = document.getElementById('aiModelsProvidersSection');
            if (!section) return;
            const providers = state.aiProviders.filter(p => p.id !== 'pollinations' && p.id !== 'agnes' && p.id !== 'custom');
            if (providers.length === 0) { section.innerHTML = ''; return; }
            section.innerHTML = `
                <div class="flex items-center justify-between pt-5 pb-2.5 px-1">
                    <span class="text-[11px] font-bold uppercase tracking-wider text-m3-primary">More providers</span>
                    <button onclick="openAiProviderSetup()" class="text-[13px] font-semibold text-m3-primary">Manage keys</button>
                </div>
                <div class="space-y-2.5">
                    ${providers.map(p => `
                        <div onclick="openAiProviderSetup()"
                            class="bg-m3-surface rounded-[22px] px-4 py-3 flex items-center justify-between gap-3 cursor-pointer">
                            <div class="flex items-center gap-3.5 min-w-0">
                                <div class="relative w-10 h-10 flex items-center justify-center shrink-0">
                                    ${getProviderShapeSvg(p.shape, p.ready ? p.badgeBg : '#2b3139')}
                                    <span style="color: ${p.ready ? p.badgeText : '#8D877D'}" class="absolute text-[15px] font-bold">${p.letter}</span>
                                </div>
                                <div class="min-w-0">
                                    <div class="text-[15.5px] font-medium text-[#EDE9E2] truncate">${escapeHtml(p.name)}</div>
                                    <div class="text-[12px] text-[#8D877D] truncate mt-0.5">${escapeHtml(p.statusText)}</div>
                                </div>
                            </div>
                            ${p.ready
                                ? '<span class="material-symbols-rounded text-[22px] text-[#EDE9E2] shrink-0">check_circle</span>'
                                : '<span class="text-[12px] font-bold px-3.5 py-2 rounded-full bg-m3-primaryContainer text-m3-onPrimaryContainer shrink-0">Add key</span>'}
                        </div>
                    `).join('')}
                </div>
            `;
        }

        function aiModelsProviderEmptyHtml(cat) {
            const provider = state.aiProviders.find(p => p.name === cat);
            const ready = provider && provider.ready;
            const icon = cat === 'Google Gemini' ? 'model_training' : 'vpn_key';
            return `
                <div class="h-full min-h-[440px] flex flex-col items-center justify-center text-center px-8">
                    <div class="w-[84px] h-[84px] rounded-full bg-m3-primaryContainer flex items-center justify-center mb-5">
                        <span class="material-symbols-rounded text-[38px] text-m3-primary">${icon}</span>
                    </div>
                    <h3 class="text-[18px] font-bold text-[#EDE9E2] mb-1.5">${escapeHtml(cat)}</h3>
                    <p class="text-[13.5px] text-[#8D877D] leading-relaxed max-w-[250px] mb-5">
                        ${ready
                            ? 'Provider connected — its model catalog syncs from your provider settings.'
                            : 'This provider needs an API key before its models can appear here.'}
                    </p>
                    <button onclick="openAiProviderSetup()" class="px-6 py-3 rounded-[16px] bg-m3-primary text-m3-onPrimary text-[14.5px] font-bold expressive-btn mb-2.5">
                        ${ready ? 'Manage provider' : 'Add API key'}
                    </button>
                    <button onclick="selectAiModelCategory('All')" class="px-4 py-2 rounded-full text-[13px] font-semibold text-m3-primary">
                        Browse Pollinations instead
                    </button>
                </div>
            `;
        }

        function openAiProviderSetup() {
            closeAiModelsScreen();
            openAiProvidersScreen();
        }

        function updateAiModelsSubtitle() {
            const sub = document.getElementById('aiModelsSubtitle');
            if (!sub) return;
            const total = state.pollinationsModels.length;
            const enabled = state.pollinationsModels.filter(m => m.enabled).length;
            sub.textContent = `${enabled} of ${total} enabled`;
        }

        function filterAiModels(q) {
            state.aiModelsQuery = q;
            const clearBtn = document.getElementById('aiModelsClearBtn');
            if (clearBtn) {
                const has = q.trim().length > 0;
                clearBtn.classList.toggle('hidden', !has);
                clearBtn.classList.toggle('flex', has);
            }
            // Re-render list only (keeps search-field focus); hero count stays live
            renderAiModelsList();
            const count = document.getElementById('aiModelsEnabledCount');
            if (count) {
                const total = state.pollinationsModels.length;
                const enabled = state.pollinationsModels.filter(m => m.enabled).length;
                count.textContent = `${enabled}/${total}`;
            }
            // Hide the providers section while searching so results stay focused
            const section = document.getElementById('aiModelsProvidersSection');
            if (section) section.style.display = q.trim() ? 'none' : '';
        }

        function clearAiModelsSearch() {
            const input = document.getElementById('aiModelsSearchInput');
            if (input) { input.value = ''; input.focus(); }
            filterAiModels('');
        }

        // --- MEMORIES SUB-SCREEN (Screenshot 5) ---
        function openMemoriesScreen() {
            document.getElementById('memoriesSearchInput').value = '';
            renderMemoriesState('');
            document.getElementById('memoriesScreen').classList.remove('hidden');
            document.getElementById('memoriesScreen').classList.add('flex');
        }

        function closeMemoriesScreen() {
            document.getElementById('memoriesScreen').classList.add('hidden');
            document.getElementById('memoriesScreen').classList.remove('flex');
        }

        function renderMemoriesState(query) {
            const sub = document.getElementById('memoriesEmptySubtext');
            if (query && query.trim().length > 0) {
                sub.textContent = `No learned memories matching "${query.trim()}"`;
            } else {
                sub.textContent = 'The AI will learn about your projects as you work together';
            }
        }

        // --- AI PROVIDERS SUB-SCREEN (Screenshot 6) ---
        function getProviderShapeSvg(shape, fillHex) {
            if (shape === 'scallop') {
                return `<svg class="w-full h-full" style="color: ${fillHex}" viewBox="0 0 100 100" fill="currentColor">
                    <path d="M 50 4 C 55 4 58 8 62 9 C 66 10 71 7 75 10 C 79 13 78 18 81 22 C 84 26 90 26 92 31 C 94 36 90 40 91 45 C 92 50 96 54 95 59 C 94 64 89 66 87 71 C 85 76 88 81 84 85 C 80 89 75 87 70 90 C 65 93 64 97 59 97 C 54 97 50 93 45 94 C 40 95 36 98 31 96 C 26 94 26 89 22 86 C 18 83 12 85 9 80 C 6 75 10 70 9 65 C 8 60 3 57 4 52 C 5 47 9 44 10 39 C 11 34 7 29 10 24 C 13 19 19 20 23 17 C 27 14 27 8 32 6 C 37 4 42 7 46 5 C 48 4 49 4 50 4 Z"/>
                </svg>`;
            }
            if (shape === 'flower') {
                return `<svg class="w-full h-full" style="color: ${fillHex}" viewBox="0 0 100 100" fill="currentColor">
                    <path d="M50 2 L61 19 L80 11 L79 31 L97 38 L85 54 L96 71 L76 74 L74 94 L56 84 L43 97 L36 78 L16 81 L22 62 L5 52 L21 39 L15 20 L35 23 L42 4 Z"/>
                </svg>`;
            }
            if (shape === 'clover') {
                return `<svg class="w-full h-full" style="color: ${fillHex}" viewBox="0 0 100 100" fill="currentColor">
                    <path d="M 50 12 C 62 4 82 4 90 16 C 98 28 96 42 86 50 C 96 58 98 72 90 84 C 82 96 62 96 50 88 C 38 96 18 96 10 84 C 2 72 4 58 14 50 C 4 42 2 28 10 16 C 18 4 38 4 50 12 Z"/>
                </svg>`;
            }
            if (shape === 'starburst') {
                return `<svg class="w-full h-full" style="color: ${fillHex}" viewBox="0 0 100 100" fill="currentColor">
                    <path d="M 50 3 C 56 3 60 11 66 13 C 72 15 81 11 85 16 C 89 21 85 30 87 36 C 89 42 97 45 97 51 C 97 57 89 60 87 66 C 85 72 89 81 85 86 C 81 91 72 87 66 89 C 60 91 56 98 50 98 C 44 98 40 91 34 89 C 28 87 19 91 15 86 C 11 81 15 72 13 66 C 11 60 3 57 3 51 C 3 45 11 42 13 36 C 15 30 11 21 15 16 C 19 11 28 15 34 13 C 40 11 44 3 50 3 Z"/>
                </svg>`;
            }
            return `<div class="w-full h-full rounded-full" style="background-color: ${fillHex}"></div>`;
        }

        function openAiProvidersScreen() {
            renderAiProvidersList();
            document.getElementById('aiProvidersScreen').classList.remove('hidden');
            document.getElementById('aiProvidersScreen').classList.add('flex');
        }

        function closeAiProvidersScreen() {
            document.getElementById('aiProvidersScreen').classList.add('hidden');
            document.getElementById('aiProvidersScreen').classList.remove('flex');
        }

        function toggleExpandAiProvider(id) {
            state.aiProviders.forEach(p => {
                if (p.id === id) p.expanded = !p.expanded;
            });
            renderAiProvidersList();
        }

        function pasteProviderKey(id) {
            const p = state.aiProviders.find(item => item.id === id);
            if (!p) return;
            p.apiKey = `nvapi-${id}-live-99410expressive`;
            p.ready = true;
            p.statusText = 'Key saved';
            p.badgeBg = '#38342D';
            p.badgeText = '#EDE9E2';
            renderAiProvidersList();
            const readyCount = state.aiProviders.filter(i => i.ready).length;
            const sub = document.getElementById('aiProvidersReadySubtitle');
            if (sub) sub.textContent = `${readyCount} of 11 ready`;
            showToast(`Saved API key for ${p.name}`);
        }

        function renderAiProvidersList() {
            const container = document.getElementById('aiProvidersListContainer');
            container.innerHTML = state.aiProviders.map(p => {
                const badgeArt = getProviderShapeSvg(p.shape, p.badgeBg);
                return `
                    <div class="bg-m3-surface rounded-[26px] p-4 border border-white/[0.03] transition-all">
                        <div onclick="toggleExpandAiProvider('${p.id}')" class="flex items-center justify-between cursor-pointer">
                            <div class="flex items-center gap-3.5 min-w-0">
                                <div class="relative w-[44px] h-[44px] flex items-center justify-center shrink-0">
                                    ${badgeArt}
                                    <span style="color: ${p.badgeText}" class="absolute text-[16px] font-bold">${p.letter}</span>
                                </div>
                                <div class="min-w-0">
                                    <div class="text-[17px] font-semibold text-[#EDE9E2] truncate">${escapeHtml(p.name)}</div>
                                    <div class="text-[13px] text-[#8D877D] truncate">${escapeHtml(p.statusText)}</div>
                                </div>
                            </div>
                            ${p.ready ? `
                                <span class="material-symbols-rounded text-[23px] text-[#EDE9E2] shrink-0">check_circle</span>
                            ` : ''}
                        </div>

                        ${p.expanded ? `
                            <div class="mt-4 pt-1 space-y-3.5">
                                <p class="text-[13.5px] text-[#8D877D] leading-relaxed">
                                    ${escapeHtml(p.description)}
                                </p>
                                <div class="relative flex items-center">
                                    <input type="password" value="${escapeHtml(p.apiKey)}" placeholder="API key"
                                        oninput="this.dataset.edited = 'true'"
                                        class="w-full bg-transparent border border-[#4E493F] focus:border-m3-primary rounded-[14px] pl-4 pr-20 py-3.5 text-[15px] text-[#EDE9E2] placeholder-[#8D877D] focus:outline-none transition-colors" />
                                    <div class="absolute right-2.5 flex items-center gap-1">
                                        <button type="button" onclick="pasteProviderKey('${p.id}')" title="Paste API Key"
                                            class="w-8 h-8 rounded-lg hover:bg-white/10 flex items-center justify-center text-[#B8B2A8]">
                                            <span class="material-symbols-rounded text-[19px]">content_paste</span>
                                        </button>
                                        <button type="button" onclick="showToast('Key visibility toggled')" title="Show/Hide Key"
                                            class="w-8 h-8 rounded-lg hover:bg-white/10 flex items-center justify-center text-[#B8B2A8]">
                                            <span class="material-symbols-rounded text-[19px]">visibility</span>
                                        </button>
                                    </div>
                                </div>
                                <button type="button" onclick="showToast('Opening ${escapeHtml(p.name)} developer portal...')"
                                    class="inline-flex items-center gap-1.5 text-[14px] font-semibold text-m3-primary hover:underline pt-0.5">
                                    <span>Get a key</span>
                                    <span class="material-symbols-rounded text-[16px]">open_in_new</span>
                                </button>
                            </div>
                        ` : ''}
                    </div>
                `;
            }).join('');
        }

        // --- GIT SETTINGS SUB-SCREEN (Screenshots 7 & 8) ---
        function openGitSettingsScreen(fromTarget = 'settings') {
            state.gitReturnTarget = fromTarget;
            document.getElementById('gitSettingsScreen').classList.remove('hidden');
            document.getElementById('gitSettingsScreen').classList.add('flex');
        }

        function closeGitSettingsScreen() {
            document.getElementById('gitSettingsScreen').classList.add('hidden');
            document.getElementById('gitSettingsScreen').classList.remove('flex');
        }

        function checkGitIdentityForm() {
            const nameVal = document.getElementById('gitIdentityName').value.trim();
            const emailVal = document.getElementById('gitIdentityEmail').value.trim();
            const btn = document.getElementById('saveGitIdentityBtn');
            if (nameVal || emailVal) {
                btn.className = "w-full py-3 rounded-full bg-m3-primary text-m3-onPrimary text-[14.5px] font-bold flex items-center justify-center gap-2 transition-all expressive-btn shadow-md";
            } else {
                btn.className = "w-full py-3 rounded-full bg-[#24211D] text-[#6E6A63] text-[14.5px] font-semibold flex items-center justify-center gap-2 transition-all expressive-btn";
            }
        }

        function saveGitIdentity() {
            const nameVal = document.getElementById('gitIdentityName').value.trim() || 'Developer';
            showToast(`Saved Git identity for ${nameVal}`);
        }

        function checkGitCredForm() {
            const userVal = document.getElementById('gitCredUsernameInput').value.trim();
            const tokenVal = document.getElementById('gitCredTokenInput').value.trim();
            const btn = document.getElementById('saveGitCredBtn');
            if (userVal || tokenVal) {
                btn.className = "w-full py-3 rounded-full bg-m3-primary text-m3-onPrimary text-[14.5px] font-bold flex items-center justify-center gap-2 transition-all expressive-btn shadow-md";
            } else {
                btn.className = "w-full py-3 rounded-full bg-[#24211D] text-[#6E6A63] text-[14.5px] font-semibold flex items-center justify-center gap-2 transition-all expressive-btn";
            }
        }

        function toggleGitTokenVisibility() {
            const input = document.getElementById('gitCredTokenInput');
            const icon = document.getElementById('gitCredEyeIcon');
            if (input.type === 'password') {
                input.type = 'text';
                icon.textContent = 'visibility_off';
            } else {
                input.type = 'password';
                icon.textContent = 'visibility';
            }
        }

        function saveGitCredentials() {
            const hostVal = document.getElementById('gitCredHostInput').value.trim() || 'github.com';
            showToast(`Saved encrypted credentials for ${hostVal}`);
        }

