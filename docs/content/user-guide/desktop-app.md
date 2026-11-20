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

The app is not signed with a vendor certificate yet, so the first start asks once:

- **Windows** may show *Windows protected your PC*. Choose **More info → Run anyway**.
- **macOS** refuses an app downloaded from the internet that Apple has not checked. Open **System Settings → Privacy & Security** and choose **Open Anyway** next to the message about RDFArchitect, or right-click the app and choose **Open**.

## The window

The editor opens in a window without tabs or address bar, using Chrome, Edge, Brave or Chromium if one is installed. Edge is part of every Windows 10 and 11. Without any of them, the editor opens in a tab of your default browser instead. The browser keeps the app's window position, settings and storage in a profile of its own, apart from your normal browsing.

Starting RDFArchitect again while it runs opens another window of the same app, with the same workspaces.

## Saving and opening workspaces

The desktop app keeps your workspaces in memory while it runs, like the server keeps a session. To keep a workspace, save it as a **workspace file** (`.rdfa`), the same file **File → Export → Workspace** writes on the server:

- **File → Save Workspace** (Ctrl+S) saves the workspace you are working on. The first time it asks where to; afterwards it writes to the same file again without asking.
- **File → Save Workspace As…** (Ctrl+Shift+S) saves it to another file.
- **File → Open Workspace…** (Ctrl+O) opens a workspace file as a new workspace, with all of its schemas, constraints, diagrams and layouts. Saving it again writes back to the file it came from.

Saving to the same file again works where the browser can write files, which Chrome, Edge and Brave can. In other browsers every save downloads the file into your downloads folder.

While the workspace you are working on has changes that are not saved yet, the toolbar shows **Unsaved**; clicking it saves. Undoing back to where you saved makes the workspace saved again. A workspace without any schema is never reported as unsaved.

## Closing

RDFArchitect stops by itself once its last window is closed; there is nothing else to quit. If a workspace has unsaved changes, closing or reloading the window asks first. The question comes from the browser, so its wording is the browser's.

A window whose RDFArchitect has stopped (for example after a crash, or because its process was ended) says so and offers to reload. Start RDFArchitect again first.

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

Any backend setting can be given as `--<property>=<value>` as well, for example `--desktop.exit-when-closed=false` to keep RDFArchitect running without a window.

The app keeps its log files and the window's browser profile in:

- Windows: `%LOCALAPPDATA%\RDFArchitect`
- macOS: `~/Library/Application Support/RDFArchitect`
- Linux: `~/.local/share/rdfarchitect` (or `$XDG_DATA_HOME/rdfarchitect`)

The log is `logs/rdfarchitect.log` there. Deleting the directory resets the window's size and browser settings; your workspace files are not in it.
