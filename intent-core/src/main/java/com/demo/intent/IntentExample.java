package com.demo.intent;

/**
 * 意図のサンプル例を表すレコード。
 *
 * @param intent 意図ラベル（例: "ORDER_STATUS", "CANCEL"）
 * @param text   サンプルテキスト
 */
public record IntentExample(String intent, String text) {
}
