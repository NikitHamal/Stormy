# CodeX

A modern AI-powered web development IDE for Android, optimized for mobile devices.

## Features

- **AI-Powered Development**: Chat with Stormy, your AI coding assistant, to build websites through natural conversation
- **Modern Code Editor**: Syntax highlighting for HTML, CSS, JavaScript, and JSON
- **Live Preview**: Preview your websites in mobile, tablet, and desktop modes
- **File Management**: Full file tree with create, rename, and delete operations
- **Project Templates**: Start with blank, basic HTML, Tailwind CSS, or landing page templates
- **Material 3 Design**: Beautiful, clean UI with dark/light mode support
- **Optimized for Mobile**: Designed for smooth performance on low-end devices

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
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

- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 35

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
app/src/main/java/com/codex/stormy/
├── CodeXApplication.kt       # Application class
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

## Roadmap

- [ ] AI integration with OpenAI/Claude APIs
- [ ] Git integration
- [ ] Multiple AI model support
- [ ] Code completion
- [ ] Export to ZIP
- [ ] Share to hosting services
