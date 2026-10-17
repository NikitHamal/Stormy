        // ============================================================================
        // HELPER & RENDERING FUNCTIONS
        // ============================================================================
        function escapeHtml(str) {
            return String(str).replace(/[&<>"']/g, m => ({
                '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
            }[m]));
        }

        function highlightMatch(text, query) {
            const safeText = escapeHtml(text);
            if (!query || !query.trim()) return safeText;
            const escapedQuery = query.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
            const regex = new RegExp(`(${escapedQuery})`, 'gi');
            return safeText.replace(regex, '<mark class="m3-highlight">$1</mark>');
        }

        function getFilteredProjects() {
            let list = [...state.projects];

            if (state.activeFilter !== 'all') {
                if (state.activeFilter === 'Today') {
                    list = list.filter(p => p.dateLabel.toLowerCase() === 'today');
                } else if (state.activeFilter === 'Pinned') {
                    list = list.filter(p => p.pinned);
                } else if (state.activeFilter === 'Git') {
                    list = list.filter(p => p.isCloned || p.category === 'Git');
                } else {
                    list = list.filter(p => p.category.toLowerCase() === state.activeFilter.toLowerCase());
                }
            }

            if (state.searchQuery.trim() !== '') {
                const q = state.searchQuery.toLowerCase().trim();
                list = list.filter(p =>
                    p.name.toLowerCase().includes(q) ||
                    p.dateLabel.toLowerCase().includes(q) ||
                    p.category.toLowerCase().includes(q) ||
                    (p.templateName && p.templateName.toLowerCase().includes(q))
                );
            }

            if (state.sortOrder === 'az') {
                list.sort((a, b) => a.name.localeCompare(b.name));
            } else if (state.sortOrder === 'template') {
                list.sort((a, b) => (a.templateName || '').localeCompare(b.templateName || ''));
            }

            return list;
        }

        function renderProjects() {
            const container = document.getElementById('projectsListContainer');
            const emptyState = document.getElementById('emptySearchState');
            const libraryEmptyState = document.getElementById('emptyLibraryState');
            const statusSubheader = document.getElementById('searchStatusSubheader');
            const resultsCountLabel = document.getElementById('resultsCountLabel');
            const closedFilterDot = document.getElementById('closedFilterDot');
            const activeFilterCountBadge = document.getElementById('activeFilterCountBadge');

            const filtered = getFilteredProjects();
            const hasActiveFilters = state.activeFilter !== 'all' || state.searchQuery.trim() !== '' || state.sortOrder !== 'recent';

            if (closedFilterDot) closedFilterDot.style.display = (!hasActiveFilters || state.isSearchOpen) ? 'none' : 'block';

            let filterCount = 0;
            if (state.activeFilter !== 'all') filterCount++;
            if (state.sortOrder !== 'recent') filterCount++;
            if (filterCount > 0) {
                activeFilterCountBadge.textContent = filterCount;
                activeFilterCountBadge.classList.remove('hidden');
                activeFilterCountBadge.classList.add('flex');
            } else {
                activeFilterCountBadge.classList.add('hidden');
                activeFilterCountBadge.classList.remove('flex');
            }

            if (state.isSearchOpen || hasActiveFilters) {
                statusSubheader.classList.remove('hidden');
                statusSubheader.classList.add('flex');
                resultsCountLabel.textContent = `${filtered.length} ${filtered.length === 1 ? 'project' : 'projects'} found`;
            } else {
                statusSubheader.classList.add('hidden');
                statusSubheader.classList.remove('flex');
            }

            // True empty library (zero projects) gets its own best-in-class state
            if (state.projects.length === 0) {
                container.innerHTML = '';
                emptyState.classList.add('hidden');
                emptyState.classList.remove('flex');
                statusSubheader.classList.add('hidden');
                statusSubheader.classList.remove('flex');
                if (libraryEmptyState) { libraryEmptyState.classList.remove('hidden'); libraryEmptyState.classList.add('flex'); }
                return;
            }

            if (filtered.length === 0) {
                container.innerHTML = '';
                if (libraryEmptyState) { libraryEmptyState.classList.add('hidden'); libraryEmptyState.classList.remove('flex'); }
                emptyState.classList.remove('hidden');
                emptyState.classList.add('flex');
                return;
            }

            if (libraryEmptyState) { libraryEmptyState.classList.add('hidden'); libraryEmptyState.classList.remove('flex'); }
            emptyState.classList.add('hidden');
            emptyState.classList.remove('flex');

            const showExtraBadges = state.isSearchOpen || state.projects.length > 1;

            container.innerHTML = filtered.map((proj) => {
                const initial = proj.name.charAt(0).toUpperCase();
                const highlightedTitle = highlightMatch(proj.name, state.searchQuery);
                const highlightedDate = highlightMatch(proj.dateLabel, state.searchQuery);

                return `
                    <div onclick="openEditor('${proj.id}')"
                        class="group relative w-full bg-m3-surface hover:bg-[#24211D] rounded-[26px] px-[18px] py-[20px] flex items-center justify-between transition-all duration-200 cursor-pointer border border-transparent hover:border-white/5 active:scale-[0.985]">
                        
                        <div class="flex items-center gap-4 min-w-0">
                            <!-- Circular Avatar matching Screenshot ("P" in #38342D) -->
                            <div style="background-color: ${proj.avatarBg}; color: ${proj.avatarText};"
                                class="w-[46px] h-[46px] rounded-full flex items-center justify-center text-[18px] font-semibold shrink-0 shadow-inner">
                                ${initial}
                            </div>

                            <!-- Project Name & Date/Template Subtitle -->
                            <div class="flex flex-col min-w-0">
                                <div class="flex items-center gap-2">
                                    <span class="text-[17px] font-semibold text-[#EDE9E2] truncate leading-snug">
                                        ${highlightedTitle}
                                    </span>
                                    ${showExtraBadges && proj.pinned ? `
                                        <span class="material-symbols-rounded filled text-[14px] text-m3-primary shrink-0" title="Pinned">keep</span>
                                    ` : ''}
                                    ${showExtraBadges && proj.isCloned ? `
                                        <span class="material-symbols-rounded text-[14px] text-emerald-400 shrink-0" title="Git Cloned">cloud_done</span>
                                    ` : ''}
                                </div>
                                <div class="flex items-center gap-2 mt-0.5">
                                    <span class="text-[13.5px] text-[#8D877D] leading-tight">${highlightedDate}</span>
                                    ${showExtraBadges && proj.templateName ? `
                                        <span class="text-[10px] px-2 py-0.5 rounded-md bg-white/5 text-[#A8A29A] font-medium">${escapeHtml(proj.templateName)}</span>
                                    ` : ''}
                                </div>
                            </div>
                        </div>

                        <!-- Right 3-dots menu button (Opens Screenshot 5 Project Context Menu Sheet) -->
                        <button onclick="event.stopPropagation(); openProjectMenuSheet('${proj.id}')"
                            aria-label="Project options"
                            class="w-9 h-9 rounded-full hover:bg-white/10 flex items-center justify-center text-[#B8B2A8] shrink-0 transition-colors"
                            title="Project options (Rename / Delete)">
                            <span class="material-symbols-rounded text-[22px]">more_vert</span>
                        </button>
                    </div>
                `;
            }).join('');
        }

        // ============================================================================
        // SPEED-DIAL FAB MENU LOGIC (SCREENSHOT 1)
        // ============================================================================
        function toggleSpeedDial(forceState = null) {
            state.isSpeedDialOpen = forceState !== null ? forceState : !state.isSpeedDialOpen;
            const menu = document.getElementById('speedDialMenu');
            const scrim = document.getElementById('speedDialScrim');
            const fabIcon = document.getElementById('mainFabIcon');
            const fabSvg = document.getElementById('mainFabSvg');

            if (state.isSpeedDialOpen) {
                menu.classList.remove('hidden');
                menu.classList.add('flex');
                scrim.classList.remove('hidden');
                fabIcon.textContent = 'close';
                fabIcon.style.transform = 'rotate(90deg)';
                fabSvg.style.transform = 'rotate(45deg)';
            } else {
                menu.classList.add('hidden');
                menu.classList.remove('flex');
                scrim.classList.add('hidden');
                fabIcon.textContent = 'add';
                fabIcon.style.transform = 'rotate(0deg)';
                fabSvg.style.transform = 'rotate(0deg)';
            }
        }

        function closeSpeedDial() {
            toggleSpeedDial(false);
        }

        // ============================================================================
        // NEW PROJECT TEMPLATE STUDIO LOGIC (SCREENSHOTS 2 & 3)
        // ============================================================================
        function renderTemplateStudio() {
            const cardsRow = document.getElementById('templateCardsRow');
            const countBadge = document.getElementById('templateCountBadge');

            // All templates loaded by default (no category filter)
            const visibleTemplates = TEMPLATES_DATA;

            countBadge.textContent = `${visibleTemplates.length} ${visibleTemplates.length === 1 ? 'template' : 'templates'}`;

            // Ensure a valid template is selected
            if (!visibleTemplates.some(t => t.id === state.selectedTemplateId) && visibleTemplates.length > 0) {
                state.selectedTemplateId = visibleTemplates[0].id;
            }

            // 2. Render Horizontal Template Cards (all templates, improved legibility)
            cardsRow.innerHTML = visibleTemplates.map(tpl => {
                const isSelected = state.selectedTemplateId === tpl.id;
                const cardClasses = isSelected
                    ? 'bg-m3-primaryContainer border-2 border-m3-primary text-[#EDE9E2] shadow-[0_6px_18px_rgba(0,76,105,0.5)]'
                    : 'bg-m3-surfaceDeep/50 border border-m3-outline/70 text-[#B8B2A8] hover:border-white/30 hover:bg-m3-surfaceHigh/50';

                return `
                    <button type="button" onclick="selectTemplateCard('${tpl.id}')"
                        class="w-[104px] h-[94px] rounded-[18px] p-2.5 flex flex-col items-center justify-center gap-2 shrink-0 transition-all duration-200 expressive-btn ${cardClasses}">
                        ${tpl.isTextHero ? `
                            <span class="text-[13px] font-extrabold tracking-tight ${isSelected ? 'text-white' : 'text-[#EDE9E2]'} h-7 flex items-center">
                                ${escapeHtml(tpl.heroText)}
                            </span>
                        ` : `
                            <span class="material-symbols-rounded text-[26px] ${isSelected ? 'text-m3-primary' : 'text-[#B8B2A8]'}">
                                ${tpl.icon}
                            </span>
                        `}
                        <span class="text-[12px] font-medium truncate w-full text-center leading-tight">
                            ${escapeHtml(tpl.shortLabel)}
                        </span>
                    </button>
                `;
            }).join('');

            // 3. Update Selected Template Description Box below cards
            const currentTpl = TEMPLATES_DATA.find(t => t.id === state.selectedTemplateId) || TEMPLATES_DATA[1];
            document.getElementById('selectedTemplateTitle').textContent = currentTpl.name;
            document.getElementById('selectedTemplateDesc').textContent = currentTpl.description;
            document.getElementById('selectedTemplateBadge').textContent = currentTpl.badge;
            document.getElementById('selectedTemplatePreviewIcon').textContent = currentTpl.icon;
        }

        function selectTemplateCategory(category) {
            // Category filter removed — all templates always shown
            state.selectedTemplateCategory = 'All';
            renderTemplateStudio();
        }

        function selectTemplateCard(tplId) {
            state.selectedTemplateId = tplId;
            renderTemplateStudio();
        }

        function openNewProjectFlow() {
            closeSpeedDial();
            applyModalPresentationStyle();
            renderTemplateStudio();
            document.getElementById('newProjectOverlay').classList.remove('hidden');
            setTimeout(() => document.getElementById('newProjNameInput').focus(), 60);
        }

        function closeNewProjectFlow() {
            document.getElementById('newProjectOverlay').classList.add('hidden');
        }

        function submitNewProject() {
            const nameInput = document.getElementById('newProjNameInput');
            const descInput = document.getElementById('newProjDescInput');
            const currentTpl = TEMPLATES_DATA.find(t => t.id === state.selectedTemplateId) || TEMPLATES_DATA[1];

            const projName = nameInput.value.trim() || `${currentTpl.name} App`;
            const newProj = {
                id: 'proj-' + Date.now(),
                name: projName,
                dateLabel: 'Today',
                category: currentTpl.category,
                templateName: currentTpl.name,
                pinned: false,
                isCloned: false,
                avatarBg: '#38342D',
                avatarText: '#EDE9E2'
            };

            state.projects.unshift(newProj);
            nameInput.value = '';
            descInput.value = '';
            closeNewProjectFlow();
            renderProjects();
            showToast(`Created "${projName}" (${currentTpl.name})`);
        }

        // ============================================================================
        // CLONE REPOSITORY FLOW LOGIC (SCREENSHOT 4)
        // ============================================================================
        function openCloneRepoFlow() {
            closeSpeedDial();
            applyModalPresentationStyle();
            document.getElementById('cloneRepoOverlay').classList.remove('hidden');
            setTimeout(() => document.getElementById('cloneRepoUrlInput').focus(), 60);
        }

        function closeCloneRepoFlow() {
            document.getElementById('cloneRepoOverlay').classList.add('hidden');
        }

        function autoFillCloneName(urlVal) {
            const nameInput = document.getElementById('cloneProjNameInput');
            const cleaned = urlVal.trim().replace(/\/+$/, '').replace(/\.git$/i, '');
            const parts = cleaned.split('/');
            const lastSegment = parts[parts.length - 1];
            if (lastSegment && ! lastSegment.includes(':')) {
                nameInput.value = lastSegment;
            }
        }

        function prefillCloneRepo(url, name) {
            document.getElementById('cloneRepoUrlInput').value = url;
            document.getElementById('cloneProjNameInput').value = name;
        }

        function toggleShallowClone() {
            state.shallowClone = !state.shallowClone;
            const box = document.getElementById('shallowCloneCheckbox');
            const icon = document.getElementById('shallowCloneCheckIcon');
            if (state.shallowClone) {
                box.className = "w-6 h-6 rounded-[6px] bg-m3-primary text-m3-onPrimary flex items-center justify-center shrink-0 transition-all";
                icon.classList.remove('hidden');
            } else {
                box.className = "w-6 h-6 rounded-[6px] border-2 border-m3-outline bg-transparent flex items-center justify-center shrink-0 transition-all";
                icon.classList.add('hidden');
            }
        }

        function submitCloneRepo() {
            const urlInput = document.getElementById('cloneRepoUrlInput');
            const nameInput = document.getElementById('cloneProjNameInput');
            const repoName = nameInput.value.trim() || 'cloned-repository';

            const newProj = {
                id: 'proj-' + Date.now(),
                name: repoName,
                dateLabel: 'Today',
                category: 'Git',
                templateName: state.shallowClone ? 'Git (Shallow)' : 'Git Repo',
                pinned: false,
                isCloned: true,
                avatarBg: '#2E2B25',
                avatarText: '#B9B2A4'
            };

            state.projects.unshift(newProj);
            urlInput.value = '';
            nameInput.value = '';
            closeCloneRepoFlow();
            renderProjects();
            showToast(`Cloned "${repoName}" into Projects`);
        }

        // ============================================================================
        // IMPORT FOLDER & SETTINGS SHEETS
        // ============================================================================
        function openImportFolderFlow() {
            closeSpeedDial();
            document.getElementById('importFolderOverlay').classList.remove('hidden');
        }

        function closeImportFolderFlow() {
            document.getElementById('importFolderOverlay').classList.add('hidden');
        }

        function selectImportFolderItem(folderName, category) {
            state.projects.unshift({
                id: 'proj-' + Date.now(),
                name: folderName,
                dateLabel: 'Today',
                category: category,
                templateName: 'Imported',
                pinned: false,
                isCloned: false,
                avatarBg: '#3E3A32',
                avatarText: '#D8D2C4'
            });
            closeImportFolderFlow();
            renderProjects();
            showToast(`Imported folder "${folderName}"`);
        }

