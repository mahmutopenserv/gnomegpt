package com.gnomegpt.llm;

/**
 * Callback for streaming LLM responses.
 */
public interface StreamCallback
{
    /** Called with each text chunk as it arrives. */
    void onToken(String token);

    /** Called when the full response is complete. */
    void onComplete(String fullResponse);

    /** Called on error. */
    void onError(String error);
}
