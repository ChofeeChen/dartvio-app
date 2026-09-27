#!/usr/bin/env python3
"""把 PRD 的 .docx 批量转成 Markdown（一次性迁移工具，跑完即可删除）。

用法：
    python tools/docx2md.py <docx目录> <输出目录>

只做结构转换（标题 / 段落 / 列表 / 表格 / 粗体），不做内容改写：
转换结果需要人工（或 AI）再按 App 现状校对。
"""
import re
import sys
from pathlib import Path

from docx import Document
from docx.table import Table
from docx.text.paragraph import Paragraph


def iter_block_items(parent):
    """按文档实际顺序产出段落与表格（python-docx 默认把两者分开迭代）。"""
    from docx.oxml.ns import qn

    body = parent.element.body
    for child in body.iterchildren():
        if child.tag == qn("w:p"):
            yield Paragraph(child, parent)
        elif child.tag == qn("w:tbl"):
            yield Table(child, parent)


def runs_to_md(paragraph: Paragraph) -> str:
    out = []
    for run in paragraph.runs:
        text = run.text.replace("|", "\\|")
        if not text:
            continue
        if run.bold:
            text = f"**{text}**"
        if run.italic:
            text = f"*{text}*"
        out.append(text)
    text = "".join(out)
    return re.sub(r"[ \t]+", " ", text).strip()


def is_list(paragraph: Paragraph) -> bool:
    style = (paragraph.style.name or "").lower()
    if "list" in style:
        return True
    pPr = paragraph._p.find(
        "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}pPr"
    )
    return pPr is not None and pPr.find(
        "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}numPr"
    ) is not None


def heading_level(paragraph: Paragraph):
    style = paragraph.style.name or ""
    m = re.match(r"^(Heading|标题)\s*(\d+)$", style.strip(), re.IGNORECASE)
    return int(m.group(2)) if m else None


def table_to_md(table: Table) -> str:
    rows = []
    for row in table.rows:
        rows.append([c.text.strip().replace("|", "\\|").replace("\n", " ") for c in row.cells])
    if not rows:
        return ""
    width = max(len(r) for r in rows)
    head = rows[0] + [""] * (width - len(rows[0]))
    lines = ["| " + " | ".join(head) + " |", "| " + " | ".join(["---"] * width) + " |"]
    for r in rows[1:]:
        lines.append("| " + " | ".join(r + [""] * (width - len(r))) + " |")
    return "\n".join(lines)


def convert(docx_path: Path) -> str:
    doc = Document(str(docx_path))
    md = []
    numbered = 0
    for block in iter_block_items(doc):
        if isinstance(block, Table):
            md.append(table_to_md(block))
            md.append("")
            continue
        para = block
        level = heading_level(para)
        text = runs_to_md(para)
        if not text:
            continue
        if level:
            md.append("#" * min(level, 6) + " " + text)
            md.append("")
        elif is_list(para):
            numbered += 1
            md.append(f"- {text}")
        else:
            numbered = 0
            md.append(text)
            md.append("")
    return "\n".join(md).strip() + "\n"


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except AttributeError:
        pass
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    src = Path(sys.argv[1])
    dst = Path(sys.argv[2])
    dst.mkdir(parents=True, exist_ok=True)
    for docx in sorted(src.glob("*.docx")):
        if docx.name.startswith("~$"):
            continue
        out = dst / (docx.stem + ".md")
        out.write_text(convert(docx), encoding="utf-8")
        print(f"{docx.name} -> {out} ({out.stat().st_size / 1024:.1f} KB)")
    print("done")


if __name__ == "__main__":
    main()
