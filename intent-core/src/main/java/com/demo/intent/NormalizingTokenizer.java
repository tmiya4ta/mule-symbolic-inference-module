package com.demo.intent;

import java.util.List;

/**
 * 同義語正規化を前段に挟む {@link Tokenizer} デコレータ。
 *
 * <p>{@link SynonymNormalizer} でテキストを正規化してから、ベースの
 * トークナイザ（通常 {@link CjkBigramTokenizer}）に委譲する。
 * 例文側・クエリ側の双方が同一の正規化を通るため、言い換えが canonical 形に揃う。
 */
public class NormalizingTokenizer implements Tokenizer {

    private final Tokenizer base;
    private final SynonymNormalizer normalizer;

    public NormalizingTokenizer(Tokenizer base, SynonymNormalizer normalizer) {
        this.base = base;
        this.normalizer = normalizer;
    }

    @Override
    public List<String> tokenize(String text) {
        return base.tokenize(normalizer.normalize(text));
    }
}
