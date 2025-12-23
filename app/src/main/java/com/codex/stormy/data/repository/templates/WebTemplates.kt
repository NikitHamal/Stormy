package com.codex.stormy.data.repository.templates

/**
 * Portfolio website template files
 */
object PortfolioTemplates {
    val HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>My Portfolio</title>
    <link rel="stylesheet" href="style.css">
    <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-gray-900 text-white">
    <!-- Navigation -->
    <nav class="fixed top-0 w-full bg-gray-900/80 backdrop-blur-md z-50 border-b border-gray-800">
        <div class="max-w-6xl mx-auto px-6 py-4">
            <div class="flex justify-between items-center">
                <a href="#" class="text-2xl font-bold bg-gradient-to-r from-blue-500 to-purple-500 bg-clip-text text-transparent">
                    Portfolio
                </a>
                <div class="hidden md:flex space-x-8">
                    <a href="#about" class="hover:text-blue-400 transition">About</a>
                    <a href="#skills" class="hover:text-blue-400 transition">Skills</a>
                    <a href="#projects" class="hover:text-blue-400 transition">Projects</a>
                    <a href="#contact" class="hover:text-blue-400 transition">Contact</a>
                </div>
            </div>
        </div>
    </nav>

    <!-- Hero Section -->
    <section class="min-h-screen flex items-center justify-center px-6 pt-20">
        <div class="text-center">
            <div class="w-32 h-32 mx-auto mb-8 rounded-full bg-gradient-to-r from-blue-500 to-purple-500 p-1">
                <div class="w-full h-full rounded-full bg-gray-900 flex items-center justify-center">
                    <span class="text-4xl">👋</span>
                </div>
            </div>
            <h1 class="text-5xl md:text-7xl font-bold mb-4">
                Hi, I'm <span class="bg-gradient-to-r from-blue-500 to-purple-500 bg-clip-text text-transparent">Your Name</span>
            </h1>
            <p class="text-xl text-gray-400 mb-8 max-w-2xl mx-auto">
                A passionate developer creating beautiful and functional web experiences.
            </p>
            <div class="flex justify-center gap-4">
                <a href="#projects" class="px-8 py-3 bg-gradient-to-r from-blue-500 to-purple-500 rounded-full font-semibold hover:opacity-90 transition">
                    View My Work
                </a>
                <a href="#contact" class="px-8 py-3 border border-gray-600 rounded-full font-semibold hover:border-blue-500 transition">
                    Contact Me
                </a>
            </div>
        </div>
    </section>

    <!-- About Section -->
    <section id="about" class="py-20 px-6">
        <div class="max-w-4xl mx-auto">
            <h2 class="text-3xl font-bold text-center mb-12">About Me</h2>
            <div class="grid md:grid-cols-2 gap-12 items-center">
                <div>
                    <p class="text-gray-400 mb-4">
                        I'm a full-stack developer with a passion for creating elegant solutions to complex problems.
                        With expertise in modern web technologies, I build scalable and user-friendly applications.
                    </p>
                    <p class="text-gray-400">
                        When I'm not coding, you can find me exploring new technologies, contributing to open source,
                        or sharing knowledge with the developer community.
                    </p>
                </div>
                <div class="grid grid-cols-2 gap-4">
                    <div class="bg-gray-800 p-6 rounded-xl text-center">
                        <div class="text-3xl font-bold text-blue-500">5+</div>
                        <div class="text-gray-400">Years Experience</div>
                    </div>
                    <div class="bg-gray-800 p-6 rounded-xl text-center">
                        <div class="text-3xl font-bold text-purple-500">50+</div>
                        <div class="text-gray-400">Projects Completed</div>
                    </div>
                    <div class="bg-gray-800 p-6 rounded-xl text-center">
                        <div class="text-3xl font-bold text-green-500">30+</div>
                        <div class="text-gray-400">Happy Clients</div>
                    </div>
                    <div class="bg-gray-800 p-6 rounded-xl text-center">
                        <div class="text-3xl font-bold text-yellow-500">10+</div>
                        <div class="text-gray-400">Awards Won</div>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Skills Section -->
    <section id="skills" class="py-20 px-6 bg-gray-800/50">
        <div class="max-w-4xl mx-auto">
            <h2 class="text-3xl font-bold text-center mb-12">Skills & Technologies</h2>
            <div class="grid grid-cols-2 md:grid-cols-4 gap-6">
                <div class="skill-card bg-gray-800 p-6 rounded-xl text-center hover:bg-gray-700 transition">
                    <div class="text-4xl mb-3">⚛️</div>
                    <div class="font-semibold">React</div>
                </div>
                <div class="skill-card bg-gray-800 p-6 rounded-xl text-center hover:bg-gray-700 transition">
                    <div class="text-4xl mb-3">📱</div>
                    <div class="font-semibold">React Native</div>
                </div>
                <div class="skill-card bg-gray-800 p-6 rounded-xl text-center hover:bg-gray-700 transition">
                    <div class="text-4xl mb-3">🟢</div>
                    <div class="font-semibold">Node.js</div>
                </div>
                <div class="skill-card bg-gray-800 p-6 rounded-xl text-center hover:bg-gray-700 transition">
                    <div class="text-4xl mb-3">🐍</div>
                    <div class="font-semibold">Python</div>
                </div>
                <div class="skill-card bg-gray-800 p-6 rounded-xl text-center hover:bg-gray-700 transition">
                    <div class="text-4xl mb-3">🎨</div>
                    <div class="font-semibold">Tailwind CSS</div>
                </div>
                <div class="skill-card bg-gray-800 p-6 rounded-xl text-center hover:bg-gray-700 transition">
                    <div class="text-4xl mb-3">🗄️</div>
                    <div class="font-semibold">PostgreSQL</div>
                </div>
                <div class="skill-card bg-gray-800 p-6 rounded-xl text-center hover:bg-gray-700 transition">
                    <div class="text-4xl mb-3">☁️</div>
                    <div class="font-semibold">AWS</div>
                </div>
                <div class="skill-card bg-gray-800 p-6 rounded-xl text-center hover:bg-gray-700 transition">
                    <div class="text-4xl mb-3">🐳</div>
                    <div class="font-semibold">Docker</div>
                </div>
            </div>
        </div>
    </section>

    <!-- Projects Section -->
    <section id="projects" class="py-20 px-6">
        <div class="max-w-6xl mx-auto">
            <h2 class="text-3xl font-bold text-center mb-12">Featured Projects</h2>
            <div class="grid md:grid-cols-2 lg:grid-cols-3 gap-8">
                <div class="project-card bg-gray-800 rounded-xl overflow-hidden hover:transform hover:scale-105 transition duration-300">
                    <div class="h-48 bg-gradient-to-r from-blue-500 to-purple-500"></div>
                    <div class="p-6">
                        <h3 class="text-xl font-semibold mb-2">Project One</h3>
                        <p class="text-gray-400 mb-4">A modern e-commerce platform with real-time inventory.</p>
                        <div class="flex gap-2">
                            <span class="px-3 py-1 bg-blue-500/20 text-blue-400 rounded-full text-sm">React</span>
                            <span class="px-3 py-1 bg-green-500/20 text-green-400 rounded-full text-sm">Node.js</span>
                        </div>
                    </div>
                </div>
                <div class="project-card bg-gray-800 rounded-xl overflow-hidden hover:transform hover:scale-105 transition duration-300">
                    <div class="h-48 bg-gradient-to-r from-green-500 to-teal-500"></div>
                    <div class="p-6">
                        <h3 class="text-xl font-semibold mb-2">Project Two</h3>
                        <p class="text-gray-400 mb-4">AI-powered task management application.</p>
                        <div class="flex gap-2">
                            <span class="px-3 py-1 bg-purple-500/20 text-purple-400 rounded-full text-sm">Python</span>
                            <span class="px-3 py-1 bg-yellow-500/20 text-yellow-400 rounded-full text-sm">FastAPI</span>
                        </div>
                    </div>
                </div>
                <div class="project-card bg-gray-800 rounded-xl overflow-hidden hover:transform hover:scale-105 transition duration-300">
                    <div class="h-48 bg-gradient-to-r from-orange-500 to-red-500"></div>
                    <div class="p-6">
                        <h3 class="text-xl font-semibold mb-2">Project Three</h3>
                        <p class="text-gray-400 mb-4">Real-time collaboration platform for teams.</p>
                        <div class="flex gap-2">
                            <span class="px-3 py-1 bg-blue-500/20 text-blue-400 rounded-full text-sm">Vue.js</span>
                            <span class="px-3 py-1 bg-red-500/20 text-red-400 rounded-full text-sm">Firebase</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Contact Section -->
    <section id="contact" class="py-20 px-6 bg-gray-800/50">
        <div class="max-w-2xl mx-auto text-center">
            <h2 class="text-3xl font-bold mb-4">Let's Work Together</h2>
            <p class="text-gray-400 mb-8">
                Have a project in mind? I'd love to hear about it. Let's create something amazing together.
            </p>
            <a href="mailto:hello@example.com" class="inline-block px-8 py-4 bg-gradient-to-r from-blue-500 to-purple-500 rounded-full font-semibold hover:opacity-90 transition">
                Get in Touch
            </a>
            <div class="flex justify-center gap-6 mt-8">
                <a href="#" class="text-gray-400 hover:text-white transition">GitHub</a>
                <a href="#" class="text-gray-400 hover:text-white transition">LinkedIn</a>
                <a href="#" class="text-gray-400 hover:text-white transition">Twitter</a>
            </div>
        </div>
    </section>

    <!-- Footer -->
    <footer class="py-8 px-6 border-t border-gray-800">
        <div class="max-w-6xl mx-auto text-center text-gray-500">
            <p>&copy; 2024 Your Name. All rights reserved.</p>
        </div>
    </footer>

    <script src="script.js"></script>
</body>
</html>
    """.trimIndent()

    val CSS = """
/* Custom styles */
html {
    scroll-behavior: smooth;
}

/* Skill card hover effect */
.skill-card:hover {
    transform: translateY(-5px);
}

/* Project card overlay on hover */
.project-card:hover .project-overlay {
    opacity: 1;
}
    """.trimIndent()

    val JS = """
// Smooth scroll for navigation links
document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function(e) {
        e.preventDefault();
        const target = document.querySelector(this.getAttribute('href'));
        if (target) {
            target.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    });
});

// Navbar scroll effect
window.addEventListener('scroll', () => {
    const nav = document.querySelector('nav');
    if (window.scrollY > 100) {
        nav.classList.add('shadow-lg');
    } else {
        nav.classList.remove('shadow-lg');
    }
});

// Intersection Observer for fade-in animations
const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
        if (entry.isIntersecting) {
            entry.target.classList.add('animate-fade-in');
        }
    });
}, { threshold: 0.1 });

document.querySelectorAll('section').forEach(section => {
    observer.observe(section);
});
    """.trimIndent()
}

/**
 * Blog template files
 */
object BlogTemplates {
    val INDEX_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>My Blog</title>
    <link rel="stylesheet" href="style.css">
    <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-gray-50 min-h-screen">
    <!-- Header -->
    <header class="bg-white border-b border-gray-200">
        <div class="max-w-4xl mx-auto px-6 py-6">
            <div class="flex justify-between items-center">
                <a href="index.html" class="text-2xl font-bold text-gray-900">My Blog</a>
                <nav class="flex gap-6">
                    <a href="index.html" class="text-gray-600 hover:text-gray-900 transition">Home</a>
                    <a href="#" class="text-gray-600 hover:text-gray-900 transition">About</a>
                    <a href="#" class="text-gray-600 hover:text-gray-900 transition">Contact</a>
                </nav>
            </div>
        </div>
    </header>

    <!-- Hero -->
    <section class="bg-gradient-to-r from-indigo-500 to-purple-600 text-white py-20">
        <div class="max-w-4xl mx-auto px-6 text-center">
            <h1 class="text-4xl md:text-5xl font-bold mb-4">Welcome to My Blog</h1>
            <p class="text-xl text-indigo-100">Thoughts, stories, and ideas about technology and life.</p>
        </div>
    </section>

    <!-- Main Content -->
    <main class="max-w-4xl mx-auto px-6 py-12">
        <h2 class="text-2xl font-bold text-gray-900 mb-8">Latest Posts</h2>

        <div class="space-y-8">
            <!-- Post 1 -->
            <article class="bg-white rounded-xl shadow-sm overflow-hidden hover:shadow-md transition">
                <a href="post.html" class="block md:flex">
                    <div class="md:w-1/3 h-48 md:h-auto bg-gradient-to-r from-blue-500 to-indigo-500"></div>
                    <div class="md:w-2/3 p-6">
                        <div class="flex items-center gap-2 text-sm text-gray-500 mb-2">
                            <span>December 15, 2024</span>
                            <span>•</span>
                            <span>5 min read</span>
                        </div>
                        <h3 class="text-xl font-semibold text-gray-900 mb-2 hover:text-indigo-600 transition">
                            Getting Started with Web Development
                        </h3>
                        <p class="text-gray-600 mb-4">
                            Learn the fundamentals of web development and start building your first website today.
                        </p>
                        <div class="flex gap-2">
                            <span class="px-3 py-1 bg-indigo-100 text-indigo-700 rounded-full text-sm">Tutorial</span>
                            <span class="px-3 py-1 bg-green-100 text-green-700 rounded-full text-sm">Beginner</span>
                        </div>
                    </div>
                </a>
            </article>

            <!-- Post 2 -->
            <article class="bg-white rounded-xl shadow-sm overflow-hidden hover:shadow-md transition">
                <a href="post.html" class="block md:flex">
                    <div class="md:w-1/3 h-48 md:h-auto bg-gradient-to-r from-green-500 to-teal-500"></div>
                    <div class="md:w-2/3 p-6">
                        <div class="flex items-center gap-2 text-sm text-gray-500 mb-2">
                            <span>December 10, 2024</span>
                            <span>•</span>
                            <span>8 min read</span>
                        </div>
                        <h3 class="text-xl font-semibold text-gray-900 mb-2 hover:text-indigo-600 transition">
                            Modern CSS Techniques You Should Know
                        </h3>
                        <p class="text-gray-600 mb-4">
                            Explore the latest CSS features that will revolutionize how you style your websites.
                        </p>
                        <div class="flex gap-2">
                            <span class="px-3 py-1 bg-purple-100 text-purple-700 rounded-full text-sm">CSS</span>
                            <span class="px-3 py-1 bg-yellow-100 text-yellow-700 rounded-full text-sm">Advanced</span>
                        </div>
                    </div>
                </a>
            </article>

            <!-- Post 3 -->
            <article class="bg-white rounded-xl shadow-sm overflow-hidden hover:shadow-md transition">
                <a href="post.html" class="block md:flex">
                    <div class="md:w-1/3 h-48 md:h-auto bg-gradient-to-r from-orange-500 to-red-500"></div>
                    <div class="md:w-2/3 p-6">
                        <div class="flex items-center gap-2 text-sm text-gray-500 mb-2">
                            <span>December 5, 2024</span>
                            <span>•</span>
                            <span>6 min read</span>
                        </div>
                        <h3 class="text-xl font-semibold text-gray-900 mb-2 hover:text-indigo-600 transition">
                            Building Accessible Web Applications
                        </h3>
                        <p class="text-gray-600 mb-4">
                            Best practices for making your web applications accessible to everyone.
                        </p>
                        <div class="flex gap-2">
                            <span class="px-3 py-1 bg-blue-100 text-blue-700 rounded-full text-sm">Accessibility</span>
                            <span class="px-3 py-1 bg-pink-100 text-pink-700 rounded-full text-sm">Best Practices</span>
                        </div>
                    </div>
                </a>
            </article>
        </div>
    </main>

    <!-- Footer -->
    <footer class="bg-white border-t border-gray-200 py-8">
        <div class="max-w-4xl mx-auto px-6 text-center text-gray-500">
            <p>&copy; 2024 My Blog. All rights reserved.</p>
        </div>
    </footer>

    <script src="script.js"></script>
</body>
</html>
    """.trimIndent()

    val POST_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Getting Started with Web Development - My Blog</title>
    <link rel="stylesheet" href="style.css">
    <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-gray-50 min-h-screen">
    <!-- Header -->
    <header class="bg-white border-b border-gray-200">
        <div class="max-w-4xl mx-auto px-6 py-6">
            <div class="flex justify-between items-center">
                <a href="index.html" class="text-2xl font-bold text-gray-900">My Blog</a>
                <nav class="flex gap-6">
                    <a href="index.html" class="text-gray-600 hover:text-gray-900 transition">Home</a>
                    <a href="#" class="text-gray-600 hover:text-gray-900 transition">About</a>
                    <a href="#" class="text-gray-600 hover:text-gray-900 transition">Contact</a>
                </nav>
            </div>
        </div>
    </header>

    <!-- Post Header -->
    <div class="bg-gradient-to-r from-blue-500 to-indigo-500 text-white py-16">
        <div class="max-w-4xl mx-auto px-6">
            <a href="index.html" class="text-indigo-200 hover:text-white transition mb-4 inline-block">&larr; Back to posts</a>
            <h1 class="text-4xl md:text-5xl font-bold mb-4">Getting Started with Web Development</h1>
            <div class="flex items-center gap-4 text-indigo-100">
                <span>December 15, 2024</span>
                <span>•</span>
                <span>5 min read</span>
            </div>
        </div>
    </div>

    <!-- Post Content -->
    <main class="max-w-4xl mx-auto px-6 py-12">
        <article class="prose prose-lg max-w-none">
            <p class="text-xl text-gray-600 mb-8">
                Learn the fundamentals of web development and start building your first website today.
                This guide will walk you through everything you need to know.
            </p>

            <h2 class="text-2xl font-bold text-gray-900 mt-8 mb-4">Introduction</h2>
            <p class="text-gray-700 mb-4">
                Web development is one of the most rewarding skills you can learn in today's digital world.
                Whether you want to build your own website, start a career in tech, or simply understand
                how the internet works, learning web development is a great place to start.
            </p>

            <h2 class="text-2xl font-bold text-gray-900 mt-8 mb-4">The Three Pillars</h2>
            <p class="text-gray-700 mb-4">
                Web development is built on three core technologies:
            </p>
            <ul class="list-disc list-inside text-gray-700 mb-4 space-y-2">
                <li><strong>HTML</strong> - The structure of your web pages</li>
                <li><strong>CSS</strong> - The styling and visual presentation</li>
                <li><strong>JavaScript</strong> - The interactivity and dynamic behavior</li>
            </ul>

            <h2 class="text-2xl font-bold text-gray-900 mt-8 mb-4">Getting Started</h2>
            <p class="text-gray-700 mb-4">
                The best way to learn web development is by building projects. Start with simple static
                pages and gradually add more complexity as you learn. Practice consistently and don't
                be afraid to make mistakes - that's how you learn!
            </p>

            <div class="bg-indigo-50 border-l-4 border-indigo-500 p-6 my-8">
                <p class="text-indigo-800 font-medium">
                    Pro tip: Use browser developer tools to inspect websites you admire and learn how
                    they're built!
                </p>
            </div>

            <h2 class="text-2xl font-bold text-gray-900 mt-8 mb-4">Conclusion</h2>
            <p class="text-gray-700 mb-4">
                Web development is an exciting journey. Take it one step at a time, build lots of projects,
                and most importantly - have fun! The web is your canvas to create amazing things.
            </p>
        </article>

        <!-- Author -->
        <div class="mt-12 pt-8 border-t border-gray-200">
            <div class="flex items-center gap-4">
                <div class="w-16 h-16 rounded-full bg-gradient-to-r from-blue-500 to-indigo-500"></div>
                <div>
                    <h3 class="font-semibold text-gray-900">Author Name</h3>
                    <p class="text-gray-600">Web Developer & Technical Writer</p>
                </div>
            </div>
        </div>
    </main>

    <!-- Footer -->
    <footer class="bg-white border-t border-gray-200 py-8">
        <div class="max-w-4xl mx-auto px-6 text-center text-gray-500">
            <p>&copy; 2024 My Blog. All rights reserved.</p>
        </div>
    </footer>

    <script src="script.js"></script>
</body>
</html>
    """.trimIndent()

    val CSS = """
/* Custom blog styles */
html {
    scroll-behavior: smooth;
}

.prose p {
    margin-bottom: 1rem;
}

.prose h2 {
    margin-top: 2rem;
}
    """.trimIndent()

    val JS = """
// Blog interactions
console.log('Blog loaded!');
    """.trimIndent()
}

/**
 * Admin Dashboard template files
 */
object DashboardTemplates {
    val HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Dashboard</title>
    <link rel="stylesheet" href="style.css">
    <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-gray-100">
    <div class="flex min-h-screen">
        <!-- Sidebar -->
        <aside class="w-64 bg-gray-900 text-white flex-shrink-0">
            <div class="p-6">
                <h1 class="text-xl font-bold">Dashboard</h1>
            </div>
            <nav class="mt-6">
                <a href="#" class="flex items-center px-6 py-3 bg-gray-800 text-white">
                    <span class="mr-3">📊</span> Overview
                </a>
                <a href="#" class="flex items-center px-6 py-3 text-gray-400 hover:bg-gray-800 hover:text-white transition">
                    <span class="mr-3">👥</span> Users
                </a>
                <a href="#" class="flex items-center px-6 py-3 text-gray-400 hover:bg-gray-800 hover:text-white transition">
                    <span class="mr-3">📦</span> Products
                </a>
                <a href="#" class="flex items-center px-6 py-3 text-gray-400 hover:bg-gray-800 hover:text-white transition">
                    <span class="mr-3">💰</span> Revenue
                </a>
                <a href="#" class="flex items-center px-6 py-3 text-gray-400 hover:bg-gray-800 hover:text-white transition">
                    <span class="mr-3">⚙️</span> Settings
                </a>
            </nav>
        </aside>

        <!-- Main Content -->
        <div class="flex-1 flex flex-col">
            <!-- Top Bar -->
            <header class="bg-white shadow-sm px-6 py-4 flex justify-between items-center">
                <h2 class="text-xl font-semibold text-gray-800">Overview</h2>
                <div class="flex items-center gap-4">
                    <button class="p-2 text-gray-500 hover:text-gray-700">🔔</button>
                    <div class="w-8 h-8 rounded-full bg-indigo-500"></div>
                </div>
            </header>

            <!-- Dashboard Content -->
            <main class="flex-1 p-6">
                <!-- Stats Grid -->
                <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
                    <div class="bg-white rounded-xl p-6 shadow-sm">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-gray-500 text-sm">Total Users</p>
                                <p class="text-3xl font-bold text-gray-900">12,345</p>
                            </div>
                            <div class="w-12 h-12 bg-blue-100 rounded-xl flex items-center justify-center text-2xl">
                                👥
                            </div>
                        </div>
                        <p class="text-green-500 text-sm mt-2">+12% from last month</p>
                    </div>
                    <div class="bg-white rounded-xl p-6 shadow-sm">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-gray-500 text-sm">Revenue</p>
                                <p class="text-3xl font-bold text-gray-900">$54,321</p>
                            </div>
                            <div class="w-12 h-12 bg-green-100 rounded-xl flex items-center justify-center text-2xl">
                                💰
                            </div>
                        </div>
                        <p class="text-green-500 text-sm mt-2">+8% from last month</p>
                    </div>
                    <div class="bg-white rounded-xl p-6 shadow-sm">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-gray-500 text-sm">Orders</p>
                                <p class="text-3xl font-bold text-gray-900">1,234</p>
                            </div>
                            <div class="w-12 h-12 bg-purple-100 rounded-xl flex items-center justify-center text-2xl">
                                📦
                            </div>
                        </div>
                        <p class="text-red-500 text-sm mt-2">-3% from last month</p>
                    </div>
                    <div class="bg-white rounded-xl p-6 shadow-sm">
                        <div class="flex items-center justify-between">
                            <div>
                                <p class="text-gray-500 text-sm">Conversion</p>
                                <p class="text-3xl font-bold text-gray-900">3.2%</p>
                            </div>
                            <div class="w-12 h-12 bg-yellow-100 rounded-xl flex items-center justify-center text-2xl">
                                📈
                            </div>
                        </div>
                        <p class="text-green-500 text-sm mt-2">+0.5% from last month</p>
                    </div>
                </div>

                <!-- Charts and Tables -->
                <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
                    <!-- Chart Placeholder -->
                    <div class="bg-white rounded-xl p-6 shadow-sm">
                        <h3 class="text-lg font-semibold text-gray-800 mb-4">Revenue Overview</h3>
                        <div class="h-64 bg-gradient-to-t from-indigo-100 to-white rounded-lg flex items-end justify-around p-4">
                            <div class="w-12 bg-indigo-500 rounded-t" style="height: 60%"></div>
                            <div class="w-12 bg-indigo-500 rounded-t" style="height: 80%"></div>
                            <div class="w-12 bg-indigo-500 rounded-t" style="height: 45%"></div>
                            <div class="w-12 bg-indigo-500 rounded-t" style="height: 90%"></div>
                            <div class="w-12 bg-indigo-500 rounded-t" style="height: 70%"></div>
                            <div class="w-12 bg-indigo-500 rounded-t" style="height: 85%"></div>
                        </div>
                    </div>

                    <!-- Recent Activity -->
                    <div class="bg-white rounded-xl p-6 shadow-sm">
                        <h3 class="text-lg font-semibold text-gray-800 mb-4">Recent Activity</h3>
                        <div class="space-y-4">
                            <div class="flex items-center gap-4">
                                <div class="w-10 h-10 bg-green-100 rounded-full flex items-center justify-center">✓</div>
                                <div class="flex-1">
                                    <p class="font-medium text-gray-800">New user registered</p>
                                    <p class="text-sm text-gray-500">2 minutes ago</p>
                                </div>
                            </div>
                            <div class="flex items-center gap-4">
                                <div class="w-10 h-10 bg-blue-100 rounded-full flex items-center justify-center">📦</div>
                                <div class="flex-1">
                                    <p class="font-medium text-gray-800">Order #1234 shipped</p>
                                    <p class="text-sm text-gray-500">15 minutes ago</p>
                                </div>
                            </div>
                            <div class="flex items-center gap-4">
                                <div class="w-10 h-10 bg-purple-100 rounded-full flex items-center justify-center">💳</div>
                                <div class="flex-1">
                                    <p class="font-medium text-gray-800">Payment received</p>
                                    <p class="text-sm text-gray-500">1 hour ago</p>
                                </div>
                            </div>
                            <div class="flex items-center gap-4">
                                <div class="w-10 h-10 bg-yellow-100 rounded-full flex items-center justify-center">⭐</div>
                                <div class="flex-1">
                                    <p class="font-medium text-gray-800">New review posted</p>
                                    <p class="text-sm text-gray-500">3 hours ago</p>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </main>
        </div>
    </div>

    <script src="script.js"></script>
</body>
</html>
    """.trimIndent()

    val CSS = """
/* Dashboard custom styles */
html {
    scroll-behavior: smooth;
}

/* Active nav item indicator */
.nav-active {
    position: relative;
}

.nav-active::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 4px;
    background: linear-gradient(135deg, #6366f1, #8b5cf6);
}
    """.trimIndent()

    val JS = """
// Dashboard interactions
console.log('Dashboard loaded!');

// Simulate real-time updates
setInterval(() => {
    // Add your real-time update logic here
}, 30000);
    """.trimIndent()
}
