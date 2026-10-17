        // ============================================================================
        // TEMPLATES CATALOG (Exact match to Screenshots 2 & 3 + Rich Categories)
        // ============================================================================
        const TEMPLATE_CATEGORIES = ['All', 'Web', 'Frameworks', 'Mobile/Native', 'Games', 'Backend'];

        const TEMPLATES_DATA = [
            {
                id: 'tpl-blank',
                name: 'Blank',
                shortLabel: 'Blank',
                category: 'Web',
                badge: 'EMPTY',
                icon: 'description',
                description: 'Empty workspace with zero boilerplate files'
            },
            {
                id: 'tpl-html',
                name: 'Basic HTML',
                shortLabel: 'Basic HTML',
                category: 'Web',
                badge: 'HTML5',
                isTextHero: true,
                heroText: 'HTML',
                icon: 'html',
                description: 'Simple HTML5 boilerplate'
            },
            {
                id: 'tpl-tailwind',
                name: 'Tailwind CSS',
                shortLabel: 'Tailwind',
                category: 'Web',
                badge: 'CSS',
                icon: 'palette',
                description: 'Utility-first Tailwind CSS starter template'
            },
            {
                id: 'tpl-portfolio',
                name: 'Portfolio',
                shortLabel: 'Portfolio',
                category: 'Web',
                badge: 'SITE',
                icon: 'person',
                description: 'Personal developer portfolio & showcase layout'
            },
            {
                id: 'tpl-blog',
                name: 'Blog',
                shortLabel: 'Blog',
                category: 'Web',
                badge: 'MDX',
                icon: 'article',
                description: 'Clean typography blog & documentation starter'
            },
            {
                id: 'tpl-dashboard',
                name: 'Dashboard',
                shortLabel: 'Dashboard',
                category: 'Frameworks',
                badge: 'UI',
                icon: 'dashboard',
                description: 'Responsive analytics dashboard with bento cards'
            },
            {
                id: 'tpl-react',
                name: 'React + Vite',
                shortLabel: 'React Vite',
                category: 'Frameworks',
                badge: 'JSX',
                icon: 'deployed_code',
                description: 'Fast React 19 + Vite + TypeScript setup'
            },
            {
                id: 'tpl-native',
                name: 'Kotlin Compose',
                shortLabel: 'Compose UI',
                category: 'Mobile/Native',
                badge: 'ANDROID',
                icon: 'phone_android',
                description: 'Material 3 Expressive Android Jetpack Compose starter'
            },
            {
                id: 'tpl-game',
                name: '2D Canvas Game',
                shortLabel: '2D Game',
                category: 'Games',
                badge: 'GAME',
                icon: 'sports_esports',
                description: 'HTML5 Canvas & WebGL arcade game loop starter'
            },
            {
                id: 'tpl-backend',
                name: 'Node REST API',
                shortLabel: 'Node API',
                category: 'Backend',
                badge: 'API',
                icon: 'dns',
                description: 'Express / Fastify REST service with JSON routes'
            }
        ];

        // ============================================================================
        // INITIAL PROJECT DATASETS (Exact 1-item Screenshot vs 6-item Rich Demo)
        // ============================================================================
        const screenshotProject = [
            {
                id: 'proj-1',
                name: 'Portfolio',
                description: '',
                dateLabel: 'Today',
                category: 'Web',
                templateName: 'Portfolio',
                pinned: true,
                isCloned: false,
                avatarBg: '#38342D',
                avatarText: '#EDE9E2'
            }
        ];

        // ============================================================================
        // APPLICATION STATE
        // ============================================================================
        let state = {
            projects: [...screenshotProject],
            isSearchOpen: false,
            searchQuery: '',
            activeFilter: 'all',          // 'all', 'Today', 'Web', 'Frameworks', 'Git', 'Pinned'
            sortOrder: 'recent',          // 'recent', 'az', 'template'
            selectedPattern: 1,           // 1, 2, 3, 4
            showLogoAndAppName: false,
            isAdvancedFilterOpen: false,
            isSpeedDialOpen: false,
            useBottomSheetStyle: true,    // true = Revamped M3 Expressive Bottom Sheet, false = 1:1 Centered Modal
            // New Project Modal State (Defaults to "All" + "Basic HTML" as shown in Screenshot 2)
            selectedTemplateCategory: 'All',
            selectedTemplateId: 'tpl-html',
            // Clone Repo Modal State
            shallowClone: true,
            // Active Project ID for 3-Dots Context Sheet, Edit Project & Delete Project
            activeContextProjectId: 'proj-1',
            // Settings & Sub-Screens State
            themeMode: 'system',
            dynamicColors: true,
            editorFontSize: 14,
            lineNumbers: true,
            wordWrap: false,
            debugLogs: false,
            gitReturnTarget: 'settings',
            aiModelsActiveCategory: 'All',
            aiModelsQuery: '',
            pollinationsModels: [
                { id: 'm1', name: 'ALL MiniLM L6 V2', meta: 'Embedding • 22M params • 256 ctx', enabled: true },
                { id: 'm2', name: 'ALL Mpnet Base V2', meta: 'Embedding • 109M params • 384 ctx', enabled: true },
                { id: 'm3', name: 'BGE Base EN V1.5', meta: 'Embedding • 109M params • 512 ctx', enabled: true },
                { id: 'm4', name: 'BGE EN ICL', meta: 'Embedding • few-shot retrieval', enabled: true },
                { id: 'm5', name: 'BGE Large EN V1.5', meta: 'Embedding • 335M params • 512 ctx', enabled: true },
                { id: 'm6', name: 'BGE M3', meta: 'Hybrid • dense + sparse retrieval', enabled: true },
                { id: 'm7', name: 'BGE M3 Multi', meta: 'Hybrid • multilingual retrieval', enabled: true }
            ],
            aiProviders: [
                {
                    id: 'pollinations',
                    name: 'Pollinations',
                    letter: 'P',
                    shape: 'circle',
                    badgeBg: '#38342D',
                    badgeText: '#EDE9E2',
                    statusText: 'Ready — no key needed',
                    ready: true,
                    expanded: false,
                    description: 'Zero-config community inference endpoint.',
                    apiKey: ''
                },
                {
                    id: 'gemini',
                    name: 'Google Gemini',
                    letter: 'G',
                    shape: 'scallop',
                    badgeBg: '#38342D',
                    badgeText: '#EDE9E2',
                    statusText: 'Key saved',
                    ready: true,
                    expanded: false,
                    description: 'Supports Gemini 2.5 Pro, Flash & Live API models.',
                    apiKey: 'AIzaSyExpressiveGeminiKey2026'
                },
                {
                    id: 'agnes',
                    name: 'Agnes AI',
                    letter: 'A',
                    shape: 'flower',
                    badgeBg: '#38342D',
                    badgeText: '#EDE9E2',
                    statusText: 'Key saved',
                    ready: true,
                    expanded: false,
                    description: 'Fast coding assistant & agent reasoning models.',
                    apiKey: 'agnes_live_98a7bc65d'
                },
                {
                    id: 'custom',
                    name: 'Custom (OpenAI-compati...',
                    letter: 'C',
                    shape: 'clover',
                    badgeBg: '#38342D',
                    badgeText: '#EDE9E2',
                    statusText: 'Optional key — set a base URL',
                    ready: true,
                    expanded: false,
                    description: 'Connect Ollama, LM Studio, or any OpenAI-compatible server.',
                    apiKey: ''
                },
                {
                    id: 'nvidia',
                    name: 'NVIDIA NIM',
                    letter: 'N',
                    shape: 'clover',
                    badgeBg: '#2b3139',
                    badgeText: '#B8B2A8',
                    statusText: 'Needs a key',
                    ready: false,
                    expanded: true,
                    description: 'Free credits on signup. Hosts Nemotron, Qwen, DeepSeek, gpt-oss.',
                    apiKey: ''
                },
                {
                    id: 'openrouter',
                    name: 'OpenRouter',
                    letter: 'O',
                    shape: 'starburst',
                    badgeBg: '#2b3139',
                    badgeText: '#B8B2A8',
                    statusText: 'Needs a key',
                    ready: false,
                    expanded: false,
                    description: 'Unified router for Claude, DeepSeek, Llama, and Mistral models.',
                    apiKey: ''
                }
            ]
        };

