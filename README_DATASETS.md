# AIBot Dataset Guide

Put all datasets in the folder shown in app → Menu → Stats → Storage path

---

## Loading Order (Do This Exactly)

### Step 1 — Basic Chat (do first, ~500KB)
**HuggingFaceTB/everyday-conversations-llama3.1-2k**
- Download: https://huggingface.co/datasets/HuggingFaceTB/everyday-conversations-llama3.1-2k/resolve/main/data/train.jsonl
- Save as: `everyday_chat.jsonl`
- Teaches: hello, goodbye, basic questions, who are you
- Train time: ~5 min on ARMv7a

### Step 2 — Whisper Personality (built in)
- Already in app as `whisper_personality.jsonl`
- Copy from: `app/src/main/res/raw/whisper_personality.jsonl`
- Teaches: unique calm voice, emotional depth
- Train time: ~2 min

### Step 3 — Persona Chat (~8MB)
**Cynaptics/persona-chat**
- Download: https://huggingface.co/datasets/Cynaptics/persona-chat/resolve/main/data/train.jsonl
- Save as: `persona_chat.jsonl`
- Teaches: personality, consistent character, personal facts
- Train time: ~20 min

### Step 4 — Reasoning (~3MB)
**openai/gsm8k**
- Download: https://huggingface.co/datasets/openai/gsm8k/resolve/main/main/train.jsonl
- Save as: `gsm8k_reasoning.jsonl`
- Teaches: step by step thinking, math, logic
- Train time: ~10 min

### Step 5 — Deep Persona (~15MB, optional)
**google/Synthetic-Persona-Chat**
- Download: https://huggingface.co/datasets/google/Synthetic-Persona-Chat/resolve/main/train.json
- Save as: `synthetic_persona.json`
- Teaches: rich persona depth, long conversations
- Train time: ~45 min

---

## Total After All Datasets

```
Vocab words:    ~6000-8000
NARS beliefs:   ~2000+
Train steps:    ~50,000+
Personality:    Whisper style — calm, thoughtful, real
```

---

## What Each Dataset Fixes

| Problem | Dataset That Fixes It |
|---|---|
| Says nothing on "hello" | everyday_chat.jsonl |
| Robotic tone | whisper_personality.jsonl |
| No personality | persona_chat.jsonl |
| Can't reason | gsm8k_reasoning.jsonl |
| Shallow responses | synthetic_persona.json |

---

## Formats Supported

| Format | Example datasets |
|---|---|
| .jsonl with input/output | gsm8k, custom |
| .jsonl with messages[] | everyday-conversations |
| .jsonl with dialogue[] | persona-chat |
| .json array | synthetic-persona-chat |
| .txt plain text | any text file |
| .csv with columns | any spreadsheet export |
