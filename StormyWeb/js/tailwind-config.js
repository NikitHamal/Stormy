        tailwind.config = {
            darkMode: 'class',
            theme: {
                extend: {
                    fontFamily: {
                        sans: ['"Plus Jakarta Sans"', '-apple-system', 'BlinkMacSystemFont', 'sans-serif'],
                        mono: ['"JetBrains Mono"', 'monospace'],
                    },
                    colors: {
                        m3: {
                            bg: '#141210',               // Warm charcoal-black app background
                            surface: '#1E1B17',          // Warm charcoal card surface
                            surfaceModal: '#24211D',     // Dialog / sheet surface
                            surfaceHigh: '#2B2823',       // Elevated container surface
                            surfaceHighest: '#38342D',   // Tonal pills & selected states
                            surfaceDeep: '#1A1714',      // Input & code-adjacent background
                            primary: '#EDE9E2',          // Warm-paper white actions (mono accent)
                            onPrimary: '#141210',        // Ink text/icons inside primary
                            primaryContainer: '#38342D', // Tonal container fill
                            onPrimaryContainer: '#EDE9E2', // Text inside primaryContainer
                            onSurface: '#EDE9E2',        // Warm-paper primary text
                            onSurfaceVariant: '#A8A29A', // Warm gray secondary text
                            outline: '#4E493F',          // Input border outline
                            outlineSubtle: '#33302A',    // Subtle card border
                            error: '#EDE9E2',            // Destructive = strong ink (no red)
                            errorContainer: '#38342D',   // Destructive container fill
                            onErrorContainer: '#EDE9E2'  // Text inside destructive container
                        }
                    }
                }
            }
        }
