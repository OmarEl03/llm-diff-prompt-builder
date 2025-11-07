# LLM Diff Prompt Builder

A tiny Swing app to load **Before/After** code files, generate a **unified diff**, and build a **deterministic LLM prompt**. Works with any language (auto-detects from file extension; you can override).

---

## 1) Requirements

* **JDK 17+** (tested with OpenJDK 21)
* A desktop environment (it’s a GUI app)

---

## 2) Quick Start

Here’s a cleaned **Quick Start** that uses `git clone` instead of “put files in a folder.” Replace your section with this:

---

## 2) Quick Start

Clone the repo and enter it:

```bash
git clone https://github.com/OmarEl03/llm-diff-prompt-builder.git
cd llm-diff-prompt-builder
```

Project structure:

```
.
├── After.py
├── Before.py
├── PromptBuilderApp.java
└── README.md
```

Build:

```bash
javac PromptBuilderApp.java
```

Run:

```bash
java PromptBuilderApp
```


---

## 3) How to Use

1. Click **Load Before…** and select your “before” file.
2. Click **Load After…** and select your “after” file.

   * Language will auto-fill from the file extension; you can edit the **Language** field manually.
3. Click **Generate Prompt**.

   * The bottom pane shows a full prompt including the `diff` block and the required JSON schema.
4. **Copy Prompt** copies to clipboard.
5. **Save Prompt…** writes the prompt to a file.


---

## 5) Project Notes

* Language detection is extension-based (e.g., `.py → Python`, `.cs → C#`, `.java → Java`, etc.).
* The diff header uses the **actual filenames** you loaded.
* The prompt is deterministic: same inputs → same prompt text.

Compile, run, load files, generate prompt.
