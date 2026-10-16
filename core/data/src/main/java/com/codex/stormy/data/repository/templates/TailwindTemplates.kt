package com.codex.stormy.data.repository.templates

/**
 * Tailwind CSS template files
 */
object TailwindTemplates {
    val HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Tailwind Website</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <link rel="stylesheet" href="style.css">
    <script>
        tailwind.config = {
            theme: {
                extend: {
                    colors: {
                        primary: '#4f46e5',
                        secondary: '#7c3aed',
                    }
                }
            }
        }
    </script>
</head>
<body class="bg-gray-50 min-h-screen">
    <!-- Navigation -->
    <nav class="bg-white shadow-sm sticky top-0 z-50">
        <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div class="flex justify-between items-center h-16">
                <div class="flex-shrink-0">
                    <span class="text-2xl font-bold text-primary">MySite</span>
                </div>
                <div class="hidden md:flex items-center space-x-8">
                    <a href="#home" class="text-gray-700 hover:text-primary transition-colors">Home</a>
                    <a href="#features" class="text-gray-700 hover:text-primary transition-colors">Features</a>
                    <a href="#pricing" class="text-gray-700 hover:text-primary transition-colors">Pricing</a>
                    <a href="#contact" class="text-gray-700 hover:text-primary transition-colors">Contact</a>
                    <button class="bg-primary text-white px-4 py-2 rounded-lg hover:bg-indigo-700 transition-colors">
                        Get Started
                    </button>
                </div>
                <!-- Mobile menu button -->
                <div class="md:hidden">
                    <button class="text-gray-700 hover:text-primary">
                        <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16"/>
                        </svg>
                    </button>
                </div>
            </div>
        </div>
    </nav>

    <!-- Hero Section -->
    <section id="home" class="pt-20 pb-32 px-4">
        <div class="max-w-7xl mx-auto text-center">
            <h1 class="text-5xl md:text-6xl font-bold text-gray-900 mb-6">
                Build Something
                <span class="text-transparent bg-clip-text bg-gradient-to-r from-primary to-secondary">
                    Amazing
                </span>
            </h1>
            <p class="text-xl text-gray-600 mb-10 max-w-2xl mx-auto">
                Create beautiful, responsive websites with Tailwind CSS.
                Utility-first CSS framework for rapid UI development.
            </p>
            <div class="flex flex-col sm:flex-row gap-4 justify-center">
                <button class="bg-primary text-white px-8 py-3 rounded-lg font-semibold hover:bg-indigo-700 transition-all hover:shadow-lg">
                    Get Started Free
                </button>
                <button class="border-2 border-gray-300 text-gray-700 px-8 py-3 rounded-lg font-semibold hover:border-primary hover:text-primary transition-all">
                    Learn More
                </button>
            </div>
        </div>
    </section>

    <!-- Features Section -->
    <section id="features" class="py-20 bg-white">
        <div class="max-w-7xl mx-auto px-4">
            <div class="text-center mb-16">
                <h2 class="text-3xl md:text-4xl font-bold text-gray-900 mb-4">Features</h2>
                <p class="text-gray-600 max-w-2xl mx-auto">
                    Everything you need to build modern web applications
                </p>
            </div>
            <div class="grid md:grid-cols-3 gap-8">
                <!-- Feature 1 -->
                <div class="bg-gray-50 rounded-2xl p-8 hover:shadow-lg transition-shadow">
                    <div class="w-12 h-12 bg-primary/10 rounded-xl flex items-center justify-center mb-6">
                        <svg class="w-6 h-6 text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/>
                        </svg>
                    </div>
                    <h3 class="text-xl font-semibold text-gray-900 mb-3">Lightning Fast</h3>
                    <p class="text-gray-600">
                        Optimized for performance with minimal CSS output and tree-shaking support.
                    </p>
                </div>
                <!-- Feature 2 -->
                <div class="bg-gray-50 rounded-2xl p-8 hover:shadow-lg transition-shadow">
                    <div class="w-12 h-12 bg-secondary/10 rounded-xl flex items-center justify-center mb-6">
                        <svg class="w-6 h-6 text-secondary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21a4 4 0 01-4-4V5a2 2 0 012-2h4a2 2 0 012 2v12a4 4 0 01-4 4zm0 0h12a2 2 0 002-2v-4a2 2 0 00-2-2h-2.343M11 7.343l1.657-1.657a2 2 0 012.828 0l2.829 2.829a2 2 0 010 2.828l-8.486 8.485M7 17h.01"/>
                        </svg>
                    </div>
                    <h3 class="text-xl font-semibold text-gray-900 mb-3">Highly Customizable</h3>
                    <p class="text-gray-600">
                        Extend and customize every aspect of the design system to match your brand.
                    </p>
                </div>
                <!-- Feature 3 -->
                <div class="bg-gray-50 rounded-2xl p-8 hover:shadow-lg transition-shadow">
                    <div class="w-12 h-12 bg-green-100 rounded-xl flex items-center justify-center mb-6">
                        <svg class="w-6 h-6 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
                        </svg>
                    </div>
                    <h3 class="text-xl font-semibold text-gray-900 mb-3">Production Ready</h3>
                    <p class="text-gray-600">
                        Battle-tested in thousands of production applications worldwide.
                    </p>
                </div>
            </div>
        </div>
    </section>

    <!-- CTA Section -->
    <section class="py-20 bg-gradient-to-r from-primary to-secondary">
        <div class="max-w-4xl mx-auto text-center px-4">
            <h2 class="text-3xl md:text-4xl font-bold text-white mb-6">
                Ready to get started?
            </h2>
            <p class="text-indigo-100 mb-8 text-lg">
                Join thousands of developers building amazing products with Tailwind CSS.
            </p>
            <button class="bg-white text-primary px-8 py-3 rounded-lg font-semibold hover:bg-gray-100 transition-colors">
                Start Building Today
            </button>
        </div>
    </section>

    <!-- Footer -->
    <footer class="bg-gray-900 text-gray-400 py-12">
        <div class="max-w-7xl mx-auto px-4 text-center">
            <p>&copy; 2024 MySite. All rights reserved.</p>
        </div>
    </footer>
</body>
</html>
    """.trimIndent()

    val CSS = """
/* Custom styles that complement Tailwind */
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');

body {
    font-family: 'Inter', system-ui, sans-serif;
}

/* Custom animations */
@keyframes float {
    0%, 100% { transform: translateY(0); }
    50% { transform: translateY(-10px); }
}

.animate-float {
    animation: float 3s ease-in-out infinite;
}

/* Custom gradient text */
.gradient-text {
    background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
}

/* Smooth scroll behavior */
html {
    scroll-behavior: smooth;
}
    """.trimIndent()
}
