"""Read-only production packaging checks. This is not a malware scanner."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FROZEN_DICTIONARY_SHA256 = "e7cbdd41e8bb1cdf3cc23389f7aa7af0baa0dfe4f50172520b6c3f49041a0ef0"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--observed-date", required=True, help="Report date, YYYY-MM-DD")
    args = parser.parse_args()
    assert re.fullmatch(r"\d{4}-\d{2}-\d{2}", args.observed_date)
    version = re.search(r"version = '([^']+)'", (ROOT / "Spectre-26.2/build.gradle").read_text())[1]
    artifacts = [ROOT / f"dist/spectre-{version}.jar", ROOT / f"dist/spectre-{version}-sources.jar"]
    checks = []

    with zipfile.ZipFile(artifacts[0]) as jar:
        names = jar.namelist()
        metadata = json.loads(jar.read("fabric.mod.json"))
        assert metadata["version"] == version
        assert metadata["authors"] == ["cody-raves"]
        assert metadata["entrypoints"]["client"] == ["net.spectre.client.SpectreClientMod"]
        checks.append("PASS: expected version, project author, and production-only client entrypoint")
        assert not any(any(part in n for part in ["/discord/", "/webhook/", "DiscordPresenceModule", "CommunityPopup"]) for n in names)
        checks.append("PASS: invite popup, Presence module, IPC connector, and webhook classes absent")
        assert not any(any(part in n for part in ["SmokeClient", "SmokeBrandingCapture", "SmokeBuilderCamera", "ShowcaseCapture", "DemoClient", "/fixtures/"]) for n in names)
        checks.append("PASS: opt-in smoke/demo initializers, capture mixins, and fixtures absent")
        for name in names:
            if name.endswith(".class"):
                content = jar.read(name).lower()
                assert b"discord.gg/" not in content and b"discordapp.com/api/webhooks/" not in content, name
        checks.append("PASS: no hardcoded Discord invite URL in top-level compiled classes")
        for module in ["PlayerDetectionModule", "SpawnerProtectModule"]:
            content = jar.read(f"net/spectre/client/module/impl/misc/{module}.class")
            assert b"Webhook" not in content and b"webhook" not in content and b"discord" not in content
        checks.append("PASS: detection/protection classes have no webhook references")
        provider = jar.read("net/spectre/client/module/BuiltinModuleProvider.class")
        assert b"DiscordPresenceModule" not in provider
        session = jar.read("net/spectre/client/account/GameSession.class")
        assert b"getAccessToken" in session and b"createFriendsService" in session
        checks.append("PASS: account-switcher token/friends-service symbols remain inspectable")
        assert "LICENSE_spectre" in names and "GONUTS-LICENSE.txt" in names
        checks.append("PASS: existing project and GoNuts license notices retained")

    catalog = json.loads((ROOT / "docs/modules.json").read_text(encoding="utf-8"))
    assert catalog["version"] == version
    assert len(catalog["modules"]) == 114
    assert not any(m["id"] == "discord-presence" for m in catalog["modules"])
    assert not any("webhook" in s["label"].lower() or "discord" in s["label"].lower() for m in catalog["modules"] for s in m["settings"])
    checks.append("PASS: generated reference has 114 modules and no removed controls")

    source = (ROOT / "Spectre-26.2/src/main/java/net/spectre/client/config/ShareDictionary.java").read_text(encoding="utf-8")
    def strings(name):
        block = re.search(name + r" = new String\[\]\{(.*?)\n   \};", source, re.S)[1]
        return [json.loads(x) for x in re.findall(r'"(?:\\.|[^"\\])*"', block)]
    dictionary = ("".join('"' + v + '":{' for v in strings("MODULES"))
                  + "".join('"' + v + '":' for v in strings("SETTINGS"))
                  + "".join(strings("FRAMES"))).encode("utf-8")
    assert hashlib.sha256(dictionary).hexdigest() == FROZEN_DICTIONARY_SHA256
    checks.append("PASS: existing shared-profile compression dictionary is unchanged")

    digests = []
    for artifact in artifacts:
        digests.append(f"{hashlib.sha256(artifact.read_bytes()).hexdigest()}  {artifact.name}")
    report = [f"Spectre {version} release packaging verification", f"Observed: {args.observed_date}", "", *checks, "", "SHA-256", *digests, "",
              "Scope: automated static packaging and metadata checks only.",
              "No authenticated runtime traffic capture or third-party/native-code audit.",
              "These checks are not proof that software is malware-free.",
              "See SECURITY-REPORT.md for behavior, source-review findings, and known local-storage weaknesses.", ""]
    (ROOT / "dist/RELEASE-VERIFICATION.txt").write_text("\n".join(report), encoding="utf-8")
    (ROOT / "dist/SHA256SUMS.txt").write_text("\n".join(digests) + "\n", encoding="utf-8")
    print("\n".join(report))


if __name__ == "__main__":
    main()
