const MAX_EXTRACTED_CHARS = 120_000;

async function officeXml(file: File, mode: "slides" | "odf" | "epub") {
  const JSZip = (await import("jszip")).default;
  const zip = await JSZip.loadAsync(await file.arrayBuffer());
  const paths = Object.keys(zip.files).filter((path) => mode === "slides"
    ? /^ppt\/slides\/slide\d+\.xml$/i.test(path)
    : mode === "odf" ? path === "content.xml" : /\.(xhtml|html|htm)$/i.test(path));
  paths.sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
  const lines: string[] = [];
  for (const path of paths) {
    const xml = await zip.file(path)?.async("string");
    if (!xml) continue;
    const doc = new DOMParser().parseFromString(xml, "application/xml");
    if (doc.querySelector("parsererror")) continue;
    const tags = mode === "slides" ? ["a:t"] : ["text:p", "text:h", "p"];
    for (const tag of tags) {
      for (const node of Array.from(doc.getElementsByTagName(tag))) {
        const text = node.textContent?.trim();
        if (text) lines.push(text);
      }
    }
    if (lines.join("\n").length >= MAX_EXTRACTED_CHARS) break;
  }
  return lines.join("\n");
}

async function workbook(file: File) {
  const JSZip = (await import("jszip")).default;
  const zip = await JSZip.loadAsync(await file.arrayBuffer());
  const sharedDoc = zip.file("xl/sharedStrings.xml")
    ? new DOMParser().parseFromString(await zip.file("xl/sharedStrings.xml")!.async("string"), "application/xml")
    : null;
  const shared = sharedDoc ? Array.from(sharedDoc.getElementsByTagName("si"), (item) => Array.from(item.getElementsByTagName("t"), (part) => part.textContent ?? "").join("")) : [];
  const sheets = Object.keys(zip.files).filter((path) => /^xl\/worksheets\/sheet\d+\.xml$/i.test(path)).sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
  const lines: string[] = [];
  for (const path of sheets) {
    const xml = await zip.file(path)?.async("string");
    if (!xml) continue;
    const doc = new DOMParser().parseFromString(xml, "application/xml");
    for (const row of Array.from(doc.getElementsByTagName("row"))) {
      const cells = Array.from(row.getElementsByTagName("c"), (cell) => {
        const value = cell.getElementsByTagName("v")[0]?.textContent ?? "";
        if (cell.getAttribute("t") === "s") return shared[Number(value)] ?? value;
        if (cell.getAttribute("t") === "inlineStr") return Array.from(cell.getElementsByTagName("t"), (part) => part.textContent ?? "").join("");
        return value;
      });
      if (cells.some(Boolean)) lines.push(cells.join(" | "));
    }
    if (lines.join("\n").length >= MAX_EXTRACTED_CHARS) break;
  }
  return lines.join("\n");
}

export async function extractStudyFile(file: File): Promise<string> {
  const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
  if (ext === "pdf" || file.type === "application/pdf") {
    const [pdf, worker] = await Promise.all([
      import("pdfjs-dist/legacy/build/pdf.mjs"),
      import("pdfjs-dist/legacy/build/pdf.worker.min.mjs?url"),
    ]);
    pdf.GlobalWorkerOptions.workerSrc = worker.default;
    const doc = await pdf.getDocument({ data: new Uint8Array(await file.arrayBuffer()) }).promise;
    const pages: string[] = [];
    for (let pageNumber = 1; pageNumber <= Math.min(doc.numPages, 300); pageNumber++) {
      const page = await doc.getPage(pageNumber);
      const content = await page.getTextContent();
      pages.push(content.items.map((item) => "str" in item ? item.str : "").join(" "));
      if (pages.join("\n").length >= MAX_EXTRACTED_CHARS) break;
    }
    return pages.join("\n");
  }
  if (ext === "docx") {
    const imported = await import("mammoth/mammoth.browser.js");
    const mammoth = (imported.default ?? imported) as { convertToHtml: (input: { arrayBuffer: ArrayBuffer }) => Promise<{ value: string }> };
    const converted = await mammoth.convertToHtml({ arrayBuffer: await file.arrayBuffer() });
    const container = document.createElement("div");
    container.innerHTML = converted.value;
    return container.innerText || container.textContent || "";
  }
  if (ext === "pptx") return officeXml(file, "slides");
  if (ext === "xlsx") return workbook(file);
  if (["odt", "ods", "odp"].includes(ext)) return officeXml(file, "odf");
  if (ext === "epub") return officeXml(file, "epub");

  const text = await file.text();
  if (ext === "rtf") return text.replace(/\\'[0-9a-f]{2}/gi, " ").replace(/\\[a-z]+-?\d* ?/gi, " ").replace(/[{}]/g, " ").replace(/\s+/g, " ");
  const binary = (text.match(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\ufffd]/g) ?? []).length;
  if (binary > Math.max(2, text.length * 0.01)) {
    throw new Error(`“${file.name}” is a binary format Playbit cannot extract yet. Export it as PDF, DOCX, PPTX, XLSX, ODT, ODS, ODP, EPUB, or text and retry.`);
  }
  if (["html", "htm", "xml"].includes(ext)) {
    const container = document.createElement("div");
    container.innerHTML = text;
    return container.innerText || container.textContent || "";
  }
  return text;
}
