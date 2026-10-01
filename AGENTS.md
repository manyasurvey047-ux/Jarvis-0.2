# Project Rules & Permanent Constraints

## Voice & Gemini Live Core Protection (CRITICAL)
- **Do NOT modify, break, or refactor the core voice pipeline**, WebSocket audio streaming, Gemini Live API session (`LiveSessionManager`), audio recorder (`AudioRecord`), audio track (`AudioTrack`), or Foreground Service (`ZoyaForegroundService`) without explicit prior user confirmation.
- **Voice quality, speech recognition, and low-latency audio response** are fully calibrated and must remain intact.
- Any future changes or feature additions must build on top of existing capabilities without altering working live voice sessions or tool calling protocols unless explicitly requested.
