        // ============================================================================
        // FIT-TO-SCREEN + ZOOM (phone preview always fits viewport height by default)
        // ============================================================================
        let fitScale = 1;
        let currentZoom = 1;
        let isFitMode = true;

        function getFrameBaseSize() {
            const frame = document.getElementById('phoneFrame');
            // offsetWidth/Height includes the 8px border, i.e. true on-screen footprint
            return { w: frame.offsetWidth || 410, h: frame.offsetHeight || 824 };
        }

        function computeFitScale() {
            const stage = document.getElementById('previewStage');
            const { w: fw, h: fh } = getFrameBaseSize();
            const availW = stage.clientWidth - 16;
            const availH = stage.clientHeight - 8;
            if (fw <= 0 || fh <= 0 || availW <= 0 || availH <= 0) return 1;
            // Fit BOTH width and height, never upscale beyond 1 so default is minimized, not huge
            return Math.min(availW / fw, availH / fh, 1);
        }

        function applyZoom(scale) {
            currentZoom = Math.min(1.6, Math.max(0.35, scale));
            document.getElementById('phoneScaler').style.transform = `scale(${currentZoom})`;
            const label = document.getElementById('zoomLabel');
            if (label) label.textContent = Math.round(currentZoom * 100) + '%';
        }

        function refitToScreen() {
            fitScale = computeFitScale();
            isFitMode = true;
            applyZoom(fitScale);
        }

        function resetFit() {
            refitToScreen();
            showToast(`Fit to screen (${Math.round(fitScale * 100)}%)`);
        }

        function zoomIn() {
            isFitMode = false;
            applyZoom(currentZoom + 0.1);
        }

        function zoomOut() {
            isFitMode = false;
            applyZoom(currentZoom - 0.1);
        }

        window.addEventListener('resize', () => {
            // Keep it fittable: if user hasn't manually zoomed, stay locked to fit.
            // If they did zoom, only shrink down when viewport gets smaller than the zoomed frame.
            if (isFitMode) {
                refitToScreen();
            } else {
                const fit = computeFitScale();
                fitScale = fit;
                if (currentZoom > fit) applyZoom(fit);
            }
        });

        // Ctrl/Cmd + wheel = zoom, double-click stage = toggle fit / 100%
        document.addEventListener('wheel', (e) => {
            if (!e.ctrlKey && !e.metaKey) return;
            e.preventDefault();
            isFitMode = false;
            applyZoom(currentZoom + (e.deltaY < 0 ? 0.06 : -0.06));
        }, { passive: false });

        document.addEventListener('keydown', (e) => {
            if ((e.ctrlKey || e.metaKey) && (e.key === '+' || e.key === '=')) { e.preventDefault(); zoomIn(); }
            if ((e.ctrlKey || e.metaKey) && e.key === '-') { e.preventDefault(); zoomOut(); }
            if ((e.ctrlKey || e.metaKey) && e.key === '0') { e.preventDefault(); resetFit(); }
        });

        // ============================================================================
        // ANDROID PRESS-ONLY: strip every hover: / group-hover: utility inside the phone
        // (native Android has no hover — only :active press. Toolbar above phone keeps hover.)
        // Covers both static markup and dynamically rendered lists via MutationObserver.
        // ============================================================================
        function stripHoverClasses(root) {
            if (!root) return;
            const els = root.querySelectorAll('[class*="hover:"]');
            els.forEach((el) => {
                const cls = el.getAttribute('class');
                if (!cls || !cls.includes('hover:')) return;
                const cleaned = cls.split(/\s+/).filter((c) => !c.includes('hover:')).join(' ');
                if (cleaned !== cls) el.setAttribute('class', cleaned);
            });
        }

        function initAndroidNoHover() {
            const phone = document.getElementById('phoneFrame');
            if (!phone) return;
            stripHoverClasses(phone);
            const obs = new MutationObserver((mutations) => {
                let dirty = false;
                for (const m of mutations) {
                    if (m.type === 'attributes' && m.attributeName === 'class') {
                        const c = m.target.getAttribute && m.target.getAttribute('class');
                        if (c && c.includes('hover:')) { dirty = true; break; }
                    } else if (m.type === 'childList') {
                        for (const n of m.addedNodes) {
                            if (n.nodeType !== 1) continue;
                            const c = n.getAttribute && n.getAttribute('class');
                            if ((c && c.includes('hover:')) || (n.querySelector && n.querySelector('[class*="hover:"]'))) {
                                dirty = true; break;
                            }
                        }
                        if (dirty) break;
                    }
                }
                if (dirty) stripHoverClasses(phone);
            });
            obs.observe(phone, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
        }
