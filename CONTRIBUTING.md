# Contributing to Cyclauncher

Thank you for your interest in contributing to **Cyclauncher**! We welcome bug fixes, performance optimizations, feature additions, and documentation improvements.

This guide provides an overview of the project setup, architectural conventions, and the workflow for submitting pull requests.

---

## 🛠️ Prerequisites & Setup

1. **JDK 17 or higher** (bundled with Android Studio).
2. **Android Studio Meerkat (2025.1)** or newer recommended.
3. **Android SDK 36** with Build Tools installed (minimum SDK: 24 / Android 7.0).

### Build & Run locally

```bash
# Clone the repository
git clone https://github.com/msbluesnow/Cyclauncher.git
cd Cyclauncher

# Build debug APK via Gradle wrapper
./gradlew assembleDebug
```

> **Tip:** Testing on a **physical device** is strongly encouraged. Launcher features like system gesture navigation (swipe-up to home), recent app transitions, and direct boot handling can behave differently on emulators.

---

## 📂 Project Architecture

Cyclauncher is built using **Kotlin**, **Jetpack Compose**, and **MVVM architecture** with `StateFlow`.

```
app/src/main/java/dev/msbs/cyclauncher/
├── MainActivity.kt                # Activity entry point, pager states, BackHandler logic, edge-to-edge
├── LauncherViewModel.kt            # Core ViewModel handling StateFlows, app filtering, theme state
├── CyclauncherApp.kt               # Application class with Coil 3 ImageLoader setup & memory management
│
├── coil/                          # Custom Coil 3 asynchronous app icon fetcher
│   └── AppIconFetcher.kt
│
├── data/                          # Data layer & persistence
│   └── AppActionsManager.kt        # Favorites, history, tags, custom labels & AI auto-tagging
│
├── model/                         # Core data models
│   ├── AppInfo.kt                 # Application metadata (packageName, activityName, label, searchChar)
│   └── Tag.kt                     # Custom tag model (id, name, color)
│
├── ui/                            # User Interface
│   ├── components/
│   │   ├── RectangularAlphabetWheel.kt  # Custom Canvas-rendered wheel with deceleration physics
│   │   ├── AppActionMenu.kt             # Context menu (Favorites, Edit Label, Tags, Uninstall)
│   │   └── AppUiComponents.kt           # Shared UI elements (AppIconPainter, AppListItemWithIcon)
│   ├── screens/
│   │   ├── MainMenuScreen.kt            # Main screen (Favorites drag & drop, History with adaptive shadows)
│   │   ├── SearchScreen.kt              # Letter-filtered app list view
│   │   ├── TextSearchInterface.kt       # Keyboard search interface
│   │   ├── SettingsScreen.kt            # Accent colors (Catppuccin/Nord), hand orientation, default launcher
│   │   └── AutoTagsScreen.kt            # AI-assisted tagging import/export workflow
│   └── theme/
│       ├── AccentColor.kt               # Curated Catppuccin & Nord light/dark accent color pairs
│       └── PrimaryTextColor.kt          # White/Black text modes with adaptive drop-shadow calculation
│
└── utils/                         # Utilities
    └── StorageUtils.kt            # Direct Boot & safe device-protected storage context helper
```

---

## 💡 Development Guidelines

### Code Style & Standards
- Write clean, idiomatic **Kotlin**.
- Use **Jetpack Compose** for all UI components — avoid XML layouts.
- Follow **MVVM**: UI state belongs in `LauncherViewModel.kt`, persistent data logic in `AppActionsManager.kt`.
- Document public classes and non-trivial functions using **KDoc** comments.

### Key Performance & Architectural Principles
1. **Asynchronous Icon Fetching:** Never load bitmaps directly inside ViewModels or synchronous lists. App icons are fetched on demand by Coil via `AppIconFetcher.kt`, keeping RAM overhead low.
2. **Gesture & SystemUI Compatibility:** Do not re-introduce `windowIsTranslucent` or `FLAG_LAYOUT_NO_LIMITS`. Always preserve standard `taskAffinity` and use `enableEdgeToEdge()` to maintain gesture navigation and Recents overview support.
3. **Direct Boot Safe Context:** Use `getSafeStorageContext()` when accessing SharedPreferences to prevent crashes before device unlock.

---

## 🔄 How to Submit Changes

1. **Find an Issue or Start a Discussion**
   - Check open [GitHub Issues](https://github.com/msbluesnow/Cyclauncher/issues) for `good first issue` or `help wanted` tags.
   - For major features or architectural changes, discuss your idea first on [Discord](https://discord.gg/9cnf49JnM) in `#development`.

2. **Create a Feature Branch**
   ```bash
   git checkout -b feat/your-feature-name
   # or
   git checkout -b fix/your-bug-description
   ```

3. **Commit Messages**
   Use conventional commit prefixes:
   - `feat:` for new features
   - `fix:` for bug fixes
   - `perf:` for performance improvements
   - `refactor:` for code restructures
   - `docs:` for documentation updates

4. **Submit a Pull Request**
   - Push your branch to your fork and submit a PR against `master`.
   - Include a concise explanation of what your PR changes and why.
   - Attach screenshots or screen recordings for any visual/UI changes.

---

## 🤝 Community & Support

- **Discord**: Join `#development` for technical discussions and help: [Join Discord](https://discord.gg/9cnf49JnM)
- **Issues**: Report bugs or request features via [GitHub Issues](https://github.com/msbluesnow/Cyclauncher/issues).

---

## 📜 License

By contributing, you agree that your contributions will be licensed under the [GNU General Public License v3.0](LICENSE).
