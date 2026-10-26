//! In-app Windows update.
//!
//! The user asks for this. We download the official NSIS installer from the
//! VGallery GitHub release, then start it quietly and leave so it can replace
//! this exe and reopen the app. Nothing is saved into Downloads for them to run.

use std::fs::{self, File};
use std::io::{Read, Write};
use std::path::Path;
use std::process::Command;
use std::time::Duration;

use serde::Serialize;
use tauri::{AppHandle, Emitter};

const MAX_BYTES: u64 = 80 * 1024 * 1024;
const MIN_BYTES: u64 = 100 * 1024;

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct UpdateProgress {
    received: u64,
    total: u64,
    phase: &'static str,
}

struct HttpsUrl {
    host: String,
    path: String,
}

fn parse_https(raw: &str) -> Result<HttpsUrl, String> {
    let rest = raw
        .trim()
        .strip_prefix("https://")
        .ok_or("Updates are only downloaded over HTTPS.")?;
    if rest.is_empty() || rest.contains('@') || rest.contains('\\') || rest.contains(' ') {
        return Err("That update link is not the official VGallery release.".into());
    }
    let (authority, path) = rest.split_once('/').unwrap_or((rest, ""));
    let host = match authority.rsplit_once(':') {
        Some((host, port)) => {
            if port != "443" {
                return Err("That update link is not the official VGallery release.".into());
            }
            host
        }
        None => authority,
    };
    if host.is_empty() || host.contains(':') {
        return Err("That update link is not the official VGallery release.".into());
    }
    Ok(HttpsUrl {
        host: host.to_ascii_lowercase(),
        path: format!("/{path}"),
    })
}

/// The address the UI got from the GitHub API. Redirects are checked separately.
fn allow_release_url(raw: &str) -> Result<(), String> {
    let url = parse_https(raw)?;
    let path = url.path.to_ascii_lowercase();
    if url.host != "github.com"
        || !path.starts_with("/realmjunaid/vgallery/releases/download/")
        || !path.split('?').next().unwrap_or("").ends_with(".exe")
    {
        return Err("Updates can only come from the official VGallery GitHub release.".into());
    }
    Ok(())
}

fn allow_redirect(raw: &str) -> Result<(), String> {
    let url = parse_https(raw)?;
    let ok = matches!(
        url.host.as_str(),
        "github.com"
            | "release-assets.githubusercontent.com"
            | "objects.githubusercontent.com"
            | "github-releases.githubusercontent.com"
    );
    if !ok {
        return Err("The update download was redirected away from GitHub.".into());
    }
    Ok(())
}

fn resolve_redirect(current: &str, location: &str) -> Result<String, String> {
    let location = location.trim();
    if location.starts_with("https://") {
        return Ok(location.to_string());
    }
    if location.starts_with("//") {
        return Ok(format!("https:{location}"));
    }
    let origin = current
        .find("://")
        .and_then(|scheme| current[scheme + 3..].find('/').map(|end| &current[..scheme + 3 + end]))
        .ok_or("Could not follow the update download.")?;
    if let Some(path) = location.strip_prefix('/') {
        return Ok(format!("{origin}/{path}"));
    }
    Err("Could not follow the update download.".into())
}

fn emit(app: &AppHandle, received: u64, total: u64, phase: &'static str) {
    let _ = app.emit(
        "vgallery:update-progress",
        UpdateProgress {
            received,
            total,
            phase,
        },
    );
}

fn download_installer(app: &AppHandle, start_url: &str, dest: &Path) -> Result<(), String> {
    allow_release_url(start_url)?;
    // ureq does not turn native-tls on by itself. Windows uses the system certificate store.
    let tls = std::sync::Arc::new(
        ureq::native_tls::TlsConnector::new()
            .map_err(|_| "Could not start a secure download.".to_string())?,
    );
    let agent = ureq::AgentBuilder::new()
        .https_only(true)
        .redirects(0)
        .timeout(Duration::from_secs(600))
        .tls_connector(tls)
        .build();

    let mut current = start_url.to_string();
    for hop in 0..6 {
        if hop > 0 {
            allow_redirect(&current)?;
        }
        let result = agent
            .get(&current)
            .set("User-Agent", "VGallery")
            .set("Accept", "application/octet-stream")
            .call();
        // redirects(0) still returns a 302 as a normal response. Follow only GitHub's CDN.
        let redirect = match &result {
            Ok(response) if (300..400).contains(&response.status()) => response.header("location").map(str::to_string),
            Err(ureq::Error::Status(code, response)) if (300..400).contains(code) => {
                response.header("location").map(str::to_string)
            }
            _ => None,
        };
        if let Some(location) = redirect {
            current = resolve_redirect(&current, &location)?;
            continue;
        }
        match result {
            Ok(response) => return write_installer(app, response, dest),
            Err(ureq::Error::Status(code, _)) => {
                return Err(format!("GitHub answered {code}. Try again later."));
            }
            Err(error) => {
                let detail = error.to_string();
                if detail.is_empty() {
                    return Err(
                        "Could not download the update. Check your connection and try again.".into(),
                    );
                }
                return Err(format!("Could not download the update. {detail}"));
            }
        }
    }
    Err("The update download was redirected too many times.".into())
}

fn write_installer(app: &AppHandle, response: ureq::Response, dest: &Path) -> Result<(), String> {
    let total = response
        .header("content-length")
        .and_then(|value| value.parse::<u64>().ok())
        .unwrap_or(0);
    if total > MAX_BYTES {
        return Err("That update is larger than a VGallery installer.".into());
    }

    let part = dest.with_extension("part");
    let cleanup = || {
        let _ = fs::remove_file(&part);
    };
    let mut file = match File::create(&part) {
        Ok(file) => file,
        Err(error) => {
            cleanup();
            return Err(format!("Could not save the update: {error}"));
        }
    };
    let mut reader = response.into_reader();
    let mut buf = [0_u8; 64 * 1024];
    let mut received = 0_u64;
    let mut header = [0_u8; 2];
    let mut header_len = 0_usize;

    loop {
        let read = match reader.read(&mut buf) {
            Ok(0) => break,
            Ok(n) => n,
            Err(_) => {
                cleanup();
                return Err("The update download stopped. Check your connection and try again.".into());
            }
        };
        if header_len < 2 {
            let take = (2 - header_len).min(read);
            header[header_len..header_len + take].copy_from_slice(&buf[..take]);
            header_len += take;
            if header_len == 2 && &header != b"MZ" {
                cleanup();
                return Err("The downloaded file is not a Windows installer.".into());
            }
        }
        received += read as u64;
        if received > MAX_BYTES {
            cleanup();
            return Err("That update is larger than a VGallery installer.".into());
        }
        if file.write_all(&buf[..read]).is_err() {
            cleanup();
            return Err("Could not save the update.".into());
        }
        emit(app, received, total, "downloading");
    }
    if file.flush().is_err() {
        cleanup();
        return Err("Could not save the update.".into());
    }
    drop(file);

    if received < MIN_BYTES {
        cleanup();
        return Err("The downloaded update looks incomplete.".into());
    }
    if fs::rename(&part, dest).is_err() {
        cleanup();
        return Err("Could not prepare the installer.".into());
    }
    Ok(())
}

#[cfg(windows)]
fn launch_installer(path: &Path) -> Result<(), String> {
    use std::os::windows::process::CommandExt;
    const CREATE_NO_WINDOW: u32 = 0x0800_0000;

    let path_text = path.to_string_lossy().replace('\'', "''");
    // `/S` silent, `/UPDATE` keep settings and shortcuts, `/R` reopen VGallery.
    // Start-Process detaches the installer so it survives when this app exits.
    let script = format!(
        "Start-Process -FilePath '{path_text}' -ArgumentList '/S','/UPDATE','/R'"
    );
    let mut child = Command::new("powershell")
        .args([
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-WindowStyle",
            "Hidden",
            "-Command",
            &script,
        ])
        .creation_flags(CREATE_NO_WINDOW)
        .spawn()
        .map_err(|error| format!("Could not start the installer: {error}"))?;
    let status = child
        .wait()
        .map_err(|error| format!("Could not start the installer: {error}"))?;
    if !status.success() {
        return Err("The installer did not start.".into());
    }
    Ok(())
}

#[cfg(not(windows))]
fn launch_installer(_path: &Path) -> Result<(), String> {
    Err("In-app updates are only available on Windows.".into())
}

pub fn install_blocking(app: &AppHandle, url: &str) -> Result<(), String> {
    let dir = std::env::temp_dir().join("VGalleryUpdate");
    fs::create_dir_all(&dir).map_err(|error| format!("Could not prepare the update folder: {error}"))?;
    let dest = dir.join("VGallery-setup.exe");
    let _ = fs::remove_file(&dest);
    download_installer(app, url, &dest)?;
    emit(app, 1, 1, "installing");
    std::thread::sleep(Duration::from_millis(280));
    launch_installer(&dest)?;
    // The installer replaces this exe, so we have to be gone before it copies files.
    std::process::exit(0);
}
