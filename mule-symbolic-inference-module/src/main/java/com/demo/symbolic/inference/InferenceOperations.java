package com.demo.symbolic.inference;

import com.demo.intent.IntentResult;

import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.MediaType;

import static org.mule.runtime.extension.api.annotation.param.MediaType.APPLICATION_JSON;

/**
 * Symbolic inference operations.
 */
public class InferenceOperations {

    /**
     * Infers the intent and slots from a natural-language utterance.
     *
     * Returns a JSON document so it can be returned directly by an HTTP flow
     * or parsed with DataWeave's read():
     * { "intent": "...", "confidence": 0.0, "ranking": {..}, "slots": {..} }.
     *
     * A JSON String is returned (rather than a Map) because the Mule SDK
     * requires an OutputResolver for Object/Map return types.
     *
     * @param config the flow configuration holding the initialised classifier
     * @param text   the utterance to classify
     */
    @MediaType(value = APPLICATION_JSON, strict = false)
    public String classify(@Config InferenceConfiguration config, String text) {
        IntentResult result = config.getClassifier().classify(text == null ? "" : text);
        return result.toJson();
    }
}
