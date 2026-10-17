# CodeX

A modern AI-powered web development IDE for Android, optimized for mobile devices.

## Features

- **AI-Powered Development**: Chat with Stormy, your AI coding assistant, to build websites through natural conversation
- **Modern Code Editor**: Syntax highlighting for HTML, CSS, JavaScript, and JSON
- **Live Preview**: Preview your websites in mobile, tablet, and desktop modes
- **File Management**: Full file tree with create, rename, and delete operations
- **Project Templates**: Start with blank, basic HTML, Tailwind CSS, or landing page templates
- **Material 3 Expressive**: Expressive type scale, shape system, motion scheme and components (ButtonGroup, LoadingIndicator, FloatingToolbar, SplitButton)
- **Optimized for Mobile**: Designed for smooth performance on low-end devices

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3 Expressive (material3 1.5.x)
- **Architecture**: MVVM with Repository pattern
- **Database**: Room for local storage
- **Preferences**: DataStore
- **Navigation**: Compose Navigation
- **Fonts**: Poppins

## Supported Web Technologies

- HTML5
- CSS3
- JavaScript (ES6+)
- Tailwind CSS (via CDN)

## Screenshots

Coming soon...

## Building

### Requirements

- Android Studio Narwhal or newer (AGP 9.4)
- **JDK 21** -- AGP 9.4's bundled lint crashes on JDK 17 inside UAST
  (`AsyncExecutionService.getService must not return null`)
- Android SDK 37 (`compileSdk = 37`)

The project uses AGP 9's built-in Kotlin support, so the
`org.jetbrains.kotlin.android` plugin is deliberately absent; Kotlin
compiler options live in the top-level `kotlin { compilerOptions { } }`
block.

### Build Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/codex.git
   cd codex
   ```

2. Generate the release keystore (if not present):
   ```bash
   keytool -genkey -v \
     -keystore keystore/codex-release.jks \
     -keyalg RSA \
     -keysize 2048 \
     -validity 10000 \
     -alias codex \
     -storepass codex2024 \
     -keypass codex2024 \
     -dname "CN=CodeX, OU=Mobile Development, O=CodeX, L=Unknown, ST=Unknown, C=US"
   ```

3. Build the release APK:
   ```bash
   ./gradlew assembleRelease
   ```

The APK will be available at `app/build/outputs/apk/release/app-release.apk`

## CI/CD

This project uses GitHub Actions for continuous integration. On every push to non-WIP branches, the workflow:

1. Sets up JDK 17
2. Generates the keystore if missing
3. Builds both debug and release APKs
4. Uploads artifacts with commit hash naming

## Project Structure

```
app/src/main/java/com/oro/app/
├── OroApplication.kt       # Application class
├── MainActivity.kt           # Main entry point
├── crash/                    # Crash handler
├── data/
│   ├── local/               # Room database
│   └── repository/          # Data repositories
├── domain/
│   └── model/               # Domain models
└── ui/
    ├── navigation/          # Navigation graph
    ├── screens/
    │   ├── debug/           # Crash activity
    │   ├── editor/          # Code editor with chat
    │   ├── home/            # Project list
    │   ├── onboarding/      # First-time setup
    │   ├── preview/         # Web preview
    │   └── settings/        # App settings
    └── theme/               # Material 3 theme
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is open source. See the LICENSE file for details.

## AI providers

The model picker speaks the OpenAI-compatible protocol, so providers are
configuration rather than code -- each is one entry in the `AiProvider` enum.

| Provider | Key needed | Notes |
|---|---|---|
| Pollinations | No | Default. Anonymous tier, supports tool calling. |
| Google Gemini | Yes | Free tier, no billing details. |
| NVIDIA NIM | Yes | Free credits. Nemotron, Qwen, DeepSeek, gpt-oss. |
| Groq | Yes | Free tier. Fastest tokens/sec of the set. |
| Cerebras | Yes | Free tier, generous daily allowance. |
| Mistral | Yes | Free experiment tier. |
| Agnes AI | Yes | Free tier, ~20 RPM. |
| OpenRouter | Yes | Has a catalogue of `:free` models. |
| OpenCode Zen | Yes | Its `*-free` models are restricted to the OpenCode client. |
| Kilo Code | Yes | Routes to OpenRouter behind a Kilo key. |
| DeepInfra | Yes | Its keyless endpoint was retired in 2026. |
| Custom | Optional | Any OpenAI-compatible server, e.g. a local Ollama. |

"Key needed" means the provider will not answer without one. Every key-based
provider above is free to sign up for without billing details -- that is a
different thing from keyless, and the app labels it as such.

## Roadmap

- [x] Multiple AI provider support
- [x] Git integration
- [ ] Apply the Expressive component layer across every screen
- [ ] Code completion
- [ ] Export to ZIP
- [ ] Share to hosting services
