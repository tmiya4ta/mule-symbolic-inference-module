package com.demo.intent;

import java.util.List;

/**
 * テキストをトークンのリストに変換するインタフェース。
 */
public interface Tokenizer {
    List<String> tokenize(String text);
}
