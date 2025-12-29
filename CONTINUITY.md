# Continuity Ledger

## Goal (incl. success criteria)
Major CodeX Stormy app enhancement with:
1. Fix Git index lock crash (LockFailedException)
2. Fix Git push not working properly
3. Change project storage to external: storage/emulated/0/CodeX/Projects/
4. Fix image viewer in code editor (shows 0x0)
5. Complete UI/UX overhaul - modern black/white/grey professional theme
6. Remove API section from Settings
7. Make Stormy agent memory system functional
8. Make preview console text selectable
9. Implement framework preview support (React, Vue, Next.js, etc.)
10. Add advanced AI tools and enhance agent capabilities

**Success criteria**: All features working, production-grade quality, no TODOs left

## Constraints/Assumptions
- Files max 500-1000 lines (modularization)
- Production-grade code only
- No TODOs, placeholders, or incomplete implementations
- Android app using Kotlin/Compose
- External storage requires proper permissions on Android

## Key decisions
- Using modern design system with black/white/grey palette
- Storage path: storage/emulated/0/CodeX/Projects/(project name)
- Memory system will use improved context integration
- Framework preview will use embedded dev server approach

## State

### Done
- (none yet)

### Now
- Starting with Git crash fix (index.lock handling)

### Next
- Git push fix
- Storage location change
- Image viewer fix
- UI theme overhaul
- Settings cleanup
- Memory system enhancement
- Console text selection
- Framework preview
- Agent tools enhancement

## Open questions (UNCONFIRMED if needed)
- None currently

## Working set (files/ids/commands)
- `app/src/main/java/com/codex/stormy/data/git/GitRepository.kt`
- `app/src/main/java/com/codex/stormy/data/git/GitManager.kt`
- `app/src/main/java/com/codex/stormy/data/repository/ProjectRepository.kt`
- `app/src/main/java/com/codex/stormy/ui/theme/Color.kt`
- `app/src/main/java/com/codex/stormy/ui/theme/Theme.kt`
- `app/src/main/java/com/codex/stormy/data/ai/tools/MemoryStorage.kt`
