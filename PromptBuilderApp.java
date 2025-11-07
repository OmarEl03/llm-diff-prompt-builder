import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Dimension;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PromptBuilderApp {

    // Remember selected files so we can label the diff and auto-detect language
    private static Path beforePath = null;
    private static Path afterPath  = null;

    // Extension -> Language map (extend as needed)
    private static final Map<String,String> EXT_TO_LANG = new HashMap<>();
    static {
        EXT_TO_LANG.put("py",   "Python");
        EXT_TO_LANG.put("cs",   "C#");
        EXT_TO_LANG.put("java", "Java");
        EXT_TO_LANG.put("kt",   "Kotlin");
        EXT_TO_LANG.put("js",   "JavaScript");
        EXT_TO_LANG.put("mjs",  "JavaScript");
        EXT_TO_LANG.put("ts",   "TypeScript");
        EXT_TO_LANG.put("tsx",  "TypeScript");
        EXT_TO_LANG.put("jsx",  "JavaScript");
        EXT_TO_LANG.put("cpp",  "C++");
        EXT_TO_LANG.put("cc",   "C++");
        EXT_TO_LANG.put("cxx",  "C++");
        EXT_TO_LANG.put("h",    "C/C++ Header");
        EXT_TO_LANG.put("hpp",  "C++ Header");
        EXT_TO_LANG.put("c",    "C");
        EXT_TO_LANG.put("go",   "Go");
        EXT_TO_LANG.put("rs",   "Rust");
        EXT_TO_LANG.put("php",  "PHP");
        EXT_TO_LANG.put("rb",   "Ruby");
        EXT_TO_LANG.put("swift","Swift");
        EXT_TO_LANG.put("scala","Scala");
        EXT_TO_LANG.put("sql",  "SQL");
        EXT_TO_LANG.put("sh",   "Shell");
        EXT_TO_LANG.put("ps1",  "PowerShell");
        EXT_TO_LANG.put("lua",  "Lua");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(PromptBuilderApp::createAndShowUI);
    }

    // ================= GUI =================
    private static void createAndShowUI() {
        JFrame frame = new JFrame("LLM Diff Prompt Builder");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Controls
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField languageField = new JTextField("Auto", 8); // user can override; "Auto" = detect from file ext
        JTextField repoField = new JTextField("Internal-LLM-Inspection/Prototype", 28);
        JCheckBox readabilityFirst = new JCheckBox("Prefer readability", true);
        JCheckBox performanceHints = new JCheckBox("Surface perf hints", true);
        JCheckBox styleConsolidation = new JCheckBox("Suggest style consolidation", true);

        topBar.add(new JLabel("Language:"));
        topBar.add(languageField);
        topBar.add(new JLabel("Repo/Module:"));
        topBar.add(repoField);
        topBar.add(readabilityFirst);
        topBar.add(performanceHints);
        topBar.add(styleConsolidation);

        // Editors
        JTextArea beforeArea = new JTextArea();
        JTextArea afterArea  = new JTextArea();
        beforeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        afterArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        beforeArea.setText(sampleBefore());
        afterArea.setText(sampleAfter());

        JScrollPane beforeScroll = new JScrollPane(beforeArea);
        JScrollPane afterScroll  = new JScrollPane(afterArea);

        JSplitPane editors = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                wrapWithTitled(beforeScroll, "Before (original)"),
                wrapWithTitled(afterScroll,  "After (edited)")
        );
        editors.setResizeWeight(0.5);
        editors.setMinimumSize(new Dimension(200, 200));

        // Output
        JTextArea promptPreview = new JTextArea();
        promptPreview.setEditable(false);
        promptPreview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane promptScroll = new JScrollPane(promptPreview);

        JButton buildBtn = new JButton(new AbstractAction("Generate Prompt") {
            @Override public void actionPerformed(ActionEvent e) {
                String before = beforeArea.getText();
                String after  = afterArea.getText();

                String beforeName = (beforePath != null) ? beforePath.getFileName().toString() : "Before.txt";
                String afterName  = (afterPath  != null) ? afterPath.getFileName().toString()  : "After.txt";

                String lang = languageField.getText().trim();
                if (lang.isEmpty() || lang.equalsIgnoreCase("Auto")) {
                    String g1 = (beforePath != null) ? guessLanguageFromFilename(beforePath.getFileName().toString()) : null;
                    String g2 = (afterPath  != null) ? guessLanguageFromFilename(afterPath.getFileName().toString())  : null;
                    lang = (g2 != null) ? g2 : (g1 != null ? g1 : "Generic");
                }

                String diff = unifiedDiff(beforeName, afterName, before, after, 3);
                String prompt = promptTemplate(
                        lang,
                        repoField.getText().trim(),
                        diff,
                        readabilityFirst.isSelected(),
                        performanceHints.isSelected(),
                        styleConsolidation.isSelected()
                );
                promptPreview.setText(prompt);
            }
        });

        JButton copyBtn = new JButton(new AbstractAction("Copy Prompt") {
            @Override public void actionPerformed(ActionEvent e) {
                String text = promptPreview.getText();
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(text), null);
                JOptionPane.showMessageDialog(frame, "Prompt copied to clipboard.");
            }
        });

        JButton loadBeforeBtn = new JButton("Load Before…");
        loadBeforeBtn.addActionListener(ev -> {
            Path p = chooseFile(frame);
            if (p != null) {
                beforePath = p;
                beforeArea.setText(tryRead(p, frame));
                // Auto language (only if Field is Auto/blank)
                String current = languageField.getText().trim();
                if (current.isEmpty() || current.equalsIgnoreCase("Auto")) {
                    String g = guessLanguageFromFilename(p.getFileName().toString());
                    if (g != null) languageField.setText(g);
                }
            }
        });

        JButton loadAfterBtn = new JButton("Load After…");
        loadAfterBtn.addActionListener(ev -> {
            Path p = chooseFile(frame);
            if (p != null) {
                afterPath = p;
                afterArea.setText(tryRead(p, frame));
                String current = languageField.getText().trim();
                if (current.isEmpty() || current.equalsIgnoreCase("Auto")) {
                    String g = guessLanguageFromFilename(p.getFileName().toString());
                    if (g != null) languageField.setText(g);
                }
            }
        });

        JButton savePromptBtn = new JButton("Save Prompt…");
        savePromptBtn.addActionListener(ev -> {
            JFileChooser fc = new JFileChooser();
            int r = fc.showSaveDialog(frame);
            if (r == JFileChooser.APPROVE_OPTION) {
                tryWrite(fc.getSelectedFile().toPath(), promptPreview.getText(), frame);
                JOptionPane.showMessageDialog(frame, "Saved.");
            }
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(loadBeforeBtn);
        actions.add(loadAfterBtn);
        actions.add(buildBtn);
        actions.add(copyBtn);
        actions.add(savePromptBtn);

        // Prompt panel (resizable, bottom half)
        JPanel promptPanel = new JPanel(new BorderLayout(8, 8));
        promptPanel.add(actions, BorderLayout.NORTH);
        promptPanel.add(wrapWithTitled(promptScroll, "Prompt Preview (send this to the LLM)"), BorderLayout.CENTER);
        promptPanel.setMinimumSize(new Dimension(200, 200));

        // Main vertical split: editors (top) ⟂ prompt panel (bottom)
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editors, promptPanel);
        mainSplit.setResizeWeight(0.5);
        mainSplit.setDividerLocation(0.5);

        root.add(topBar, BorderLayout.NORTH);
        root.add(mainSplit, BorderLayout.CENTER);

        frame.setContentPane(root);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        SwingUtilities.invokeLater(() -> mainSplit.setDividerLocation(0.5));
    }

    private static JComponent wrapWithTitled(JComponent c, String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    // =============== Diff generator (line-based LCS) ===============
    public static String unifiedDiff(String oldName, String newName, String oldText, String newText, int contextLines) {
        java.util.List<String> a = Arrays.asList(oldText.split("\n", -1));
        java.util.List<String> b = Arrays.asList(newText.split("\n", -1));

        int n = a.size(), m = b.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (Objects.equals(a.get(i), b.get(j))) dp[i][j] = dp[i + 1][j + 1] + 1;
                else dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }

        int i = 0, j = 0;
        java.util.List<Edit> edits = new java.util.ArrayList<>();
        while (i < n && j < m) {
            if (Objects.equals(a.get(i), b.get(j))) { edits.add(new Edit(' ', a.get(i))); i++; j++; }
            else if (dp[i + 1][j] >= dp[i][j + 1]) { edits.add(new Edit('-', a.get(i++))); }
            else { edits.add(new Edit('+', b.get(j++))); }
        }
        while (i < n) edits.add(new Edit('-', a.get(i++)));
        while (j < m) edits.add(new Edit('+', b.get(j++)));

        StringBuilder out = new StringBuilder();
        out.append("--- ").append(oldName).append("\n");
        out.append("+++ ").append(newName).append("\n");

        int idx = 0;
        while (idx < edits.size()) {
            while (idx < edits.size() && edits.get(idx).tag == ' ') idx++;
            if (idx >= edits.size()) break;

            int hunkStart = Math.max(0, idx - contextLines);
            int end = hunkStart;
            boolean changeSeen = false;
            int runSpaces = 0;

            for (int t = hunkStart; t < edits.size(); t++) {
                if (edits.get(t).tag == ' ') runSpaces++;
                else { changeSeen = true; runSpaces = 0; }
                end = t + 1;
                if (changeSeen && runSpaces > contextLines) {
                    end = t + 1 - runSpaces;
                    break;
                }
            }

            int aLine = 1, bLine = 1;
            for (int t = 0; t < hunkStart; t++) {
                if (edits.get(t).tag != '+') aLine++;
                if (edits.get(t).tag != '-') bLine++;
            }
            int aCount = 0, bCount = 0;
            for (int t = hunkStart; t < end; t++) {
                if (edits.get(t).tag != '+') aCount++;
                if (edits.get(t).tag != '-') bCount++;
            }

            out.append(String.format("@@ -%d,%d +%d,%d @@\n", aLine, aCount, bLine, bCount));
            for (int t = hunkStart; t < end; t++) {
                out.append(edits.get(t).tag).append(edits.get(t).line).append("\n");
            }
            idx = end;
        }
        return out.toString();
    }

    private static class Edit {
        final char tag; // ' ' common, '-' removed, '+' added
        final String line;
        Edit(char tag, String line) { this.tag = tag; this.line = line; }
    }

    // =============== Prompt template ===============
    public static String promptTemplate(String language,
                                        String repository,
                                        String unifiedDiff,
                                        boolean readabilityFirst,
                                        boolean performanceHints,
                                        boolean styleConsolidation) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a strictly deterministic static analysis assistant for ").append(language).append(" code.\n")
          .append("Your job: review the following code changes and produce actionable, line-anchored improvement suggestions.\n\n")
          .append("Context:\n")
          .append("- Repository/Module: ").append(repository).append("\n")
          .append("- Non-goals: do NOT invent behavior; base all comments on diff only.\n")
          .append("- Determinism: output MUST be stable for identical input.\n\n")
          .append("Inspection objectives (current run):\n")
          .append("  [").append(readabilityFirst ? "x" : " ").append("] Prefer readability when trade-offs are ambiguous.\n")
          .append("  [").append(performanceHints ? "x" : " ").append("] Surface potential performance pitfalls and micro-allocations.\n")
          .append("  [").append(styleConsolidation ? "x" : " ").append("] Suggest style consolidation (DRY, consistent naming, idioms).\n\n")
          .append("Unified diff to analyze (use as ground truth):\n")
          .append("```diff\n").append(unifiedDiff).append("```\n\n")
          .append("Required output (JSON, deterministic ordering by file > hunk start > rule):\n")
          .append("{\n")
          .append("  \"summary\": \"<1-2 sentences on overall risk/readability/style>\",\n")
          .append("  \"findings\": [\n")
          .append("    {\n")
          .append("      \"file\": \"<path or before/after filename>\",\n")
          .append("      \"line\": <line_number_in_new_file_or_null>,\n")
          .append("      \"rule\": \"<readability|complexity|perf|style|bug-risk>\",\n")
          .append("      \"severity\": \"info|warning|error\",\n")
          .append("      \"explanation\": \"<why this matters>\",\n")
          .append("      \"suggestion\": \"<specific edit or pattern to apply>\"\n")
          .append("    }\n")
          .append("  ],\n")
          .append("  \"metrics\": {\n")
          .append("    \"estimated_cyclomatic_delta\": <int>,\n")
          .append("    \"allocations_delta\": \"<low|medium|high|unknown>\",\n")
          .append("    \"readability\": \"<improved|regressed|unchanged>\"\n")
          .append("  }\n")
          .append("}\n");
        return sb.toString();
    }

    // =============== Helpers ===============
    private static Path chooseFile(java.awt.Component parent) {
        JFileChooser fc = new JFileChooser();
        int r = fc.showOpenDialog(parent);
        return (r == JFileChooser.APPROVE_OPTION) ? fc.getSelectedFile().toPath() : null;
    }

    private static void showError(java.awt.Component parent, String msg, Exception e) {
        JOptionPane.showMessageDialog(parent, msg + "\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static String tryRead(Path p, java.awt.Component parent) {
        try { return Files.readString(p); }
        catch (Exception e) { showError(parent, "Failed to read: " + p, e); return ""; }
    }

    private static void tryWrite(Path p, String content, java.awt.Component parent) {
        try { Files.writeString(p, content); }
        catch (Exception e) { showError(parent, "Failed to write: " + p, e); }
    }

    private static String guessLanguageFromFilename(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return null;
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return EXT_TO_LANG.getOrDefault(ext, null);
    }

    // =============== Initial samples (harmless defaults) ===============
    private static String sampleBefore() {
        return String.join("\n",
            "Hello..."
        );
    }

    private static String sampleAfter() {
        return String.join("\n",
            "Hello..."
        );
    }
}
