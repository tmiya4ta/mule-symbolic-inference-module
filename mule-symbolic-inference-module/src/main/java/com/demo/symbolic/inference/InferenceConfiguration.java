package com.demo.symbolic.inference;

import com.demo.intent.CjkBigramTokenizer;
import com.demo.intent.IntentClassifier;
import com.demo.intent.IntentConfig;
import com.demo.intent.IntentConfigLoader;
import com.demo.intent.Tokenizer;

import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.nio.file.Path;

/**
 * Inference configuration. Receives the dictionary location and the threshold,
 * and builds the {@link IntentClassifier} exactly once at startup.
 *
 * When {@code dictionaryDir} is set, the directory's intents.tsv / slots.tsv /
 * intent.properties are loaded. Otherwise the default dictionary bundled on the
 * module classpath is used.
 */
@Operations(InferenceOperations.class)
public class InferenceConfiguration implements Initialisable {

    @Parameter
    @Optional
    @Summary("Dictionary directory holding intents.tsv/slots.tsv/intent.properties. "
            + "If empty, the bundled default dictionary is used.")
    private String dictionaryDir;

    @Parameter
    @Optional
    @Summary("Overrides the UNKNOWN threshold. If empty, the value from "
            + "intent.properties is used (default 0.15).")
    private Double thresholdOverride;

    private volatile IntentClassifier classifier;

    @Override
    public void initialise() throws InitialisationException {
        try {
            IntentConfig cfg;
            if (dictionaryDir != null && !dictionaryDir.isBlank()) {
                cfg = IntentConfigLoader.loadFromDir(Path.of(dictionaryDir));
            } else {
                cfg = IntentConfigLoader.loadFromClasspath(
                        "/intents.tsv", "/slots.tsv", "/intent.properties");
            }
            if (thresholdOverride != null) {
                cfg = new IntentConfig(cfg.getExamples(), cfg.getSlotPatterns(), thresholdOverride);
            }
            Tokenizer tokenizer = new CjkBigramTokenizer();
            this.classifier = IntentClassifier.fromConfig(cfg, tokenizer);
        } catch (Exception e) {
            throw new InitialisationException(e, this);
        }
    }

    IntentClassifier getClassifier() {
        return classifier;
    }
}
