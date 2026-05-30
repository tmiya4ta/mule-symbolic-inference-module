package com.demo.intent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * TSV/properties ファイルから {@link IntentConfig} を構築するローダー。
 *
 * <h3>ファイル形式</h3>
 * <ul>
 *   <li><b>intents.tsv</b>: {@code <INTENT>\t<発話例>}。空行と {@code #} 始まり行は無視。</li>
 *   <li><b>slots.tsv</b>: {@code <slotName>\t<正規表現>}。同様に無視行あり。</li>
 *   <li><b>intent.properties</b>: {@code threshold=0.15} 等 (java.util.Properties 形式)。</li>
 * </ul>
 *
 * <h3>利用パターン</h3>
 * <pre>{@code
 * // クラスパスから読む（Mule/JAR 運用向け）
 * IntentConfig cfg = IntentConfigLoader.loadFromClasspath(
 *     "/intents.tsv", "/slots.tsv", "/intent.properties");
 *
 * // ディレクトリから読む（外部ファイル運用向け）
 * IntentConfig cfg = IntentConfigLoader.loadFromDir(Path.of("/etc/intent"));
 * }</pre>
 */
public class IntentConfigLoader {

    /** デフォルト閾値（intent.properties が存在しない/読めない場合） */
    private static final double DEFAULT_THRESHOLD = 0.15;

    private IntentConfigLoader() {} // ユーティリティクラス

    // ============================================================
    // intents.tsv パース
    // ============================================================

    /**
     * intents.tsv 形式のストリームから {@link IntentExample} リストを構築する。
     *
     * <p>UTF-8 で読む。空行・{@code #} 始まり行・タブが1つ未満の行はスキップする。
     *
     * @param is InputStream (呼び出し元がクローズすること)
     * @return IntentExample リスト（読み込み順）
     * @throws IOException 読み込み失敗時
     */
    public static List<IntentExample> loadExamples(InputStream is) throws IOException {
        return loadExamples(new InputStreamReader(is, StandardCharsets.UTF_8));
    }

    /**
     * intents.tsv 形式のリーダから {@link IntentExample} リストを構築する。
     *
     * @param reader Reader (呼び出し元がクローズすること)
     * @return IntentExample リスト（読み込み順）
     * @throws IOException 読み込み失敗時
     */
    public static List<IntentExample> loadExamples(Reader reader) throws IOException {
        List<IntentExample> result = new ArrayList<>();
        BufferedReader br = (reader instanceof BufferedReader)
            ? (BufferedReader) reader
            : new BufferedReader(reader);

        String line;
        while ((line = br.readLine()) != null) {
            String trimmed = line.trim();
            // 空行・コメント行をスキップ
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int tab = trimmed.indexOf('\t');
            if (tab < 0) {
                // タブが見つからない行はスキップ
                continue;
            }
            String intent = trimmed.substring(0, tab).trim();
            String text   = trimmed.substring(tab + 1).trim();
            if (intent.isEmpty() || text.isEmpty()) {
                continue;
            }
            result.add(new IntentExample(intent, text));
        }
        return result;
    }

    // ============================================================
    // slots.tsv パース
    // ============================================================

    /**
     * slots.tsv 形式のストリームからスロットパターンマップを構築する。
     *
     * @param is InputStream (呼び出し元がクローズすること)
     * @return スロット名 → {@link Pattern} の LinkedHashMap（読み込み順）
     * @throws IOException 読み込み失敗時
     */
    public static Map<String, Pattern> loadSlots(InputStream is) throws IOException {
        return loadSlots(new InputStreamReader(is, StandardCharsets.UTF_8));
    }

    /**
     * slots.tsv 形式のリーダからスロットパターンマップを構築する。
     *
     * @param reader Reader (呼び出し元がクローズすること)
     * @return スロット名 → {@link Pattern} の LinkedHashMap（読み込み順）
     * @throws IOException 読み込み失敗時
     */
    public static Map<String, Pattern> loadSlots(Reader reader) throws IOException {
        Map<String, Pattern> result = new LinkedHashMap<>();
        BufferedReader br = (reader instanceof BufferedReader)
            ? (BufferedReader) reader
            : new BufferedReader(reader);

        String line;
        while ((line = br.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int tab = trimmed.indexOf('\t');
            if (tab < 0) {
                continue;
            }
            String slotName = trimmed.substring(0, tab).trim();
            String regex    = trimmed.substring(tab + 1).trim();
            if (slotName.isEmpty() || regex.isEmpty()) {
                continue;
            }
            result.put(slotName, Pattern.compile(regex));
        }
        return result;
    }

    // ============================================================
    // synonyms.tsv パース
    // ============================================================

    /** synonyms.tsv 形式のストリームから variant→canonical マップを構築する。 */
    public static Map<String, String> loadSynonyms(InputStream is) throws IOException {
        return loadSynonyms(new InputStreamReader(is, StandardCharsets.UTF_8));
    }

    /**
     * synonyms.tsv 形式のリーダから variant→canonical マップを構築する。
     * 書式: {@code <canonical>\t<variant1>,<variant2>,...}（空行・# 始まり行は無視）。
     */
    public static Map<String, String> loadSynonyms(Reader reader) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        BufferedReader br = (reader instanceof BufferedReader)
            ? (BufferedReader) reader
            : new BufferedReader(reader);
        String line;
        while ((line = br.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int tab = trimmed.indexOf('\t');
            if (tab < 0) {
                continue;
            }
            String canonical = trimmed.substring(0, tab).trim();
            String variants  = trimmed.substring(tab + 1).trim();
            if (canonical.isEmpty() || variants.isEmpty()) {
                continue;
            }
            for (String v : variants.split(",")) {
                String vv = v.trim();
                if (!vv.isEmpty()) {
                    result.put(vv, canonical);
                }
            }
        }
        return result;
    }

    // ============================================================
    // 統合ロード
    // ============================================================

    /**
     * intents/slots/props の3ストリームをまとめて読んで {@link IntentConfig} を返す。
     *
     * @param intentsStream intents.tsv の InputStream (null 不可)
     * @param slotsStream   slots.tsv の InputStream (null 不可)
     * @param propsStream   intent.properties の InputStream (null なら DEFAULT_THRESHOLD を使用)
     * @return IntentConfig
     * @throws IOException 読み込み失敗時
     */
    public static IntentConfig load(InputStream intentsStream,
                                    InputStream slotsStream,
                                    InputStream propsStream) throws IOException {
        return load(intentsStream, slotsStream, propsStream, null);
    }

    /**
     * intents/slots/props/synonyms の4ストリームをまとめて読んで {@link IntentConfig} を返す。
     *
     * @param synonymsStream synonyms.tsv の InputStream (null なら正規化なし)
     */
    public static IntentConfig load(InputStream intentsStream,
                                    InputStream slotsStream,
                                    InputStream propsStream,
                                    InputStream synonymsStream) throws IOException {
        List<IntentExample> examples;
        Map<String, Pattern> slotPatterns;
        double threshold;
        Map<String, String> synonyms;

        try (InputStream is = intentsStream) {
            examples = loadExamples(is);
        }
        try (InputStream is = slotsStream) {
            slotPatterns = loadSlots(is);
        }

        if (propsStream != null) {
            Properties props = new Properties();
            try (InputStream is = propsStream) {
                props.load(is);
            }
            String tStr = props.getProperty("threshold");
            threshold = (tStr != null) ? Double.parseDouble(tStr.trim()) : DEFAULT_THRESHOLD;
        } else {
            threshold = DEFAULT_THRESHOLD;
        }

        if (synonymsStream != null) {
            try (InputStream is = synonymsStream) {
                synonyms = loadSynonyms(is);
            }
        } else {
            synonyms = Map.of();
        }

        return new IntentConfig(examples, slotPatterns, threshold, synonyms);
    }

    // ============================================================
    // クラスパスから読み込む
    // ============================================================

    /**
     * クラスパスからリソースを読んで {@link IntentConfig} を返す（Mule/JAR 運用向け）。
     *
     * <p>リソースパスはスラッシュ始まりを推奨（例: {@code "/intents.tsv"}）。
     *
     * @param intentsRes intents.tsv のクラスパスリソースパス
     * @param slotsRes   slots.tsv のクラスパスリソースパス
     * @param propsRes   intent.properties のクラスパスリソースパス (null可: デフォルト閾値を使用)
     * @return IntentConfig
     * @throws IOException リソースが見つからない/読めない場合
     */
    public static IntentConfig loadFromClasspath(String intentsRes,
                                                  String slotsRes,
                                                  String propsRes) throws IOException {
        InputStream intentsStream = IntentConfigLoader.class.getResourceAsStream(intentsRes);
        if (intentsStream == null) {
            throw new IOException("クラスパスリソースが見つかりません: " + intentsRes);
        }
        InputStream slotsStream = IntentConfigLoader.class.getResourceAsStream(slotsRes);
        if (slotsStream == null) {
            intentsStream.close();
            throw new IOException("クラスパスリソースが見つかりません: " + slotsRes);
        }
        InputStream propsStream = (propsRes != null)
            ? IntentConfigLoader.class.getResourceAsStream(propsRes)
            : null;

        // intents.tsv と同じディレクトリの synonyms.tsv を任意で読む
        String synRes = intentsRes.contains("/")
            ? intentsRes.substring(0, intentsRes.lastIndexOf('/') + 1) + "synonyms.tsv"
            : "synonyms.tsv";
        InputStream synStream = IntentConfigLoader.class.getResourceAsStream(synRes);

        return load(intentsStream, slotsStream, propsStream, synStream);
    }

    // ============================================================
    // ディレクトリから読み込む
    // ============================================================

    /**
     * 指定ディレクトリから {@code intents.tsv}、{@code slots.tsv}、
     * {@code intent.properties} を読んで {@link IntentConfig} を返す（外部ファイル運用向け）。
     *
     * <p>{@code intent.properties} が存在しない場合はデフォルト閾値を使用する。
     *
     * @param dir ディレクトリパス
     * @return IntentConfig
     * @throws IOException ファイルが読めない場合
     */
    public static IntentConfig loadFromDir(Path dir) throws IOException {
        Path intentsPath = dir.resolve("intents.tsv");
        Path slotsPath   = dir.resolve("slots.tsv");
        Path propsPath   = dir.resolve("intent.properties");

        InputStream intentsStream = Files.newInputStream(intentsPath);
        InputStream slotsStream   = Files.newInputStream(slotsPath);
        InputStream propsStream   = Files.exists(propsPath)
            ? Files.newInputStream(propsPath)
            : null;
        // synonyms.tsv が存在すれば任意で読む（同義語正規化レイヤ）
        Path synPath = dir.resolve("synonyms.tsv");
        InputStream synStream = Files.exists(synPath)
            ? Files.newInputStream(synPath)
            : null;

        return load(intentsStream, slotsStream, propsStream, synStream);
    }
}
