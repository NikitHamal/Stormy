# CodeX Development Continuity Ledger

## Goal (incl. success criteria)
Comprehensive fix and feature implementation for CodeX Android IDE:

### Critical Fixes:
1. **Provider Integration**: Fix Gemini and OpenRouter providers (return errors, only DeepInfra works)
2. **Chat Tab Tool Calls**: Fix thinking/answer content bleeding into tool call blocks
3. **Git Lock File Crash**: Fix crash when switching branches due to stale lock files
4. **Agent Selection Mode**: Fix preview activity agent selection not working

### UI/UX Fixes:
5. **Project Deletion**: Remove selection-based deletion (keep swipe-to-delete only)
6. **Blank Template**: Don't create default index.html for blank projects
7. **Console Text**: Make preview console output selectable
8. **Diff View**: Implement proper diff view for write/patch tool calls

### New Features:
9. **Icon Library**: Complete Font Awesome/Material Icons with download to assets
10. **Font Manager**: Add download fonts to assets feature
11. **Git Branch Deletion**: Long-press to delete branches
12. **Git Initialization**: Init/connect projects to GitHub repos
13. **@ File Tagging**: Tag files/directories in chat with @ syntax
14. **AI Code Edit**: Add AI edit option in code selection menu with floating prompt bar

## Constraints/Assumptions
- Kotlin + Jetpack Compose codebase
- 500-1000 lines per file max (modular)
- Production-grade, fully functional implementations only
- No TODOs or placeholder code

## Key Decisions
1. Provider errors: Gemini uses different API format than OpenAI-compatible providers
2. Tool call parsing: Need robust regex-based content block separation
3. Git lock fix: Clean up .lock files before operations, use proper try-finally

## State

### Done:
- Previous fix: HomeScreen.kt AnimatedVisibility RowScope issue resolved
- Codebase exploration and architecture understanding

### Now:
- Starting with Gemini and OpenRouter provider fixes

### Next:
- Chat tab tool call display fix
- Project deletion cleanup
- Continue with remaining items

## Open Questions
- UNCONFIRMED: Exact error messages from Gemini/OpenRouter
- UNCONFIRMED: Current tool call parsing logic details

## Working Set
- `/app/src/main/java/com/codex/stormy/data/ai/GeminiProvider.kt`
- `/app/src/main/java/com/codex/stormy/data/ai/OpenRouterProvider.kt`
- `/app/src/main/java/com/codex/stormy/data/ai/AiProviderManager.kt`
- `/app/src/main/java/com/codex/stormy/ui/screens/home/HomeScreen.kt`
- `/app/src/main/java/com/codex/stormy/ui/components/message/AiMessageContent.kt`
- `/app/src/main/java/com/codex/stormy/data/git/GitRepository.kt`
