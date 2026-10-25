"""Generate the public module reference from the registered Java modules.

Run with Python 3. No Minecraft runtime or third-party packages are needed.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "Spectre-26.2/src/main/java"
MODULE = JAVA / "net/spectre/client/module"


def arguments(text, opening):
    depth, quoted, escaped, start, result = 0, False, False, opening + 1, []
    for i in range(opening, len(text)):
        c = text[i]
        if quoted:
            if escaped:
                escaped = False
            elif c == "\\":
                escaped = True
            elif c == '"':
                quoted = False
            continue
        if c == '"':
            quoted = True
        elif c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
            if depth == 0:
                result.append(text[start:i].strip())
                return result
        elif c == "," and depth == 1:
            result.append(text[start:i].strip())
            start = i + 1
    raise ValueError("Unclosed Java expression")


def literal(value, constants=None):
    value = value.strip()
    if re.fullmatch(r'"(?:[^"\\]|\\.)*"', value):
        return json.loads(value)
    if constants and value in constants:
        return constants[value]
    return None


def main():
    provider = (MODULE / "BuiltinModuleProvider.java").read_text(encoding="utf-8")
    imports = dict((s.rsplit(".", 1)[-1], s) for s in re.findall(r"import ([\w.]+);", provider))
    registrations = re.findall(r"new\s+([\w.]+)\(\)", provider.split("return List.of(", 1)[1])
    categories = dict(re.findall(r'register\("([A-Z_]+)",\s*"([^"]+)"', (MODULE / "ModuleCategory.java").read_text(encoding="utf-8")))
    output = []
    for cls in registrations:
        full = imports.get(cls, cls)
        path = JAVA / (full.replace(".", "/") + ".java")
        text = path.read_text(encoding="utf-8")
        constants = dict(re.findall(r'static final String\s+(\w+)\s*=\s*"([^"]*)"', text))
        constructors = [arguments(text, m.end() - 1) for m in re.finditer(r"\bsuper\s*\(", text)]
        args = next((a for a in constructors if len(a) in (3, 4) and re.fullmatch(r"ModuleCategory\.\w+", a[-1])), None)
        if args is None:
            inherited = next((a for a in constructors if len(a) == 3 and all(literal(v, constants) is not None for v in a)), None)
            parent = re.search(r"class\s+\w+\s+extends\s+(\w+)", text)
            if inherited and parent:
                parent_path = path.with_name(parent[1] + ".java")
                if parent_path.exists():
                    parent_source = parent_path.read_text(encoding="utf-8")
                    parent_category = re.search(r"super\([^;]+ModuleCategory\.(\w+)\s*\)", parent_source, re.S)
                    if parent_category:
                        args = inherited + ["ModuleCategory." + parent_category[1]]
        if args is None:
            raise ValueError(f"Unresolved constructor for {cls}: {constructors}")
        if len(args) == 4:
            module_id, name, description = [literal(a, constants) for a in args[:3]]
        else:
            name, description = [literal(a, constants) for a in args[:2]]
            module_id = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
        if not all((module_id, name, description)):
            raise ValueError(f"Unresolved module metadata: {path}")
        settings, labels = [], set()
        for m in re.finditer(r"new\s+(?:[\w]+\.)*(\w+Setting)\s*\(", text):
            values = arguments(text, m.end() - 1)
            label = literal(values[0], constants)
            if not label or label in labels:
                continue
            labels.add(label)
            kind = m.group(1).removesuffix("Setting")
            setting = {"label": label, "type": kind}
            if len(values) > 1:
                default = literal(values[1], constants)
                if default is not None and kind in ("Mode", "String"):
                    setting["default"] = default
                elif kind == "Boolean" and values[1] in ("true", "false"):
                    setting["default"] = "On" if values[1] == "true" else "Off"
                elif kind == "Number" and re.fullmatch(r"-?\d*\.?\d+[fFdD]?", values[1]):
                    setting["default"] = values[1].rstrip("fFdD")
                    if len(values) >= 4:
                        setting["range"] = " to ".join(v.rstrip("fFdD") for v in values[2:4])
            if kind == "Mode":
                setting["options"] = list(dict.fromkeys(v for a in values[2:] if (v := literal(a, constants)) is not None))
            settings.append(setting)
        binding = re.search(r"(?:this\.)?setKeybind\((\d+)\)", text)
        key = None
        if binding:
            code = int(binding[1])
            key = chr(code) if 32 <= code <= 126 else {340: "Left Shift", 344: "Right Shift"}.get(code, str(code))
        action = bool(re.search(r"boolean\s+isAction\s*\(\s*\)\s*\{\s*return true", text))
        output.append({"id": module_id, "name": name, "description": description,
                       "category": categories[args[-1].split(".")[-1]], "action": action,
                       "hold": bool(re.search(r"setHoldMode\(true\)", text)), "key": key,
                       "settings": settings, "source": path.relative_to(ROOT).as_posix()})
    if len(output) != len(registrations) or len({m["id"] for m in output}) != len(output):
        raise ValueError("Module reference is incomplete or contains duplicate IDs")
    destination = ROOT / "docs/modules.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps({"version": "0.9.0+mc26.2", "modules": output}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Documented {len(output)} registered modules and {sum(len(m['settings']) for m in output)} module-specific controls")


if __name__ == "__main__":
    main()
