---
title: The Desktop App
sidebar_position: 2
---

# The Desktop App

RDFArchitect also runs as an app on your own computer, without a server, Docker or Fuseki. It is the same editor; the backend runs locally and the editor opens in a window of its own.

## Download and start

Every [release on GitHub](https://github.com/SOPTIM/RDFArchitect/releases) carries the app for Windows, macOS and Linux. Each comes with its own Java runtime, so nothing else needs to be installed.

| System | Portable (unpack and run) | Installer |
| --- | --- | --- |
| Windows | `RDFArchitect-<version>-windows-x64.zip`: unpack it and run `RDFArchitect\RDFArchitect.exe` | `…-windows-x64.msi`: installs for your user only, no administrator rights needed, with a Start menu entry |
| macOS | `RDFArchitect-<version>-mac-arm64.zip` (Apple silicon) or `…-mac-x64.zip` (Intel): unpack it and move `RDFArchitect.app` to *Applications* | `…-mac-arm64.dmg` or `…-mac-x64.dmg` |
| Linux | `RDFArchitect-<version>-linux-x64.tar.gz`: unpack it and run `RDFArchitect/bin/RDFArchitect` | `…-linux-x64.deb` |

The first start may ask once:

- **Windows:** the downloads are signed. For a new version Windows SmartScreen may still show *Windows protected your PC* until the version has become known: choose **More info → Run anyway**.
- **macOS:** the app is not signed, so macOS refuses to open it at first. Open **System Settings → Privacy & Security**, scroll to the message about RDFArchitect and choose **Open Anyway**. On macOS 14 and earlier, right-clicking the app and choosing **Open** does the same. From a terminal, `xattr -dr com.apple.quarantine /Applications/RDFArchitect.app` lets it start as well.

## The window

The editor opens in a window without tabs or address bar, using Chrome, Edge, Brave or Chromium if one is installed. Edge is part of every Windows 10 and 11. Without any of them, the editor opens in a tab of your default browser instead. The browser keeps the app's window position, settings and storage in a profile of its own, apart from your normal browsing.

Starting RDFArchitect again while it runs opens another window of the same app, with the same workspaces.

## Saving and opening workspaces

The desktop app keeps your workspaces in memory while it runs, like the server keeps a session. To keep a workspace, save it as a **workspace file** (`.rdfa`), the same file **File → Export → Workspace** writes on the server:

- **File → Save Workspace** (Ctrl+S) saves the workspace you are working on. The first time it asks where to; afterwards it writes to the same file again without asking.
- **File → Save Workspace As…** (Ctrl+Shift+S) saves it to another file.
- **File → Open Workspace…** (Ctrl+O) opens a workspace file as a new workspace, with all of its schemas, constraints, diagrams and layouts. Saving it again writes back to the file it came from.

Saving to the same file again works where the browser can write files, which Chrome, Edge and Brave can. In other browsers, such as Firefox or Safari, every save downloads the file into your downloads folder, and the workspace counts as saved as soon as the download starts. If you cancel the download, the workspace still counts as saved, so save it again.

With RDFArchitect installed from the installer, double-clicking a `.rdfa` file opens it: RDFArchitect starts if it is not running, and a window shows the file in **Open Workspace**. **File → Save Workspace** then writes back to that file, in every browser. The portable archives do not register `.rdfa` files; start RDFArchitect with the file as argument instead, for example `RDFArchitect.exe model.rdfa`.

While the workspace you are working on has changes that are not saved yet, the toolbar shows **Unsaved**; clicking it saves. Undoing back to where you saved makes the workspace saved again. A workspace without any schema is never reported as unsaved.

## Closing

RDFArchitect stops by itself once its last window is closed; there is nothing else to quit. If a workspace has unsaved changes, closing or reloading the window asks first. The question comes from the browser, so its wording is the browser's.

A window whose RDFArchitect has stopped (for example after a crash, or because its process was ended) says so and offers to reload. Start RDFArchitect again first.

## Restoring unsaved work

While it runs, RDFArchitect keeps a copy of every workspace with unsaved changes in its data directory (see below), every two minutes and once more when it stops. So unsaved work survives a window closed despite the question, a crashed browser or a computer that shuts down. The next start offers those workspaces in **Restore Unsaved Workspaces**:

- **Restore** brings the workspace back as it was kept, under its old name or, when that is taken, with *(recovered)* appended. It is still unsaved: save it to keep it.
- **Discard** deletes the copy.
- **Later** asks again at the next start.

A copy is removed as soon as its workspace is saved or deleted. If RDFArchitect itself crashes, up to the last two minutes of changes can be missing from the copy.

## Differences from the server

- **Share Snapshot** is not available: there is no shared server to keep a snapshot on. Send the workspace file instead.
- RDFArchitect listens on `127.0.0.1` only, so it cannot be reached from other computers. It answers only its own windows.

## Options and files

The app can be started from a terminal with options, for example `RDFArchitect --browser=default` (Windows: `RDFArchitect.exe --browser=default`; macOS: `RDFArchitect.app/Contents/MacOS/RDFArchitect --browser=default`):

| Option | Environment variable | Meaning |
| --- | --- | --- |
| `--browser=app` | `RDFARCHITECT_BROWSER` | Window of its own (default). `default` opens a tab of the default browser, `none` opens nothing and prints the address. |
| `--port=17407` | `RDFARCHITECT_PORT` | Port on `127.0.0.1` (default `17407`). When it is taken, any free port is used. `0` always picks a free one. |
| `--data-dir=<dir>` | `RDFARCHITECT_DATA_DIR` | Where to keep logs and the window's browser profile. |
| `--help` | | Lists the options. |
| `<file>.rdfa` | | Workspace files to open. |

Any backend setting can be given as `--<property>=<value>` as well, for example `--desktop.exit-when-closed=false` to keep RDFArchitect running without a window.

The app keeps its log files, the window's browser profile and the copies of unsaved workspaces in:

- Windows: `%LOCALAPPDATA%\RDFArchitect`
- macOS: `~/Library/Application Support/RDFArchitect`
- Linux: `~/.local/share/rdfarchitect` (or `$XDG_DATA_HOME/rdfarchitect`)

The log is `logs/rdfarchitect.log` there, the copies of unsaved workspaces are in `recovery/`. Deleting the directory resets the window's size and browser settings and drops those copies; your own workspace files are not in it.
