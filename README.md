<h1 align="center">Cyclauncher</h1>

<p align="center">
  <img src="assets/logo.png" width="128" height="128" alt="Cyclauncher Logo">
</p>

A modern, minimalist Android launcher built with **Jetpack Compose**. Fast and intuitive app navigation is provided via a unique rectangular alphabet wheel.

> [!IMPORTANT]
> **Alpha Version**: This project is currently in early development. Features are subject to change, and bugs may be encountered as the experience is refined.

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

- **Rectangular Alphabet Wheel**: Fast app access via a custom interactive wheel, optimized for high-performance scrolling and rendering.
- **Dynamic Favorites**: Organize your top apps with intuitive drag-and-drop reordering (long-press star to enter) and quick-removal tools.
- **AI-Powered Organization**: Categorize your apps efficiently with an AI-assisted tagging workflow (Export → Process via External AI Prompt → Import) and full tag backup support. *Note: AI processing is performed externally using your preferred provider.*
- **Flexible Data Management**: Robust import/export support for app names and tags in both JSON and plain text formats.
- **Customizable Themes**: Selectable accent colors, customizable main text color (Black/White) via an interactive switcher, and adaptive shadow inversion for optimal contrast on any wallpaper.

## 🤝 Community & Support

- **Discord**: Join the community for feedback and updates: [![Discord](https://img.shields.io/badge/Discord-Join%20Community-7289DA?style=for-the-badge&logo=discord)](https://discord.gg/9cnf49JnM)
- **Tribute**: Support the development of this project: [![Support on Tribute](https://img.shields.io/badge/Support-Tribute-orange?style=for-the-badge)](https://web.tribute.tg/e/1dW)

## 🛠 Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **Architecture**: MVVM with StateFlow
- **Graphics**: High-performance animations powered by the low-level Canvas API.
- **Documentation**: Enriched with comprehensive inline KDoc for improved maintainability.

## 🚀 Getting Started

Testing of this alpha version is performed by cloning the repository and running it via Android Studio.

1. The repository is cloned: `git clone https://github.com/msbluesnow/Cyclauncher.git`
2. The project is opened in **Android Studio Ladybug (or newer)**.
3. The application is built and deployed to a device.

### CLI Build Instructions
For automated systems or command-line enthusiasts, the application is built using the following commands:
```bash
chmod +x gradlew
./gradlew assembleRelease
```

## 🗺️ Roadmap

A continuous, unified timeline of completed milestones and planned updates.

<kbd>&nbsp;✓&nbsp;</kbd> <b>Letter-Based Scroll Wheel</b> — Interactive rectangular scroll wheel for high-performance app retrieval.<br>
<kbd>&nbsp;✓&nbsp;</kbd> <b>Application Tag System</b> — Grouping apps with an AI-assisted tagging workflow and full JSON backup options.<br>
<kbd>&nbsp;✓&nbsp;</kbd> <b>Adaptive Text & Theme Accents</b> — Selectable accent palettes, a custom Main Color (Black/White) switcher, and dynamic shadow inversion.<br>
<kbd>&nbsp;✓&nbsp;</kbd> <b>Performance Tuning</b> — Asynchronous Coil icon prefetching and sequential package manager querying to prevent system UI freezes under Battery Saver mode.<br>
<kbd>&nbsp;&nbsp;&nbsp;</kbd> <i>Tag Map</i> — Interactive tag map showing connections between applications and implementing quick tag-based navigation.<br>
<kbd>&nbsp;&nbsp;&nbsp;</kbd> <i>App Shortcuts</i> — Quick-launch actions like dialing specific contacts or opening deep-linked settings.<br>
<kbd>&nbsp;&nbsp;&nbsp;</kbd> <i>Widgets Integration</i> — Full support for configuring and pinning dynamic Android widgets on the home layout.<br>
<kbd>&nbsp;&nbsp;&nbsp;</kbd> <i>Localization</i> — Native translation support for multiple popular world languages.<br>
<kbd>&nbsp;&nbsp;&nbsp;</kbd> <i>3D Hex Search Grid</i> — Immersive 3D application navigation styled as a rotatable hexagonal prism.<br>


## 📜 License

This project is licensed under the **GNU GPLv3** - see the [LICENSE](LICENSE) file for details.

---
*Developed with ❤️ using Jetpack Compose.*
