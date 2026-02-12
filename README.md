# 🧒 GnomeGPT

**Your OSRS companion — like having a maxed friend who actually answers questions.**

A RuneLite plugin that gives you an AI chat companion right in your sidebar. Ask about quests, items, bosses, moneymaking, skilling — anything OSRS. It pulls from the OSRS Wiki automatically so answers are accurate and up-to-date.

![GnomeGPT](src/main/resources/gnome_child.png)

## Features

- 💬 **Natural chat** — talks like a friend, not a textbook
- 📚 **Wiki-powered** — automatically searches the OSRS Wiki for context
- 💰 **GE prices** — `/price dragon bones` for instant price checks
- 🔍 **Quick wiki lookups** — `/wiki abyssal whip` for fast info
- 🔗 **Clickable wiki links** — mentions items in `[[brackets]]` that link to the wiki
- 🤖 **Multiple AI providers** — OpenAI, Anthropic, or Ollama (free, local)
- 🔒 **Your key stays local** — API key stored in RuneLite config, only sent to your chosen provider

## Setup

### 1. Install the plugin
*(Coming to the Plugin Hub — for now, build from source)*

### 2. Get an API key (pick one)

| Provider | Cost | Get a key |
|----------|------|-----------|
| **OpenAI** (recommended) | ~$0.01/conversation | [platform.openai.com/api-keys](https://platform.openai.com/api-keys) |
| **Anthropic** | ~$0.01/conversation | [console.anthropic.com](https://console.anthropic.com/settings/keys) |
| **Ollama** (free!) | Free, runs locally | [ollama.com](https://ollama.com) — then `ollama pull llama3.2` |

### 3. Configure in RuneLite
1. Open RuneLite settings (wrench icon)
2. Find **GnomeGPT** in the plugin list
3. Pick your provider and paste your API key
4. Done! Click the gnome child icon in the sidebar to start chatting

## Commands

| Command | What it does |
|---------|-------------|
| `/price <item>` | GE price lookup |
| `/wiki <topic>` | Quick wiki summary |
| `/quest <name>` | Quest info |
| `/monster <name>` | Monster info |
| `/clear` | Clear chat history |
| `/help` | Show all commands |

Or just type normally and chat!

## Building from Source

```bash
# Clone
git clone https://github.com/gnomegpt/gnomegpt.git
cd gnomegpt

# Build
./gradlew build

# Test in RuneLite dev mode
./gradlew run
```

Requires Java 11+.

## How It Works

1. You type a question
2. GnomeGPT searches the OSRS Wiki for relevant pages
3. Wiki context + your question go to the AI
4. You get a concise, practical answer with wiki links

No data leaves your machine except API calls to your chosen provider (OpenAI/Anthropic) or your local Ollama instance. No telemetry, no tracking.

## Recommended Models

- **Budget:** `gpt-4o-mini` (OpenAI) — fast, cheap, good enough for most questions
- **Quality:** `gpt-4o` (OpenAI) or `claude-sonnet-4-20250514` (Anthropic) — better reasoning
- **Free:** `llama3.2` (Ollama) — runs on your machine, no API key needed

## License

BSD-2-Clause (same as RuneLite)
