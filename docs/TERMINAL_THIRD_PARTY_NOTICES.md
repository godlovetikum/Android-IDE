# Terminal third-party notices

Android IDE vendors the following source components from **Termux:Terminal** at
upstream tag `v0.118.3` (commit `5b657c6adf4304e5198951ce815fe0205dcac29c`):

- `terminal-emulator`: ANSI/VT terminal emulation, transcript buffer, session lifecycle, and JNI PTY boundary.
- `terminal-view`: interactive Android terminal view, keyboard handling, scrollback gestures, and selection UI.

Upstream source: <https://github.com/termux/termux-app/tree/v0.118.3>

Only these terminal components and the PTY source are included. The full Termux
application, `termux-shared`, package manager data, and unrelated packages are
not included in the Android IDE source tree.

## Licenses

- Termux project license: GPLv3-only. The complete upstream notice is included at [`third_party/termux-app-LICENSE.md`](third_party/termux-app-LICENSE.md).
- Terminal emulator/view code exception: Apache License 2.0. The complete license text is included at [`third_party/apache-2.0.txt`](third_party/apache-2.0.txt).

The runtime bootstrap archives are separate build inputs fetched by
`scripts/fetch-termux-bootstrap.sh`; their checksums and source URLs must remain
pinned in that script and must be included in release notices for the selected
ABI.
