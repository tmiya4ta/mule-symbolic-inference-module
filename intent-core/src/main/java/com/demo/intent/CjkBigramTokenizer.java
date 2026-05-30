package com.demo.intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CJK文字（ひらがな/カタカナ/漢字）についてオーバーラップするバイグラムを生成し、
 * ASCII英数字の連続ランは1トークンとして扱うトークナイザ。
 *
 * <p>アルゴリズム概要:
 * <ol>
 *   <li>入力を小文字化。</li>
 *   <li>文字を1文字ずつ走査し、CJK文字かASCII英数字か判定。</li>
 *   <li>ASCII英数字の連続ランは1トークンとして収集。</li>
 *   <li>CJK文字の連続ランは「オーバーラップバイグラム」を生成（連続長が1文字ならユニグラム）。</li>
 *   <li>空白・句読点等はトークン境界として捨てる。</li>
 *   <li>STOP_WORDS に含まれるトークンは除去（文末助動詞・接続助詞バイグラム等）。</li>
 * </ol>
 *
 * <h3>ストップワードについて</h3>
 * 「です」「すか」「ます」「てく」「てい」等は日本語疑問文・丁寧体の語尾として
 * ほぼあらゆる文に出現するため、意図識別に役立たないノイズとなる。
 * これらをストップワードとして除去することで、スコープ外クエリ（例: 天気を聞く文）が
 * 偶然一致して誤分類されるリスクを大幅に低減する。
 */
public class CjkBigramTokenizer implements Tokenizer {

    /**
     * 意図識別に寄与しない日本語頻出バイグラム（文末助動詞・助詞・接続等）。
     * 疑問文末「〜ですか」「〜ますか」や丁寧体語尾「〜ます」「〜です」、
     * 接続「〜てく(ださい)」「〜てい(ます)」等をカバー。
     */
    private static final Set<String> STOP_WORDS = Set.of(
        // 丁寧体語尾
        "です", "ます", "でし", "まし",
        // 疑問文末
        "すか", "しか",
        // 依頼・指示語尾
        "てく", "くだ", "ださ", "さい",
        // 接続・進行
        "てい", "ては", "ても",
        // 汎用接続助詞
        "ので", "から", "けど", "が、"
    );

    /**
     * 文字がCJK系（ひらがな・カタカナ・漢字・長音符）かどうかを判定する。
     */
    private static boolean isCjk(char c) {
        // ひらがな: U+3041-U+309F (゛゜含む ぀-ゟ は U+3000-U+309F で囲む)
        // カタカナ: U+30A0-U+30FF (゠-ヿ)
        // CJK統合漢字: U+4E00-U+9FFF
        // CJK拡張A: U+3400-U+4DBF
        // 長音符: ー U+30FC (カタカナ内に含まれるが明示的に含む)
        return (c >= '぀' && c <= 'ゟ') // ひらがな
            || (c >= '゠' && c <= 'ヿ') // カタカナ
            || (c >= '一' && c <= '鿿') // CJK統合漢字
            || (c >= '㐀' && c <= '䶿'); // CJK拡張A
    }

    /**
     * 文字がASCII英数字かどうかを判定する。
     */
    private static boolean isAsciiAlnum(char c) {
        return (c >= 'a' && c <= 'z')
            || (c >= '0' && c <= '9');
    }

    @Override
    public List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        String lower = text.toLowerCase();
        List<String> tokens = new ArrayList<>();

        // CJK連続バッファ
        StringBuilder cjkBuf = new StringBuilder();
        // ASCII英数字連続バッファ
        StringBuilder asciiAlnumBuf = new StringBuilder();

        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);

            if (isCjk(c)) {
                // ASCII英数字バッファをフラッシュ
                if (asciiAlnumBuf.length() > 0) {
                    tokens.add(asciiAlnumBuf.toString());
                    asciiAlnumBuf.setLength(0);
                }
                cjkBuf.append(c);
            } else if (isAsciiAlnum(c)) {
                // CJKバッファをフラッシュ
                if (cjkBuf.length() > 0) {
                    flushCjkBigrams(cjkBuf.toString(), tokens);
                    cjkBuf.setLength(0);
                }
                asciiAlnumBuf.append(c);
            } else {
                // 空白・句読点等: 両バッファをフラッシュ
                if (cjkBuf.length() > 0) {
                    flushCjkBigrams(cjkBuf.toString(), tokens);
                    cjkBuf.setLength(0);
                }
                if (asciiAlnumBuf.length() > 0) {
                    tokens.add(asciiAlnumBuf.toString());
                    asciiAlnumBuf.setLength(0);
                }
                // 区切り文字自体はトークンにしない
            }
        }

        // 末尾バッファをフラッシュ
        if (cjkBuf.length() > 0) {
            flushCjkBigrams(cjkBuf.toString(), tokens);
        }
        if (asciiAlnumBuf.length() > 0) {
            tokens.add(asciiAlnumBuf.toString());
        }

        return tokens;
    }

    /**
     * CJK連続文字列からオーバーラップバイグラムを生成。
     * 1文字ならユニグラム。2文字以上ならオーバーラップする2文字ペアを生成。
     * STOP_WORDS に含まれるトークンは追加しない。
     */
    private static void flushCjkBigrams(String cjkRun, List<String> tokens) {
        if (cjkRun.length() == 1) {
            if (!STOP_WORDS.contains(cjkRun)) {
                tokens.add(cjkRun);
            }
        } else {
            for (int i = 0; i < cjkRun.length() - 1; i++) {
                String bigram = cjkRun.substring(i, i + 2);
                if (!STOP_WORDS.contains(bigram)) {
                    tokens.add(bigram);
                }
            }
        }
    }
}
