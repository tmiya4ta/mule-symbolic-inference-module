package com.demo.intent;

import java.util.Map;

/**
 * テキストからスロット（名前付きエンティティ・引数）を抽出するインタフェース。
 */
public interface SlotExtractor {
    /**
     * @param text 入力テキスト
     * @return スロット名 → 抽出値のマップ
     */
    Map<String, String> extract(String text);
}
