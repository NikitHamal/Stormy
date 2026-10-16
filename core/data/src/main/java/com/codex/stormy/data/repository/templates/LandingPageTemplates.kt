package com.codex.stormy.data.repository.templates

/**
 * Modern landing page template files
 */
object LandingPageTemplates {
    val HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Landing Page</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <link rel="stylesheet" href="style.css">
</head>
<body class="bg-gradient-to-br from-indigo-500 via-purple-500 to-pink-500 min-h-screen">
    <!-- Navigation -->
    <nav class="container mx-auto px-6 py-4">
        <div class="flex items-center justify-between">
            <div class="text-white text-2xl font-bold">Brand</div>
            <div class="hidden md:flex items-center space-x-6">
                <a href="#" class="text-white/90 hover:text-white transition">Home</a>
                <a href="#features" class="text-white/90 hover:text-white transition">Features</a>
                <a href="#pricing" class="text-white/90 hover:text-white transition">Pricing</a>
                <a href="#contact" class="text-white/90 hover:text-white transition">Contact</a>
                <button class="bg-white/20 text-white px-4 py-2 rounded-full hover:bg-white/30 transition">
                    Sign In
                </button>
            </div>
            <!-- Mobile menu button -->
            <button class="md:hidden text-white">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16"/>
                </svg>
            </button>
        </div>
    </nav>

    <!-- Hero Section -->
    <main class="container mx-auto px-6 py-16 text-center">
        <div class="max-w-4xl mx-auto">
            <span class="inline-block px-4 py-2 bg-white/20 rounded-full text-white text-sm font-medium mb-6">
                New Release v2.0
            </span>
            <h1 class="text-5xl md:text-7xl font-bold text-white mb-6 leading-tight">
                Build Something Amazing Today
            </h1>
            <p class="text-xl md:text-2xl text-white/80 mb-10 max-w-2xl mx-auto">
                Create beautiful, responsive websites with modern tools and frameworks.
                Get started in minutes, not hours.
            </p>
            <div class="flex flex-col sm:flex-row gap-4 justify-center mb-16">
                <button class="px-8 py-4 bg-white text-indigo-600 font-semibold rounded-full hover:bg-opacity-90 transition transform hover:scale-105 shadow-xl">
                    Get Started Free
                </button>
                <button class="px-8 py-4 border-2 border-white text-white font-semibold rounded-full hover:bg-white/10 transition flex items-center justify-center gap-2">
                    <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                        <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clip-rule="evenodd"/>
                    </svg>
                    Watch Demo
                </button>
            </div>

            <!-- Stats -->
            <div class="grid grid-cols-2 md:grid-cols-4 gap-8 max-w-3xl mx-auto">
                <div class="text-center">
                    <div class="text-4xl font-bold text-white">10K+</div>
                    <div class="text-white/70">Active Users</div>
                </div>
                <div class="text-center">
                    <div class="text-4xl font-bold text-white">99%</div>
                    <div class="text-white/70">Uptime</div>
                </div>
                <div class="text-center">
                    <div class="text-4xl font-bold text-white">24/7</div>
                    <div class="text-white/70">Support</div>
                </div>
                <div class="text-center">
                    <div class="text-4xl font-bold text-white">50+</div>
                    <div class="text-white/70">Countries</div>
                </div>
            </div>
        </div>
    </main>

    <!-- Features Section -->
    <section id="features" class="bg-white py-20">
        <div class="container mx-auto px-6">
            <div class="text-center mb-16">
                <h2 class="text-4xl font-bold text-gray-900 mb-4">Why Choose Us?</h2>
                <p class="text-gray-600 max-w-2xl mx-auto">
                    Everything you need to launch your next project
                </p>
            </div>
            <div class="grid md:grid-cols-3 gap-8">
                <div class="p-8 rounded-2xl bg-gradient-to-br from-indigo-50 to-purple-50 hover:shadow-xl transition">
                    <div class="w-14 h-14 bg-indigo-500 rounded-xl flex items-center justify-center mb-6">
                        <svg class="w-7 h-7 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/>
                        </svg>
                    </div>
                    <h3 class="text-xl font-semibold text-gray-900 mb-3">Lightning Fast</h3>
                    <p class="text-gray-600">
                        Optimized performance with sub-second load times for the best user experience.
                    </p>
                </div>
                <div class="p-8 rounded-2xl bg-gradient-to-br from-purple-50 to-pink-50 hover:shadow-xl transition">
                    <div class="w-14 h-14 bg-purple-500 rounded-xl flex items-center justify-center mb-6">
                        <svg class="w-7 h-7 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"/>
                        </svg>
                    </div>
                    <h3 class="text-xl font-semibold text-gray-900 mb-3">Secure by Default</h3>
                    <p class="text-gray-600">
                        Enterprise-grade security with encryption and compliance built in.
                    </p>
                </div>
                <div class="p-8 rounded-2xl bg-gradient-to-br from-pink-50 to-red-50 hover:shadow-xl transition">
                    <div class="w-14 h-14 bg-pink-500 rounded-xl flex items-center justify-center mb-6">
                        <svg class="w-7 h-7 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 5a1 1 0 011-1h14a1 1 0 011 1v2a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM4 13a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H5a1 1 0 01-1-1v-6zM16 13a1 1 0 011-1h2a1 1 0 011 1v6a1 1 0 01-1 1h-2a1 1 0 01-1-1v-6z"/>
                        </svg>
                    </div>
                    <h3 class="text-xl font-semibold text-gray-900 mb-3">Easy to Use</h3>
                    <p class="text-gray-600">
                        Intuitive interface that doesn't require a manual to get started.
                    </p>
                </div>
            </div>
        </div>
    </section>

    <!-- CTA Section -->
    <section class="bg-gray-900 py-20">
        <div class="container mx-auto px-6 text-center">
            <h2 class="text-4xl font-bold text-white mb-6">Ready to get started?</h2>
            <p class="text-gray-400 mb-8 max-w-2xl mx-auto">
                Join thousands of happy customers using our platform.
            </p>
            <button class="px-8 py-4 bg-gradient-to-r from-indigo-500 to-purple-500 text-white font-semibold rounded-full hover:opacity-90 transition">
                Start Your Free Trial
            </button>
        </div>
    </section>

    <!-- Footer -->
    <footer class="bg-gray-900 border-t border-gray-800 py-8">
        <div class="container mx-auto px-6 text-center text-gray-500">
            <p>&copy; 2024 Brand. All rights reserved.</p>
        </div>
    </footer>
</body>
</html>
    """.trimIndent()

    val CSS = """
/* Custom styles for landing page */
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap');

body {
    font-family: 'Inter', system-ui, sans-serif;
}

/* Smooth scroll */
html {
    scroll-behavior: smooth;
}

/* Gradient animation */
@keyframes gradient {
    0% { background-position: 0% 50%; }
    50% { background-position: 100% 50%; }
    100% { background-position: 0% 50%; }
}

.animate-gradient {
    background-size: 200% 200%;
    animation: gradient 15s ease infinite;
}

/* Float animation */
@keyframes float {
    0%, 100% { transform: translateY(0); }
    50% { transform: translateY(-20px); }
}

.animate-float {
    animation: float 6s ease-in-out infinite;
}

/* Pulse glow effect */
@keyframes pulse-glow {
    0%, 100% { box-shadow: 0 0 20px rgba(139, 92, 246, 0.3); }
    50% { box-shadow: 0 0 40px rgba(139, 92, 246, 0.6); }
}

.pulse-glow {
    animation: pulse-glow 2s ease-in-out infinite;
}
    """.trimIndent()
}
