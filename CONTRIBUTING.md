# Contributing to Cyclauncher

Thank you for your interest in contributing to **Cyclauncher**! We welcome bug fixes, performance optimizations, feature additions, and documentation improvements.

This guide provides an overview of the project setup, architectural conventions, and the workflow for submitting pull requests.

---

## 🛠️ Prerequisites & Setup

1. **JDK 17 or higher** (JDK 17/21 or Android Studio JBR).
2. **Android Studio (Meerkat/Ladybug or newer)** or **VS Code**.
3. **Android SDK 36** with Build Tools installed (minimum SDK: 24 / Android 7.0).

### Build & Run locally

```bash
# Clone the repository
git clone https://github.com/msbluesnow/Cyclauncher.git
cd Cyclauncher

# Linux / macOS
chmod +x gradlew
./gradlew assembleDebug

# Windows (Command Prompt / PowerShell)
.\gradlew.bat assembleDebug
```

> **Tip:** Testing on a **physical device** is strongly encouraged. Launcher features like system gesture navigation (swipe-up to home), recent app transitions, and direct boot handling can behave differently on emulators.

---

## 📂 Project Architecture

Cyclauncher is built using **Kotlin 2.2.10**, **Jetpack Compose** (Material 3), and **MVVM architecture** with Kotlin Coroutines and `StateFlow`.

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
│   └── AppActionsManager.kt        # Favorites, history, tags, custom labels, character maps & AI auto-tagging
│
├── model/                         # Core data models
│   ├── AppInfo.kt                 # Application metadata (packageName, activityName, label, searchChar)
│   ├── FavoriteItem.kt            # Polymorphic favorite entries (FavoriteApp vs FavoriteTag)
│   └── Tag.kt                     # Custom tag model (id, name, color)
│
├── ui/                            # User Interface
│   ├── components/
│   │   ├── AppActionMenu.kt             # Context menu (Favorites, Edit Label, Tags, Uninstall)
│   │   ├── AppUiComponents.kt           # Shared UI elements (AppIconPainter, AppListItemWithIcon)
│   │   ├── KeepAndroidOpenBanner.kt     # Keep Android Open countdown banner & FreeDroidWarn integration
│   │   ├── RectangularAlphabetWheel.kt  # Custom Canvas-rendered wheel with deceleration physics
│   │   ├── SideAlphabetSearchLayout.kt  # Side index strip for rapid thumb-scrubbing app retrieval
│   │   ├── TagComponents.kt             # Components for tag folders, chips, and selection dialogs
│   │   └── TutorialOverlay.kt           # Interactive onboarding tutorial overlay demonstrating gestures
│   ├── screens/
│   │   ├── MainMenuScreen.kt            # Main screen (Favorites drag & drop, History with adaptive shadows)
│   │   ├── SearchScreen.kt              # Letter-filtered app list view
│   │   ├── TextSearchInterface.kt       # Keyboard search interface
│   │   ├── SettingsScreen.kt            # Accent colors, animation controls, hand orientation, default launcher
│   │   ├── CharacterMappingScreen.kt    # Custom character, emoji, and foreign alphabet mapping rules
│   │   └── AutoTagsScreen.kt            # AI-assisted tagging import/export workflow
│   └── theme/
│       ├── AccentColor.kt               # Curated Catppuccin & Nord light/dark accent color pairs
│       ├── PopupTheme.kt                # Proximity-aware popup menus, dialog styling & adaptive shadows
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
2. **Polymorphic Favorites Architecture:** Both applications and tag folders can be pinned and reordered in favorites via the `FavoriteItem` sealed hierarchy.
3. **Character & Alphabet Mapping Engine:** Custom symbol, foreign alphabet, and emoji categorization must route through `AppActionsManager` mapping utilities to preserve search index consistency.
4. **Gesture & SystemUI Compatibility:** Do not re-introduce `windowIsTranslucent` or `FLAG_LAYOUT_NO_LIMITS`. Always preserve standard `taskAffinity` and use `enableEdgeToEdge()` to maintain gesture navigation and Recents overview support.
5. **Direct Boot Safe Context:** Use `getSafeStorageContext()` when accessing SharedPreferences to prevent crashes before device unlock.
6. **F-Droid Compatibility:** Keep dependencies 100% open source. Never embed closed binaries, trackers, or non-FOSS dependencies. Keep `dependenciesInfo.includeInApk = false`.


## 🐛 Reporting Issues

When filing a bug report in our [GitHub Issues](https://github.com/msbluesnow/Cyclauncher/issues), please ensure you provide the following to help us resolve it quickly:
1. **App Version**: Always specify the exact version name or build code of the application you are running (e.g. `v0.8.3-alpha` or `15`). You can find this in the Settings screen or build config.
2. **Detailed Reproducing Steps**: Provide a step-by-step description of what triggers the issue.
3. **Screen Recording (Highly Recommended)**: We highly prefer that you attach a screen recording/video showing the full process of reproducing the issue from start to finish. Visual context is incredibly helpful for tracing touch coordinates, gestures, and layout states.

---

## 🔄 How to Submit Changes

1. **Find an Issue or Start a Discussion**
   - Check open [GitHub Issues](https://github.com/msbluesnow/Cyclauncher/issues) for `good first issue` or `help wanted` tags.
   - For major features or architectural changes, discuss your idea first on [Discord](https://discord.gg/Zw4EBe92Qn) in `#development`.

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

- **Discord**: Join `#development` for technical discussions and help: [Join Discord](https://discord.gg/Zw4EBe92Qn)
- **Issues**: Report bugs or request features via [GitHub Issues](https://github.com/msbluesnow/Cyclauncher/issues).

---

## 📜 License

By contributing, you agree that your contributions will be licensed under the [GNU General Public License v3.0](LICENSE).
