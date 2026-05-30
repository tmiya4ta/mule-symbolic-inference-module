package com.demo.symbolic.inference;

import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.Configurations;
import org.mule.runtime.extension.api.annotation.dsl.xml.Xml;
import org.mule.sdk.api.annotation.JavaVersionSupport;

import static org.mule.sdk.api.meta.JavaVersion.JAVA_17;
import static org.mule.sdk.api.meta.JavaVersion.JAVA_21;

/**
 * Entry point of the symbolic (LLM-free) inference Mule module.
 *
 * Flows call it like: &lt;infer:classify text="#[payload]" /&gt;.
 * No LLM is used; intent-core performs TF-IDF cosine-similarity inference
 * to produce the intent and slots in a deterministic, in-process manner.
 *
 * intent-core is compiled with Java 17 (uses records), so the module only
 * supports Java 17 and 21 runtimes.
 */
@Extension(name = "Symbolic Inference")
@Xml(prefix = "infer")
@JavaVersionSupport({JAVA_17, JAVA_21})
@Configurations(InferenceConfiguration.class)
public class SymbolicInferenceExtension {
}
