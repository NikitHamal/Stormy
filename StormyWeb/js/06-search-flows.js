        // ============================================================================
        // TOPBAR IN-SCREEN SEARCH & FILTER LOGIC
        // ============================================================================
        function openSearch() {
            closeSpeedDial();
            state.isSearchOpen = true;
            const defaultBar = document.getElementById('defaultBar');
            const searchLayer = document.getElementById('expressiveSearchContainer');
            const chipsStrip = document.getElementById('chipsStrip');
            const input = document.getElementById('projectSearchInput');

            // Reference cross-fade: default layer out (up + shrink), search layer in (from below)
            if (defaultBar) {
                defaultBar.style.opacity = '0';
                defaultBar.style.transform = 'translateY(-12px) scale(0.98)';
                defaultBar.style.pointerEvents = 'none';
            }
            if (searchLayer) {
                searchLayer.style.opacity = '1';
                searchLayer.style.transform = 'translateY(0) scale(1)';
                searchLayer.style.pointerEvents = 'auto';
            }
            // Reference chips strip reveal (delayed 0.08s via CSS) + current icon chips stagger
            if (chipsStrip) {
                chipsStrip.style.opacity = '1';
                chipsStrip.style.transform = 'translateY(0)';
                chipsStrip.style.maxHeight = '56px';
                chipsStrip.style.pointerEvents = 'auto';
            }
            staggerFilterChips();

            renderProjects();
            setTimeout(() => { if (input) input.focus(); }, 120);
        }

        function staggerFilterChips() {
            const chips = document.querySelectorAll('#filterChipsRow .m3-chip');
            chips.forEach((chip, i) => {
                chip.classList.remove('chip-enter');
                chip.style.animationDelay = `${i * 40}ms`;
                // re-trigger entrance each time search opens
                void chip.offsetWidth;
                chip.classList.add('chip-enter');
            });
        }

        function closeSearch() {
            state.isSearchOpen = false;
            const defaultBar = document.getElementById('defaultBar');
            const searchLayer = document.getElementById('expressiveSearchContainer');
            const chipsStrip = document.getElementById('chipsStrip');
            const legacyRow = document.getElementById('topbarDefaultRow');

            if (defaultBar) {
                defaultBar.style.opacity = '1';
                defaultBar.style.transform = 'translateY(0) scale(1)';
                defaultBar.style.pointerEvents = 'auto';
            }
            if (searchLayer) {
                searchLayer.style.opacity = '0';
                searchLayer.style.transform = 'translateY(12px) scale(0.96)';
                searchLayer.style.pointerEvents = 'none';
            }
            if (chipsStrip) {
                chipsStrip.style.opacity = '0';
                chipsStrip.style.transform = 'translateY(-8px)';
                chipsStrip.style.maxHeight = '0';
                chipsStrip.style.pointerEvents = 'none';
            }
            if (legacyRow) legacyRow.classList.remove('hidden');
            const advPanel = document.getElementById('advancedFilterPanel');
            if (advPanel) { advPanel.classList.add('hidden'); advPanel.classList.remove('flex'); }
            state.isAdvancedFilterOpen = false;
            renderProjects();
        }

        function handleSearchInput(val) {
            state.searchQuery = val;
            const clearBtn = document.getElementById('clearTextBtn');
            if (val.trim().length > 0) {
                clearBtn.classList.remove('hidden');
                clearBtn.classList.add('flex');
            } else {
                clearBtn.classList.add('hidden');
                clearBtn.classList.remove('flex');
            }
            renderProjects();
        }

        function clearSearchText() {
            const input = document.getElementById('projectSearchInput');
            input.value = '';
            handleSearchInput('');
            input.focus();
        }

        function setQuickFilter(filterName) {
            state.activeFilter = filterName;
            const iconMap = {
                'all': 'apps',
                'Today': 'today',
                'Web': 'html',
                'Frameworks': 'deployed_code',
                'Git': 'cloud_done',
                'Pinned': 'keep'
            };

            document.querySelectorAll('#filterChipsRow .m3-chip').forEach(btn => {
                const chipFilter = btn.getAttribute('data-filter');
                const iconEl = btn.querySelector('.chip-icon');
                if (chipFilter === filterName) {
                    btn.classList.add('selected');
                    if (iconEl) iconEl.textContent = 'check';
                } else {
                    btn.classList.remove('selected');
                    if (iconEl && iconMap[chipFilter]) iconEl.textContent = iconMap[chipFilter];
                }
            });

            renderProjects();
        }

        function toggleAdvancedFilterPanel() {
            state.isAdvancedFilterOpen = !state.isAdvancedFilterOpen;
            const panel = document.getElementById('advancedFilterPanel');
            panel.classList.toggle('hidden', !state.isAdvancedFilterOpen);
            panel.classList.toggle('flex', state.isAdvancedFilterOpen);
        }

        function setSortOrder(order) {
            state.sortOrder = order;
            document.querySelectorAll('.sort-btn').forEach(btn => {
                btn.className = "sort-btn py-1.5 px-2 rounded-xl text-[11px] font-medium text-m3-onSurfaceVariant hover:text-white flex items-center justify-center gap-1 transition-all";
            });
            const activeBtn = document.getElementById(`sort-${order}`);
            if (activeBtn) {
                activeBtn.className = "sort-btn py-1.5 px-2 rounded-xl text-[11px] font-semibold bg-m3-surfaceHighest text-m3-primary flex items-center justify-center gap-1 transition-all";
            }
            renderProjects();
        }

        function resetAllFilters() {
            state.searchQuery = '';
            state.sortOrder = 'recent';
            const input = document.getElementById('projectSearchInput');
            if (input) input.value = '';
            document.getElementById('clearTextBtn').classList.add('hidden');
            setQuickFilter('all');
            setSortOrder('recent');
            renderProjects();
            showToast('Search & filters reset');
        }

        // ============================================================================
        // RIGHT PANEL FLOW LAUNCHER & ENVIRONMENT SWITCHES
        // ============================================================================
        function launchFlowPreview(flowName) {
            // Close all overlays first
            closeNewProjectFlow();
            closeCloneRepoFlow();
            closeImportFolderFlow();
            closeSettingsSheet();
            closeProjectMenuSheet();
            closeEditProjectFlow();
            closeDeleteProjectFlow();
            closeSpeedDial();

            // Highlight active flow button
            const flows = ['home', 'search', 'speeddial', 'newproject', 'clonerepo', 'projectmenu', 'editproject', 'deleteproject'];
            flows.forEach(f => {
                const btn = document.getElementById(`flowBtn-${f}`);
                if (!btn) return;
                if (f === flowName) {
                    btn.className = "flow-nav-btn p-2.5 rounded-2xl bg-m3-primary text-m3-onPrimary font-bold text-xs flex flex-col items-center gap-1 transition-all expressive-btn";
                    const icon = btn.querySelector('.material-symbols-rounded');
                    if (icon) {
                        icon.classList.remove('text-m3-primary', 'text-m3-error');
                    }
                } else {
                    btn.className = "flow-nav-btn p-2.5 rounded-2xl bg-m3-surfaceHigh hover:bg-m3-surfaceHighest text-white font-semibold text-xs flex flex-col items-center gap-1 border border-white/10 transition-all expressive-btn";
                    const icon = btn.querySelector('.material-symbols-rounded');
                    if (icon) {
                        if (f === 'deleteproject') {
                            icon.classList.add('text-m3-error');
                        } else {
                            icon.classList.add('text-m3-primary');
                        }
                    }
                }
            });

            if (flowName === 'home') {
                closeSearch();
            } else if (flowName === 'search') {
                openSearch();
            } else if (flowName === 'speeddial') {
                closeSearch();
                toggleSpeedDial(true);
            } else if (flowName === 'newproject') {
                openNewProjectFlow();
            } else if (flowName === 'clonerepo') {
                openCloneRepoFlow();
            } else if (flowName === 'projectmenu') {
                closeSearch();
                openProjectMenuSheet();
            } else if (flowName === 'editproject') {
                closeSearch();
                openEditProjectFlow();
            } else if (flowName === 'deleteproject') {
                closeSearch();
                openDeleteProjectFlow();
            }
        }

        function selectSearchPattern(patternNum) {
            state.selectedPattern = patternNum;
            for (let i = 1; i <= 4; i++) {
                const card = document.getElementById(`patternCard-${i}`);
                if (i === patternNum) {
                    card.className = "pattern-card text-left p-3.5 rounded-2xl bg-m3-surfaceHigh border-2 border-m3-primary transition-all hover:brightness-110";
                } else {
                    card.className = "pattern-card text-left p-3.5 rounded-2xl bg-m3-bg/60 border-2 border-white/10 transition-all hover:border-white/25";
                }
            }
            launchFlowPreview('search');
            showToast(`Previewing Topbar Search Pattern ${patternNum}`);
        }

        function toggleBrandHeaderMode() {
            state.showLogoAndAppName = !state.showLogoAndAppName;
            const logoBadge = document.getElementById('appLogoBadge');
            const appNameSub = document.getElementById('appNameSub');
            const brandModeDesc = document.getElementById('brandModeDesc');
            const brandSwitchBtn = document.getElementById('brandSwitchBtn');

            if (state.showLogoAndAppName) {
                if (logoBadge) logoBadge.classList.remove('hidden');
                if (appNameSub) appNameSub.classList.remove('hidden');
                if (brandModeDesc) brandModeDesc.textContent = 'Logo + App Name';
                if (brandSwitchBtn) brandSwitchBtn.textContent = 'Title Only';
            } else {
                if (logoBadge) logoBadge.classList.add('hidden');
                if (appNameSub) appNameSub.classList.add('hidden');
                if (brandModeDesc) brandModeDesc.textContent = '"Projects" only';
                if (brandSwitchBtn) brandSwitchBtn.textContent = '+ App Logo';
            }
        }

        function togglePinProject(projId) {
            const target = state.projects.find(p => p.id === projId);
            if (target) {
                target.pinned = !target.pinned;
                renderProjects();
                showToast(target.pinned ? `Pinned "${target.name}"` : `Unpinned "${target.name}"`);
            }
        }

        let toastTimer = null;
        function showToast(msg) {
            const toast = document.getElementById('phoneToast');
            toast.textContent = msg;
            toast.classList.remove('opacity-0', 'translate-y-2');
            toast.classList.add('opacity-100', 'translate-y-0');
            clearTimeout(toastTimer);
            toastTimer = setTimeout(() => {
                toast.classList.add('opacity-0', 'translate-y-2');
                toast.classList.remove('opacity-100', 'translate-y-0');
            }, 2200);
        }

        // Escape key closes active modal, speed-dial, or search
        window.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                closeNewProjectFlow();
                closeCloneRepoFlow();
                closeImportFolderFlow();
                closeSettingsSheet();
                closeProjectMenuSheet();
                closeEditProjectFlow();
                closeDeleteProjectFlow();
                if (state.isSpeedDialOpen) closeSpeedDial();
                else if (state.isSearchOpen) closeSearch();
            }
        });

        window.onload = function () {
            initAndroidNoHover();
            applyThemeToPhone();
            renderProjects();
            renderTemplateStudio();
            stripHoverClasses(document.getElementById('phoneFrame'));
            // Default: minimized + fit to screen height (not huge)
            requestAnimationFrame(() => refitToScreen());
            setTimeout(() => refitToScreen(), 120);
        };
