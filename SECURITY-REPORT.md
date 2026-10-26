# Spectre 0.9.1 — behavior and security review

Reviewed: **2026-10-26**. Release: **0.9.1+mc26.2**, tag **v0.9.1-mc26.2**.

Installable JAR SHA-256: `615fd43520271d820fa6225dc05050c8accce15614a1c2fa4ba357322558acd2`.

## Plain-language result

The account-switching path reviewed here uses tokens to sign in to Minecraft and rebuild the game's account services. The highlighted `getAccessToken` call passes a token to Mojang's authentication library for the friends service; it is not, by itself, evidence of account theft. No transfer of account tokens to a maintainer-controlled endpoint was identified in the reviewed account flow.

Version 0.9.1 removes the community invite popup, Discord Presence and its IPC implementation, and the webhook notification features. The hardcoded `discord.gg/spectre` invite is removed.

**There is an unresolved local-storage weakness:** accounts added to Spectre's account manager are saved with unencrypted login tokens. Saved proxy passwords are also unencrypted. This review does not label those files safe to share, and does not claim the whole client is malware-free.

## What this report covers

This is a maintainer-side, static source and release-artifact review, not an independent security certification. It examines the account token flow, first-party network call sites, Discord removal, local credential storage, and production packaging. The exact installable JAR is identified by `SHA256SUMS.txt` in the release, with recorded checks in `RELEASE-VERIFICATION.txt`.

No real accounts, private configuration files, or saved credentials were read to conduct this review. No authenticated packet capture, penetration test, or exhaustive audit of Minecraft, Fabric, native libraries, Chromium, or transitive dependencies was performed. The screenshot showing 252 other scanner warnings was not a full findings export; those individual warnings have not all been assessed.

## Feature behavior

| Feature | Trigger and purpose | Data or destination |
| --- | --- | --- |
| Microsoft account sign-in | User selects Microsoft sign-in and approves the browser/device-code flow | Requests to `login.live.com`, `device.auth.xboxlive.com`, `sisu.xboxlive.com`, and `api.minecraftservices.com`. Tokens participate in that authentication flow. |
| Session-token sign-in | User explicitly adds a session token | Bearer token sent to `https://api.minecraftservices.com/minecraft/profile` to validate the profile. |
| Account switcher | User logs into an added/saved account outside a world | Installs that account's session into Minecraft and recreates user, friends, social, profile-key, and reporting services using Mojang's authentication library. |
| Skin Change | Module fetches a requested player's skin | Player name/UUID to Mojang profile services, then a skin texture URL returned by those services. |
| Media Player | User opens the player | JCEF/Chromium runtime initialization and, when required, a dependency-managed runtime download. An isolated profile is stored under `config/spectre/media`. YouTube/SoundCloud and their embedded resources can contact their own services. |
| Desktop media HUD | Module enabled on a supported system | Reads desktop media metadata using local system APIs; optional media controls interact with the media session. |
| Online lyrics | Media HUD lyrics lookup with Lyrics Online enabled (default on) | Track/artist and available album/duration information sent to `lrclib.net`; that server also sees normal connection metadata. Disable Lyrics Online to prevent those online lookups. |
| Proxy checking and routing | User checks/enables a configured proxy | Proxy authentication goes to the selected SOCKS proxy. Connectivity check requests a TCP connection to `1.1.1.1:443` through it. Game routing applies to the chosen game connection, not necessarily every HTTP feature. |
| Schematic builder and overlays | User opens/enables the corresponding tool | Reads schematic files and received game/world data; sends the game interactions required by that feature. No report-upload path was identified in these first-party features. |
| Player Detection and Spawner Protect | User enables/configures the module | Can trigger local alerts, module toggles, item-handling or disconnect actions. Player Detection also has an optional Panic Pay setting (default off), which sends an in-game `/pay` command to the configured target/amount. This is a game action, separate from account authentication. |
| Addons | User installs a Fabric addon | Loads installed addon entrypoints. Additional addons execute their own code and are outside this release review. |
| Discord and webhook notifications | Removed in 0.9.1 | No invite popup, Rich Presence worker, Discord IPC connector, webhook URL setting, or webhook sender implementation remains. |

The endpoint list describes first-party call sites and observed library handoffs. It is not a network firewall allowlist or an exhaustive list of every host a browser, game service, redirect, installed addon, or dependency can contact. Opening a web page normally discloses connection information to that page and its resources.

## Why the scanner highlights `getAccessToken`

The flagged call is in [`GameSession.apply`](Spectre-26.2/src/main/java/net/spectre/client/account/GameSession.java). Its immediate use is:

```java
var friends = this.mojangAuth(mc).createFriendsService(prepared.user().getAccessToken());
```

This runs as part of applying an account switch, after the account manager has prepared the selected account's credentials. The inspected call does not package the token into a webhook or send it to a Spectre reporting server. The related direct login requests are in [`MicrosoftLogin`](Spectre-26.2/src/main/java/net/spectre/client/account/MicrosoftLogin.java), [`SessionLogin`](Spectre-26.2/src/main/java/net/spectre/client/account/SessionLogin.java), and [`AuthHttp`](Spectre-26.2/src/main/java/net/spectre/client/account/AuthHttp.java). The HTTP authentication helper uses HTTPS call sites and does not follow redirects.

Access tokens are sensitive. A scanner recognizing token access is identifying a capability that deserves inspection; a method-name match alone does not establish either malicious intent or safety. A Microsoft application/client ID is an application identifier, not the user's password or an embedded private access token.

## Local files and known limitations

- **Account credentials:** [`Accounts.save`](Spectre-26.2/src/main/java/net/spectre/client/account/Accounts.java) stores added accounts in `config/spectre/accounts.json`. Microsoft entries contain refresh tokens; session entries contain the supplied access token. They are unencrypted. Local software or another person with file access may be able to read them. This remains unresolved in 0.9.1.
- **Proxy credentials:** [`Proxies.save`](Spectre-26.2/src/main/java/net/spectre/client/proxy/Proxies.java) stores configured proxy details, including supplied passwords, as local JSON. They are unencrypted.
- **Media profile:** the embedded browser keeps its own cache/profile. Login cookies or other site data may be present if a user signs into sites there. Its download/runtime/native code was not independently audited in this review.
- **Local settings and caches:** profiles, appearance, media/lyrics caches, skin caches, and schematic files are feature data. Do not assume a whole configuration folder or log is suitable for public upload.
- **Historical strings:** `ShareDictionary` still contains old module/setting names such as `discord-presence` and `webhook-url`. It is a frozen compression dictionary needed to decode existing shared profiles. Its byte content was preserved; those strings do not register a module, open a connection, or send a webhook.
- **Other installed code:** other mods, addons, launchers, or altered JARs can change behavior. This report applies to the specified release artifact, not to any similarly named download.

## How to verify

1. Download the official JAR and `SHA256SUMS.txt` from the release.
2. In PowerShell, run `Get-FileHash -Algorithm SHA256 .\spectre-0.9.1+mc26.2.jar` and compare the published value.
3. Open the JAR with an archive tool or run `jar tf spectre-0.9.1+mc26.2.jar`. A JAR is a ZIP-based archive and compiled Java classes can be inspected or decompiled.
4. Inspect the corresponding release tag. Build instructions are in `README.md`; Java 25 is required.
5. Review `RELEASE-VERIFICATION.txt` for the packaging and removal checks performed on this JAR.

A matching checksum confirms the artifact matches the published one, not that it is safe. Byte-for-byte reproducible builds have not been established. Readers can independently review and build the public source rather than relying on this report's conclusions alone.
