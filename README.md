<h1 align="center">Cyclauncher</h1>

<p align="center">
  <img src="assets/logo.png" width="128" height="128" alt="Cyclauncher Logo">
</p>

<p align="center">
  <a href="https://f-droid.org/packages/dev.msbs.cyclauncher/">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80">
  </a>
</p>

<p align="center">
  <a href="https://f-droid.org/packages/dev.msbs.cyclauncher/">
    <img src="https://img.shields.io/f-droid/v/dev.msbs.cyclauncher?style=for-the-badge&logo=f-droid&logoColor=white&label=F-Droid" alt="F-Droid Version">
  </a>
  <a href="https://github.com/msbluesnow/Cyclauncher/releases">
    <img src="https://img.shields.io/github/downloads/msbluesnow/Cyclauncher/total?style=for-the-badge&logo=github&color=blue&label=Downloads" alt="Total Downloads">
  </a>
  <a href="https://github.com/msbluesnow/Cyclauncher/actions/workflows/fdroid-check.yml">
    <img src="https://img.shields.io/github/actions/workflow/status/msbluesnow/Cyclauncher/fdroid-check.yml?style=for-the-badge&label=F-Droid%20Check&logo=github" alt="F-Droid Status Check">
  </a>
</p>

Cyclauncher is **not just yet another bicycle**. Built with **Jetpack Compose**, it is focused on speed, effortless app accessibility, and seamless one-handed usability. New features and mechanics are continuously designed not just to be unique, but to deliver a genuinely convenient, ergonomic, and practical daily experience. Fast and intuitive navigation is provided via versatile search methods including a custom rectangular alphabet wheel, an ergonomic side alphabet index strip, and instant text filtering.

> [!IMPORTANT]
> **Alpha Version**: This project is currently in early development. Features are subject to change, and bugs may be encountered as the experience is refined.

> [!TIP]
> **Get the Latest Build**: Download the latest signed APK (**v0.9.0-alpha**, Build 16) directly from [GitHub Releases](https://github.com/msbluesnow/Cyclauncher/releases/tag/v0.9.0-alpha).

## 📽️ Demo Showcases

<table style="width: 100%; border: none;">
  <tr>
    <td align="center" style="border: none; width: 33%;">
      <b>Uninstalling apps</b><br><br>
      <video src="https://github.com/user-attachments/assets/d769d4b5-c7e7-4843-abf8-8f19cb0b5ae6" width="100%" autoplay loop muted playsinline></video>
    </td>
    <td align="center" style="border: none; width: 33%;">
      <b>Remove apps from history</b><br><br>
      <video src="https://github.com/user-attachments/assets/ff1584bd-d147-4c3f-97f9-5dbdfd1145b9" width="100%" autoplay loop muted playsinline></video>
    </td>
    <td align="center" style="border: none; width: 33%;">
      <b>Change accent color and search for applications</b><br><br>
      <video src="https://github.com/user-attachments/assets/4f253962-1167-4a46-ab79-d45350c8709a" width="100%" autoplay loop muted playsinline></video>
    </td>
  </tr>
</table>

## ✨ Key Features

- **Tag Folder System & Tag Favorites**: Interactive tag folders on the main screen with color accents, 2x2 live icon previews, proximity-aware popup menus, and the ability to pin tag folders directly alongside favorite apps with smooth drag-and-drop reordering.
- **Versatile Search Modes**: Rapid app navigation via a custom **Rectangular Alphabet Wheel** (with inertia physics), an ergonomic **Side Alphabet Index** strip for instant thumb scrubbing, or instant **Text Search**.
- **Character & Symbol Mapping**: Comprehensive custom mapping engine to bind any symbol, emoji (🤗, 🎮...), or international alphabet letter (Cyrillic, Arabic, German/Nordic, Romance) to specific search index buckets (`A`–`Z`, `#`) with instant presets.
- **Interactive Gesture Tutorial**: Guided onboarding overlay teaching launcher gestures (search, notifications, favorites & history management, system navigation) with animated visualizers.
- **Dynamic Favorites & History Badges**: Organize top apps with intuitive drag-and-drop reordering, and track newly installed or updated apps via subtle visual update badges in history.
- **AI-Assisted App Tagging**: Fast batch categorization of applications with an external AI tagging workflow (Export → Process via External Prompt → Import) and full JSON backup options.
- **Deep Theme Customization & Accessibility**: Echo Icon Theme accent palettes, dynamic Material You wallpaper colors, interactive custom Color Picker, primary text color modes (Black/White) with adaptive wallpaper drop-shadows, customizable shadow colors, and an accessibility toggle to disable all animations including cursor blinking.

## 🤝 Community & Support

- **Discord**: Join the community for feedback and updates: [![Discord](https://img.shields.io/badge/Discord-Join%20Community-7289DA?style=for-the-badge&logo=discord)](https://discord.gg/Zw4EBe92Qn)
- **Contributing**: Check out [CONTRIBUTING.md](CONTRIBUTING.md) to learn how to set up the project, submit pull requests, and report issues (specifying versions and attaching reproduction videos).
- **Tribute**: Support the development of this project: [![Support on Tribute](https://img.shields.io/badge/Support-Tribute-orange?style=for-the-badge)](https://web.tribute.tg/e/1dW)

## 🛠 Tech Stack

- **Language**: [Kotlin 2.2.10](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3 & Compose BOM)
- **Build System**: Gradle 9.6.1 + Android Gradle Plugin 9.3.2 (Target SDK 36, Min SDK 24)
- **Architecture**: MVVM with Kotlin Coroutines & `StateFlow`
- **Image Loading**: [Coil 3](https://coil-kt.github.io/coil/) with custom asynchronous `AppIconFetcher`
- **Graphics**: High-performance custom animations rendered via the low-level Android Canvas API
- **Code Quality & Docs**: Strict Kotlin code style with comprehensive inline KDoc documentation

## 🚀 Getting Started

To build and run Cyclauncher locally:

1. **Clone the repository:**
   ```bash
   git clone https://github.com/msbluesnow/Cyclauncher.git
   cd Cyclauncher
   ```
2. **Open in IDE:**
   - Open the project in **Android Studio Meerkat / Ladybug (or newer)** or **VS Code**.
   - Ensure JDK 17+ (or bundled JBR) and Android SDK 36 are installed.
3. **Build via CLI:**
   ```bash
   # Linux / macOS
   chmod +x gradlew
   ./gradlew assembleDebug

   # Windows
   .\gradlew.bat assembleDebug
   ```

## 🗺️ Roadmap

A continuous, unified timeline of completed milestones and planned updates.

<kbd>&nbsp;✓&nbsp;</kbd> <b>Letter-Based Scroll Wheel</b> — Interactive rectangular scroll wheel for high-performance app retrieval.<br>
<kbd>&nbsp;✓&nbsp;</kbd> <b>Side Alphabet Search Mode</b> — Ergonomic alphabet index strip for swift one-handed scrubbing and app indexing.<br>
<kbd>&nbsp;✓&nbsp;</kbd> <b>Custom Character & Symbol Search Indexing</b> — Customize first-character rules, emojis, and international alphabets mapped to search letters.<br>
<kbd>&nbsp;✓&nbsp;</kbd> <b>Tag Folder System & Tag Pinning</b> — On-screen tag folders with multi-app previews, quick popups, and favorite pin support.<br>
<kbd>&nbsp;✓&nbsp;</kbd> <b>Application Tag System</b> — Grouping apps with an AI-assisted tagging workflow and full JSON backup options.<br>
<kbd>&nbsp;✓&nbsp;</kbd> <b>Adaptive Text & Theme Accents</b> — Selectable accent palettes, a custom Main Color (Black/White) switcher, dynamic shadow inversion, and animation controls.<br>
<kbd>&nbsp;✓&nbsp;</kbd> <b>Performance Tuning</b> — Asynchronous Coil icon prefetching with safe path filters, and cached default launcher checks to eliminate main thread IPC jank.<br>
<kbd>&nbsp;✓&nbsp;</kbd> <b>Interactive Gesture Tutorial</b> — Guided onboarding overlay teaching launcher gestures with animated visualizers.<br>
<kbd>&nbsp;&nbsp;&nbsp;</kbd> <i>Tag Map</i> — Interactive tag map showing connections between applications and implementing quick tag-based navigation.<br>
<kbd>&nbsp;&nbsp;&nbsp;</kbd> <i>App Shortcuts</i> — Quick-launch actions like dialing specific contacts or opening deep-linked settings.<br>
<kbd>&nbsp;&nbsp;&nbsp;</kbd> <i>Widgets Integration</i> — Full support for configuring and pinning dynamic Android widgets on the home layout.<br>
<kbd>&nbsp;&nbsp;&nbsp;</kbd> <i>Localization</i> — Native translation support for multiple popular world languages.<br>
<kbd>&nbsp;&nbsp;&nbsp;</kbd> <i>3D Hex Search Grid</i> — Immersive 3D application navigation styled as a rotatable hexagonal prism.<br>



## ⚠️ Known Issues

### Custom ROM Gesture Interception (crDroid / LineageOS / Quickstep)
* **Symptom:** When switching between Cyclauncher (or any third-party launcher) and the built-in system launcher (e.g., Trebuchet on crDroid / LineageOS), the SystemUI gesture navigation bar may occasionally fail to trigger the "Home" animation or open Recents Overview when opening a non-default launcher directly.
* **Root Cause:** This is a system-level behavior in custom ROMs (crDroid / LineageOS) where the bundled SystemUI/Quickstep provider is compiled into `/system/priv-app/`. When a non-default launcher with `CATEGORY_HOME` in its manifest is opened, Quickstep suppresses the Home swipe animation to prevent feedback loops between launcher activities.
* **Workaround / Resolution:** Cyclauncher mitigates this behavior by capturing the Home intent inside `onNewIntent()` and immediately finishing the activity when launched while not set as default. If gestures become unresponsive on custom ROMs, simply re-select your preferred launcher in **Settings → Default Apps → Home app**.

## 📜 License

This project is licensed under the **GNU GPLv3** - see the [LICENSE](LICENSE) file for details.

---
*Developed with ❤️ using Jetpack Compose.*
