# Security guidance (deployment)

This document intentionally avoids describing internal business logic. It focuses on secure deployment of the application on **bare Apache Tomcat (Windows Service)**.

## Threat model (high level)
- **Unauthorized access**: access without valid credentials or license.
- **Network exposure**: open HTTP port on server reachable from untrusted networks.
- **Credential guessing**: brute-force login attempts.
- **File share access**: service account access to SMB shares.
- **Artifact tampering**: modified `.war`, config, or license files.

## Required baseline controls
- **Run Tomcat on a dedicated service account**
  - Prefer a dedicated AD/domain service user (recommended).
  - Avoid running as `LocalSystem` unless strictly necessary.
  - Grant the service account only the needed NTFS/SMB permissions (read/watch, write/output).
- **Restrict network access**
  - Firewall: allow inbound traffic only from known subnets/IPs.
  - Do not expose Tomcat to the public Internet.
- **Use HTTPS**
  - Configure TLS either in Tomcat (`server.xml` connector) or via an approved reverse proxy.
  - If you keep HTTP (not recommended), limit exposure to internal networks only.
- **Disable unused Tomcat apps**
  - Remove/disable `manager`, `host-manager`, sample apps if present.
- **Protect configuration + license files**
  - Store `converter.properties`, `license.json`, `license.json.sig` in `TOMCAT_HOME/conf` (or another protected directory).
  - NTFS permissions: readable by the Tomcat service account only (and administrators).
  - Avoid storing secrets in world-readable paths.

## Authentication & sessions
- **Passwords**
  - Use strong passwords (length ≥ 14).
  - Rotate credentials on personnel changes.
  - Do not reuse credentials across customers/environments.
- **Session security**
  - Ensure cookies are `HttpOnly` and `Secure` when using HTTPS.
  - Configure a reasonable session timeout.

## File uploads & input safety
- Only allow expected file types and size limits.
- Keep dependencies updated (JDK + Tomcat + libraries).

## Logging & monitoring
- Enable server-side logs (Tomcat + application logs).
- Monitor failed login attempts and unexpected spikes.
- Keep logs in a protected location; rotate and archive.

## Update process (safe)
- Stop Tomcat service.
- Replace the `.war` atomically (copy to temp → rename).
- Start Tomcat service.
- Verify application health and license status.

## Incident response (minimal)
- If compromise is suspected: isolate server network access, preserve logs, rotate credentials, redeploy from a known-good artifact.

