package com.demo.intent;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 意図推論ライブラリのデモ。
 *
 * <p>辞書・スロット定義・閾値をクラスパスリソース（{@code /intents.tsv}、
 * {@code /slots.tsv}、{@code /intent.properties}）から読み込むデータ駆動方式。
 *
 * <p>4つのテストクエリを分類して JSON 形式で出力し、PASS/FAIL を判定する。
 *
 * <h3>辞書ファイルの編集方法</h3>
 * <ul>
 *   <li>{@code src/main/resources/intents.tsv} — 意図例の追加・変更。
 *       書式: {@code <INTENT>\t<発話例>}。{@code #} 始まり行・空行は無視。</li>
 *   <li>{@code src/main/resources/slots.tsv} — スロット正規表現の追加・変更。
 *       書式: {@code <slotName>\t<正規表現>}。</li>
 *   <li>{@code src/main/resources/intent.properties} — 閾値等の調整。
 *       書式: {@code threshold=0.15}。</li>
 * </ul>
 */
public class Demo {

    public static void main(String[] args) throws IOException {
        // ===== データ駆動: クラスパスからリソースを読み込む =====
        IntentConfig config = IntentConfigLoader.loadFromClasspath(
            "/intents.tsv",
            "/slots.tsv",
            "/intent.properties"
        );

        System.out.println("====== Config ======");
        System.out.println("examples  : " + config.getExamples().size() + " 件");
        System.out.println("slots     : " + config.getSlotPatterns().keySet());
        System.out.println("threshold : " + config.getThreshold());
        System.out.println();

        // ===== 分類器構築（IntentConfig 経由） =====
        Tokenizer tokenizer = new CjkBigramTokenizer();
        IntentClassifier classifier = IntentClassifier.fromConfig(config, tokenizer);

        // ===== loadFromDir 動作確認 =====
        // src/main/resources をディレクトリとして読み直し、例数を確認する
        try {
            Path resourcesDir = Path.of("src/main/resources");
            IntentConfig cfgFromDir = IntentConfigLoader.loadFromDir(resourcesDir);
            System.out.println("====== loadFromDir 確認 ======");
            System.out.println("dir examples: " + cfgFromDir.getExamples().size() + " 件 (期待: "
                               + config.getExamples().size() + " 件)");
            System.out.println("dir slots   : " + cfgFromDir.getSlotPatterns().keySet());
            System.out.println("dir thresh  : " + cfgFromDir.getThreshold());
            System.out.println();
        } catch (IOException e) {
            System.out.println("[INFO] loadFromDir スキップ (cwd に resources がない): " + e.getMessage());
            System.out.println();
        }

        // ===== テストクエリ =====
        String[] queries = {
            "注文12345の配送状況を教えてください",   // 期待: ORDER_STATUS, orderId=12345
            "やっぱりキャンセルしたいです",           // 期待: CANCEL
            "この商品の在庫を確認したい",             // 期待: STOCK_CHECK
            "今日の天気はどうですか"                  // 期待: UNKNOWN
        };

        String[] expected = {
            "ORDER_STATUS (slot: orderId=12345)",
            "CANCEL",
            "STOCK_CHECK",
            "UNKNOWN"
        };

        System.out.println("====== Intent Detection Demo ======");
        System.out.println("threshold = " + config.getThreshold());
        System.out.println();

        boolean allPassed = true;
        for (int i = 0; i < queries.length; i++) {
            String query = queries[i];
            IntentResult result = classifier.classify(query);

            System.out.println("Query    : " + query);
            System.out.println("Expected : " + expected[i]);
            System.out.println("Result   :");
            System.out.println(result.toJson());
            System.out.println();

            boolean ok = checkResult(i, result);
            System.out.println("  => " + (ok ? "PASS" : "FAIL"));
            System.out.println("----------------------------------");
            if (!ok) allPassed = false;
        }

        System.out.println();
        System.out.println("====== Summary: " + (allPassed ? "ALL PASSED" : "SOME FAILED") + " ======");

        // 失敗があった場合は非ゼロで終了（CI向け）
        if (!allPassed) {
            System.exit(1);
        }
    }

    /**
     * テストケースごとの期待値チェック。
     */
    private static boolean checkResult(int caseIdx, IntentResult r) {
        return switch (caseIdx) {
            case 0 -> "ORDER_STATUS".equals(r.intent)
                   && "12345".equals(r.slots.get("orderId"));
            case 1 -> "CANCEL".equals(r.intent);
            case 2 -> "STOCK_CHECK".equals(r.intent);
            case 3 -> "UNKNOWN".equals(r.intent);
            default -> false;
        };
    }
}
