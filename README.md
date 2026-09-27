# 🧠 AIBot - Self-Learning Android AI

A fully self-contained AI chatbot for Android ARMv7a 32-bit devices.
**No pretrained knowledge** — it starts blank and learns from you!

---

## Features

| Feature | Description |
|---|---|
| 🧠 Neural Network | Tiny transformer (10MB) built from scratch in Java |
| 🔮 NARS Reasoning | Non-Axiomatic Reasoning System for logical inference |
| 🌐 Web Search | DuckDuckGo search, no API key needed |
| 📄 Web Fetch | Read full web pages and learn from them |
| 📁 Dataset Loader | Load HuggingFace datasets (.json/.jsonl/.csv/.txt) |
| 📈 Self Learning | Updates its own weights from every conversation |
| 💾 Persistent | Saves weights, vocabulary, and beliefs to storage |

---

## Build via GitHub Actions

1. Fork or push this repo to GitHub
2. Go to **Actions** tab
3. Run **Build AIBot APK** workflow
4. Download APK from **Artifacts**

---

## Install on Device

```bash
adb install AIBot-debug.apk
```

Or download from GitHub Actions artifacts and install manually.

---

## Usage

### Chat Commands
```
!search <query>     - Search the web and learn
!fetch <url>        - Fetch a web page and learn
!datasets           - List available datasets
!load <filename>    - Train on a dataset
!stats              - Show brain statistics
!save               - Save brain manually
!reset              - Reset all knowledge
!help               - Show all commands
```

### Teaching the Bot
Just talk naturally:
```
You: "A car is a vehicle with 4 wheels"
Bot: "Understood! I learned: car is a vehicle (100% sure)"

You: "What is a car?"
Bot: "Car is a vehicle with 4 wheels."
```

### Loading HuggingFace Datasets
1. Download a dataset from HuggingFace as .jsonl or .csv
2. Put it in `/sdcard/AIBot/datasets/`
3. Use `!datasets` to list them
4. Use `!load filename.jsonl` to train

---

## File Storage

```
/sdcard/AIBot/
├── weights/
│   ├── model.bin      ← neural network weights
│   ├── vocab.txt      ← learned vocabulary
│   ├── beliefs.dat    ← NARS knowledge base
│   └── meta.txt       ← training metadata
├── datasets/          ← put HuggingFace files here
├── memory/
│   └── history.txt    ← conversation history
└── cache/             ← web fetch cache
```

---

## Architecture

```
User Input
    ↓
Tokenizer (word → token IDs)
    ↓
NARS Engine (check beliefs → reason → answer?)
    ↓ (if no direct answer)
Neural Network (generate response)
    ↓
Web Search (if needed)
    ↓
Response
    ↓
SelfLearner (update weights + beliefs)
    ↓
WeightManager (save to disk)
```

---

## Device Requirements

- Android 4.4+ (API 19+)
- ARMv7a 32-bit processor
- 2GB RAM minimum
- 200MB free storage
- Internet permission for web features

---

## NARS Reasoning Example

```
You teach: "Cat is animal"
You teach: "Animal needs food"
NARS derives: "Cat needs food" (81% confidence)

You ask: "Does cat need food?"
Bot: "Yes, cat needs food (81% sure) — I figured this out!"
```
