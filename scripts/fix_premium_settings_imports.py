from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/se/familjekalender/app"


def ensure_import(text: str, import_line: str) -> str:
    line = f"import {import_line}\n"
    if line in text:
        return text
    package_end = text.index("\n\n") + 2
    return text[:package_end] + line + text[package_end:]

mail_path = src / "MailSettingsCard.kt"
mail = mail_path.read_text(encoding="utf-8")
for imp in [
    "androidx.compose.material3.MaterialTheme",
    "androidx.compose.foundation.layout.weight",
]:
    mail = ensure_import(mail, imp)
mail_path.write_text(mail, encoding="utf-8")

update_path = src / "AppUpdate.kt"
update = update_path.read_text(encoding="utf-8")
for imp in [
    "androidx.compose.foundation.layout.Row",
    "androidx.compose.foundation.layout.weight",
    "androidx.compose.ui.Alignment",
    "androidx.compose.ui.unit.sp",
]:
    update = ensure_import(update, imp)
update_path.write_text(update, encoding="utf-8")

print("Premium settings imports fixed")
