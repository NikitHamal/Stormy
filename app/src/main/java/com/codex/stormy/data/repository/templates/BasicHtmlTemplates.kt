package com.codex.stormy.data.repository.templates

/**
 * Basic HTML5 template files
 */
object BasicHtmlTemplates {
    val HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>My Website</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <header>
        <nav>
            <div class="logo">MySite</div>
            <ul class="nav-links">
                <li><a href="#home">Home</a></li>
                <li><a href="#about">About</a></li>
                <li><a href="#contact">Contact</a></li>
            </ul>
        </nav>
    </header>

    <main>
        <section id="home" class="hero">
            <h1>Welcome to My Website</h1>
            <p>This is a simple HTML5 boilerplate to get you started.</p>
            <button class="cta-button">Get Started</button>
        </section>

        <section id="about" class="about">
            <h2>About Us</h2>
            <p>Learn more about what we do and how we can help you.</p>
        </section>

        <section id="contact" class="contact">
            <h2>Contact</h2>
            <p>Get in touch with us for any questions.</p>
        </section>
    </main>

    <footer>
        <p>&copy; 2024 My Website. All rights reserved.</p>
    </footer>

    <script src="script.js"></script>
</body>
</html>
    """.trimIndent()

    val CSS = """
/* Reset and Base Styles */
* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

:root {
    --primary-color: #4f46e5;
    --secondary-color: #7c3aed;
    --text-color: #1f2937;
    --text-light: #6b7280;
    --bg-color: #ffffff;
    --bg-secondary: #f3f4f6;
}

body {
    font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    line-height: 1.6;
    color: var(--text-color);
    background-color: var(--bg-color);
}

/* Navigation */
nav {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 1rem 5%;
    background: var(--bg-color);
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
    position: sticky;
    top: 0;
    z-index: 100;
}

.logo {
    font-size: 1.5rem;
    font-weight: bold;
    color: var(--primary-color);
}

.nav-links {
    display: flex;
    list-style: none;
    gap: 2rem;
}

.nav-links a {
    text-decoration: none;
    color: var(--text-color);
    font-weight: 500;
    transition: color 0.3s ease;
}

.nav-links a:hover {
    color: var(--primary-color);
}

/* Hero Section */
.hero {
    min-height: 80vh;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    text-align: center;
    padding: 2rem 5%;
    background: linear-gradient(135deg, var(--bg-secondary) 0%, var(--bg-color) 100%);
}

.hero h1 {
    font-size: 3rem;
    margin-bottom: 1rem;
    color: var(--text-color);
}

.hero p {
    font-size: 1.25rem;
    color: var(--text-light);
    margin-bottom: 2rem;
    max-width: 600px;
}

.cta-button {
    padding: 0.875rem 2rem;
    font-size: 1rem;
    font-weight: 600;
    color: white;
    background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
    border: none;
    border-radius: 0.5rem;
    cursor: pointer;
    transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.cta-button:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(79, 70, 229, 0.4);
}

/* Sections */
section {
    padding: 4rem 5%;
}

.about, .contact {
    text-align: center;
}

section h2 {
    font-size: 2rem;
    margin-bottom: 1rem;
    color: var(--text-color);
}

section p {
    color: var(--text-light);
    max-width: 600px;
    margin: 0 auto;
}

/* Footer */
footer {
    text-align: center;
    padding: 2rem;
    background: var(--bg-secondary);
    color: var(--text-light);
}

/* Responsive Design */
@media (max-width: 768px) {
    .nav-links {
        display: none;
    }

    .hero h1 {
        font-size: 2rem;
    }

    .hero p {
        font-size: 1rem;
    }
}
    """.trimIndent()

    val JS = """
// DOM Content Loaded
document.addEventListener('DOMContentLoaded', function() {
    console.log('Website loaded successfully!');

    // Smooth scrolling for anchor links
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function(e) {
            e.preventDefault();
            const target = document.querySelector(this.getAttribute('href'));
            if (target) {
                target.scrollIntoView({
                    behavior: 'smooth',
                    block: 'start'
                });
            }
        });
    });

    // CTA Button click handler
    const ctaButton = document.querySelector('.cta-button');
    if (ctaButton) {
        ctaButton.addEventListener('click', function() {
            alert('Thanks for clicking! Add your own functionality here.');
        });
    }
});
    """.trimIndent()
}
