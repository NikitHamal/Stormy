        // ============================================================================
        // PROJECT EDITOR PANELS — CodeIt chat tab, model picker + chat settings
        // sheets, chat/agent mode switch
        // ============================================================================
        state.chat = {
            mode: 'agent',                  // 'chat' | 'agent'
            model: 'Agnes 3.0 Flash',
            agentMode: true,
            messages: [],                   // { role: 'user' | 'bot', text }
            sending: false
        };

        const INF_SVG = `<svg class="st-inf" style="--w:30px" viewBox="250 500 1110 650" aria-hidden="true"><path class="tk" d="M810 822C880 700 980 568 1105 568C1230 568 1295 700 1295 830C1295 960 1220 1082 1105 1082C980 1082 880 940 810 822C740 700 640 567 520 567C400 567 317 700 317 825C317 950 400 1082 520 1082C640 1082 740 940 810 822Z"/><path class="ch" pathLength="100" d="M810 822C880 700 980 568 1105 568C1230 568 1295 700 1295 830C1295 960 1220 1082 1105 1082C980 1082 880 940 810 822C740 700 640 567 520 567C400 567 317 700 317 825C317 950 400 1082 520 1082C640 1082 740 940 810 822Z"/></svg>`;
        const CODEIT_REPLIES = [
            'On it — describe the change and I will draft it for you.',
            'Got it. I can read and edit your project files directly.',
            'Nice idea. Tell me more and I will break it into steps.'
        ];
        let codeitReplyIdx = 0;

        // ---- chat render ------------------------------------------------------------
        function renderChat() {
            const box = document.getElementById('chatMessages');
            const name = document.getElementById('chatModelName');
            if (name) name.textContent = state.chat.model;
            if (state.chat.messages.length === 0 && !state.chat.sending) {
                const chatOn = state.chat.mode === 'chat';
                box.innerHTML = `
                    <div class="flex flex-col items-center text-center pt-8 pb-4">
                        <img src="assets/codeit_mascot.svg" alt="CodeIt mascot" draggable="false" class="w-28 h-28" />
                        <div class="text-[26px] font-extrabold text-[#EDE9E2] tracking-tight mt-5">Hi, I'm CodeIt</div>
                        <p class="text-[15.5px] text-[#8D877D] leading-snug mt-1.5 px-4">Your AI coding assistant. Tell me what you want to build, and I'll help you create it.</p>
                        <div class="bg-white/5 rounded-full p-1 flex mt-5 w-[250px]">
                            <button onclick="setChatMode('chat')"
                                class="flex-1 h-10 rounded-full flex items-center justify-center gap-1.5 text-[15px] font-medium ${chatOn ? 'ed-miniseg-on' : 'text-[#8D877D]'} expressive-btn transition-all">
                                <span class="material-symbols-rounded text-[20px]">chat_bubble_outline</span>Chat
                            </button>
                            <button onclick="setChatMode('agent')"
                                class="flex-1 h-10 rounded-full flex items-center justify-center gap-1.5 text-[15px] font-medium ${!chatOn ? 'ed-miniseg-on' : 'text-[#8D877D]'} expressive-btn transition-all">
                                <span class="material-symbols-rounded text-[20px]">auto_awesome</span>Agent
                            </button>
                        </div>
                    </div>`;
            } else {
                box.innerHTML = state.chat.messages.map((m) => {
                    if (m.role === 'user') {
                        return `<div class="flex justify-end mb-3">
                            <div class="ed-bubble-user max-w-[80%] rounded-[20px] rounded-br-[8px] px-4 py-2.5 text-[15.5px] leading-snug">${escapeHtml(m.text)}</div>
                        </div>`;
                    }
                    return `<div class="flex justify-start mb-3">
                        <div class="ed-bubble-bot max-w-[85%] rounded-[20px] rounded-bl-[8px] px-4 py-2.5 text-[15.5px] leading-snug">${escapeHtml(m.text)}</div>
                    </div>`;
                }).join('') + (state.chat.sending ? `
                    <div class="flex justify-start mb-3">
                        <div class="st-tk">
                            <div class="cli">${INF_SVG}<span class="v"></span><span class="h">tap to stop</span></div>
                            <div class="sh">Reading project files…</div>
                            <div class="bub"><i></i><i></i><i></i></div>
                        </div>
                    </div>` : '');
            }
            box.scrollTop = box.scrollHeight;
            renderAgentSwitch();
        }

        function setChatMode(mode) {
            state.chat.mode = mode;
            renderChat();
        }

        function onChatInput(val) {
            const btn = document.getElementById('chatSendBtn');
            btn.classList.toggle('ed-sendon', val.trim().length > 0);
            btn.classList.toggle('ed-sendoff', val.trim().length === 0);
        }

        function sendChatMessage() {
            const input = document.getElementById('chatInput');
            const text = input.value.trim();
            if (!text || state.chat.sending) return;
            state.chat.messages.push({ role: 'user', text });
            input.value = '';
            onChatInput('');
            state.chat.sending = true;
            renderChat();
            setTimeout(() => {
                state.chat.sending = false;
                state.chat.messages.push({ role: 'bot', text: CODEIT_REPLIES[codeitReplyIdx % CODEIT_REPLIES.length] });
                codeitReplyIdx += 1;
                renderChat();
            }, 900);
        }

        // ---- chat settings sheet ---------------------------------------------------------
        function openChatSettingsSheet() {
            renderAgentSwitch();
            document.getElementById('chatSettingsWrap').classList.remove('hidden');
        }

        function closeChatSettingsSheet() {
            document.getElementById('chatSettingsWrap').classList.add('hidden');
        }

        function renderAgentSwitch() {
            const sw = document.getElementById('agentModeSwitch');
            if (sw) sw.classList.toggle('ed-on', state.chat.agentMode);
        }

        function toggleAgentMode() {
            state.chat.agentMode = !state.chat.agentMode;
            renderAgentSwitch();
            showToast(state.chat.agentMode ? 'Agent mode on' : 'Agent mode off');
        }

        // ---- select model sheet --------------------------------------------------------------
        function openSelectModelSheet() {
            document.getElementById('selectModelWrap').classList.remove('hidden');
        }

        function closeSelectModelSheet() {
            document.getElementById('selectModelWrap').classList.add('hidden');
        }

        function selectChatModel(modelName) {
            state.chat.model = modelName;
            document.getElementById('chatModelName').textContent = modelName;
            closeSelectModelSheet();
            showToast(`Model: ${modelName}`);
        }

        function refreshModelList() {
            const icon = document.getElementById('modelRefreshIcon');
            if (icon) {
                icon.style.transform = 'rotate(360deg)';
                setTimeout(() => { icon.style.transform = 'rotate(0deg)'; }, 550);
            }
            showToast('Models up to date');
        }
