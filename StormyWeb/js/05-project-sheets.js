        // ============================================================================
        // PROJECT 3-DOTS CONTEXT SHEET, EDIT PROJECT & DELETE PROJECT (SCREENSHOTS 5, 6, 7)
        // ============================================================================
        function getActiveContextProject() {
            let target = state.projects.find(p => p.id === state.activeContextProjectId);
            if (!target && state.projects.length > 0) {
                target = state.projects[0];
                state.activeContextProjectId = target.id;
            }
            return target || { id: 'proj-fallback', name: 'Portfolio', description: '', dateLabel: 'Today', category: 'Web', pinned: false };
        }

        function openProjectMenuSheet(projId = null) {
            closeSpeedDial();
            if (projId) state.activeContextProjectId = projId;
            const proj = getActiveContextProject();

            document.getElementById('menuSheetProjectTitle').textContent = proj.name.toUpperCase();

            const pinLabel = document.getElementById('menuPinLabel');
            const pinIcon = document.getElementById('menuPinIcon');
            if (pinLabel) {
                pinLabel.textContent = proj.pinned ? 'Unpin from top' : 'Pin to top';
            }
            if (pinIcon) {
                pinIcon.className = proj.pinned
                    ? 'material-symbols-rounded filled text-[21px] text-m3-primary transition-colors'
                    : 'material-symbols-rounded text-[21px] text-[#EDE9E2] group-hover:text-m3-primary transition-colors';
            }

            document.getElementById('projectMenuOverlay').classList.remove('hidden');
        }

        function closeProjectMenuSheet() {
            document.getElementById('projectMenuOverlay').classList.add('hidden');
        }

        function togglePinFromMenu() {
            const proj = getActiveContextProject();
            closeProjectMenuSheet();
            togglePinProject(proj.id);
        }

        function openEditProjectFromMenu() {
            closeProjectMenuSheet();
            openEditProjectFlow(state.activeContextProjectId);
        }

        function openEditProjectFlow(projId = null) {
            closeSpeedDial();
            if (projId) state.activeContextProjectId = projId;
            const proj = getActiveContextProject();

            applyModalPresentationStyle();
            const nameInput = document.getElementById('editProjNameInput');
            const descInput = document.getElementById('editProjDescInput');
            nameInput.value = proj.name;
            descInput.value = proj.description || '';

            document.getElementById('editProjectOverlay').classList.remove('hidden');
            setTimeout(() => {
                nameInput.focus();
                nameInput.select();
            }, 60);
        }

        function closeEditProjectFlow() {
            document.getElementById('editProjectOverlay').classList.add('hidden');
        }

        function submitEditProject() {
            const proj = getActiveContextProject();
            const nameInput = document.getElementById('editProjNameInput');
            const descInput = document.getElementById('editProjDescInput');

            const newName = nameInput.value.trim() || 'Untitled Project';
            proj.name = newName;
            proj.description = descInput.value.trim();

            closeEditProjectFlow();
            renderProjects();
            showToast(`Renamed project to "${newName}"`);
        }

        function openDeleteProjectFromMenu() {
            closeProjectMenuSheet();
            openDeleteProjectFlow(state.activeContextProjectId);
        }

        function openDeleteProjectFlow(projId = null) {
            closeSpeedDial();
            if (projId) state.activeContextProjectId = projId;
            const proj = getActiveContextProject();

            applyModalPresentationStyle();
            document.getElementById('deleteProjTargetName').textContent = proj.name;
            document.getElementById('deleteProjectOverlay').classList.remove('hidden');
        }

        function closeDeleteProjectFlow() {
            document.getElementById('deleteProjectOverlay').classList.add('hidden');
        }

        function confirmDeleteProject() {
            const proj = getActiveContextProject();
            const deletedName = proj.name;

            state.projects = state.projects.filter(p => p.id !== proj.id);

            if (state.projects.length > 0) {
                state.activeContextProjectId = state.projects[0].id;
            }
            closeDeleteProjectFlow();
            renderProjects();
            showToast(`Deleted "${deletedName}"`);
        }

        // ============================================================================
        // MODAL PRESENTATION MODE SWITCHER (M3 BOTTOM SHEET VS 1:1 CENTERED DIALOG)
        // ============================================================================
        function applyModalPresentationStyle() {
            const newOverlay = document.getElementById('newProjectOverlay');
            const newCard = document.getElementById('newProjectCard');
            const newHandle = document.getElementById('newProjDragHandle');
            const newBadge = document.getElementById('newProjExpressiveBadge');
            const newCloseBtn = document.getElementById('newProjCloseIconBtn');

            const cloneOverlay = document.getElementById('cloneRepoOverlay');
            const cloneCard = document.getElementById('cloneRepoCard');
            const cloneHandle = document.getElementById('cloneDragHandle');
            const cloneHeroBg = document.getElementById('cloneHeroBgSvg');

            const editOverlay = document.getElementById('editProjectOverlay');
            const editCard = document.getElementById('editProjectCard');
            const editHandle = document.getElementById('editProjDragHandle');
            const editBadge = document.getElementById('editProjExpressiveBadge');
            const editSaveBtn = document.getElementById('editProjSaveBtn');

            const deleteOverlay = document.getElementById('deleteProjectOverlay');
            const deleteCard = document.getElementById('deleteProjectCard');
            const deleteHandle = document.getElementById('deleteProjDragHandle');
            const deleteBadge = document.getElementById('deleteProjExpressiveBadge');
            const deleteConfirmBtn = document.getElementById('deleteProjConfirmBtn');

            if (state.useBottomSheetStyle) {
                // Revamped M3 Expressive Bottom Sheet UX
                newOverlay.className = "hidden absolute inset-0 bg-black/65 backdrop-blur-[3px] z-50 flex items-end justify-center p-0 transition-all";
                newCard.className = "w-full bg-m3-surfaceModal rounded-t-[34px] px-5 pt-3 pb-6 border-t border-white/10 shadow-[0_-15px_50px_rgba(0,0,0,0.75)] animate-sheet-up max-h-[92%] overflow-y-auto no-scrollbar";
                newHandle.classList.remove('hidden');
                newBadge.classList.remove('hidden');
                newCloseBtn.classList.remove('hidden');

                cloneOverlay.className = "hidden absolute inset-0 bg-black/65 backdrop-blur-[3px] z-50 flex items-end justify-center p-0 transition-all";
                cloneCard.className = "w-full bg-m3-surfaceModal rounded-t-[34px] px-5 pt-3 pb-6 border-t border-white/10 shadow-[0_-15px_50px_rgba(0,0,0,0.75)] animate-sheet-up max-h-[92%] overflow-y-auto no-scrollbar";
                cloneHandle.classList.remove('hidden');
                cloneHeroBg.classList.remove('hidden');

                editOverlay.className = "hidden absolute inset-0 bg-black/65 backdrop-blur-[3px] z-50 flex items-end justify-center p-0 transition-all";
                editCard.className = "w-full bg-m3-surfaceModal rounded-t-[34px] px-6 pt-3 pb-6 border-t border-white/10 shadow-[0_-15px_50px_rgba(0,0,0,0.8)] animate-sheet-up";
                editHandle.classList.remove('hidden');
                editBadge.classList.remove('hidden');
                editSaveBtn.className = "px-6 py-2.5 rounded-[14px] bg-m3-primary text-m3-onPrimary text-[15px] font-bold hover:brightness-110 expressive-btn transition-all";

                deleteOverlay.className = "hidden absolute inset-0 bg-black/65 backdrop-blur-[3px] z-50 flex items-end justify-center p-0 transition-all";
                deleteCard.className = "w-full bg-m3-surfaceModal rounded-t-[34px] px-6 pt-3 pb-6 border-t border-white/10 shadow-[0_-15px_50px_rgba(0,0,0,0.8)] animate-sheet-up";
                deleteHandle.classList.remove('hidden');
                deleteBadge.classList.remove('hidden');
                deleteConfirmBtn.className = "px-5 py-2.5 rounded-[14px] bg-m3-errorContainer text-m3-onErrorContainer text-[15px] font-bold hover:brightness-110 expressive-btn transition-all";
            } else {
                // 1:1 Original Screenshot Centered Dialog Style
                newOverlay.className = "hidden absolute inset-0 bg-black/65 z-50 flex items-center justify-center px-4 transition-all";
                newCard.className = "w-full bg-m3-surfaceModal rounded-[28px] p-5 border border-white/5 shadow-2xl animate-modal-zoom max-h-[90%] overflow-y-auto no-scrollbar";
                newHandle.classList.add('hidden');
                newBadge.classList.add('hidden');
                newCloseBtn.classList.add('hidden');

                cloneOverlay.className = "hidden absolute inset-0 bg-black/65 z-50 flex items-center justify-center px-4 transition-all";
                cloneCard.className = "w-full bg-m3-surfaceModal rounded-[28px] p-5 border border-white/5 shadow-2xl animate-modal-zoom max-h-[90%] overflow-y-auto no-scrollbar";
                cloneHandle.classList.add('hidden');
                cloneHeroBg.classList.add('hidden');

                // Exact Screenshot 6 Centered Edit Project Dialog with Text Buttons ("Cancel" & "Save")
                editOverlay.className = "hidden absolute inset-0 bg-black/65 z-50 flex items-center justify-center px-5 transition-all";
                editCard.className = "w-full bg-m3-surfaceModal rounded-[28px] p-6 border border-white/5 shadow-2xl animate-modal-zoom";
                editHandle.classList.add('hidden');
                editBadge.classList.add('hidden');
                editSaveBtn.className = "px-2 py-2 rounded-full text-[15px] font-semibold text-m3-primary hover:bg-m3-primary/10 transition-colors";

                // Exact Screenshot 7 Centered Delete Project Dialog with Text Buttons ("Cancel" & coral "Delete")
                deleteOverlay.className = "hidden absolute inset-0 bg-black/65 z-50 flex items-center justify-center px-5 transition-all";
                deleteCard.className = "w-full bg-m3-surfaceModal rounded-[28px] p-6 border border-white/5 shadow-2xl animate-modal-zoom";
                deleteHandle.classList.add('hidden');
                deleteBadge.classList.add('hidden');
                deleteConfirmBtn.className = "px-2 py-2 rounded-full text-[15px] font-semibold text-m3-error hover:bg-m3-error/10 transition-colors";
            }
        }

        function toggleModalPresentationMode() {
            state.useBottomSheetStyle = !state.useBottomSheetStyle;
            const desc = document.getElementById('modalStyleDesc');
            const btn = document.getElementById('modalStyleBtn');

            if (state.useBottomSheetStyle) {
                desc.textContent = 'M3 Bottom Sheet';
                btn.textContent = '1:1 Dialog';
                showToast('Switched to Revamped M3 Expressive Bottom Sheet UX');
            } else {
                desc.textContent = '1:1 Centered Dialog';
                btn.textContent = 'M3 Sheet';
                showToast('Switched to 1:1 Screenshot Centered Dialogs');
            }

            // Re-apply live if any modal/sheet is currently open
            const isNewOpen = !document.getElementById('newProjectOverlay').classList.contains('hidden');
            const isCloneOpen = !document.getElementById('cloneRepoOverlay').classList.contains('hidden');
            const isEditOpen = !document.getElementById('editProjectOverlay').classList.contains('hidden');
            const isDeleteOpen = !document.getElementById('deleteProjectOverlay').classList.contains('hidden');
            const isMenuOpen = !document.getElementById('projectMenuOverlay').classList.contains('hidden');

            applyModalPresentationStyle();

            if (isNewOpen) document.getElementById('newProjectOverlay').classList.remove('hidden');
            if (isCloneOpen) document.getElementById('cloneRepoOverlay').classList.remove('hidden');
            if (isEditOpen) document.getElementById('editProjectOverlay').classList.remove('hidden');
            if (isDeleteOpen) document.getElementById('deleteProjectOverlay').classList.remove('hidden');
            if (isMenuOpen) openProjectMenuSheet(state.activeContextProjectId);
        }

