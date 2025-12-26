# CodeX Development Continuity Ledger

## Goal (incl. success criteria)
Major CodeX Android IDE enhancement and cleanup:

### Phase 1: Provider Cleanup
1. **Remove Gemini Provider**: Delete GeminiProvider.kt and all references
2. **Remove OpenRouter Provider**: Delete OpenRouterProvider.kt and all references
3. **Cleanup UI**: Remove provider selection UI, model lists, settings for removed providers
4. **Keep ONLY DeepInfra**: This is the free reverse-engineered provider

### Phase 2: Asset Management Fixes
5. **Icon Library**: Add Font Awesome icons, complete Material Icons, add download-to-assets
6. **Font Manager**: Add download fonts to assets folder functionality
7. **All existing features**: Fix any incomplete implementations

### Phase 3: Git Enhancements
8. **Branch Deletion**: Long-press branch shows switch/delete options
9. **Git Init/Connect**: Initialize non-git projects, connect to existing repos
10. **New Git Features**: Stash, cherry-pick, interactive rebase UI, etc.

### Phase 4: New Features
11. **@ File Tagging**: Tag files/directories in chat input with @ syntax
12. **AI Code Edit**: Selection menu option to edit code with AI floating prompt
13. **Agent Selection Fix**: Fix preview activity agent mode
14. **Blank Template Fix**: Don't create index.html for blank projects
15. **Console Selection**: Make preview console text selectable

## Constraints/Assumptions
- Kotlin + Jetpack Compose codebase
- 500-1000 lines per file max (modular)
- Production-grade, fully functional implementations only
- No TODOs or placeholder code
- No italics in UI text
- Clean, professional, modern UI/UX

## Key Decisions
1. DeepInfra is the ONLY AI provider to keep (free, no API key needed)
2. Font Awesome icons will be loaded from CDN metadata/offline catalog
3. File tagging will use @ trigger with compact dropdown/popup
4. AI code edit will use floating bottom bar similar to agent mode in preview

## State

### Done:
- Codebase exploration and full architecture understanding
- All 111 Kotlin files mapped and analyzed

### Now:
- Phase 1: Remove Gemini and OpenRouter providers completely

### Next:
- Update AiProviderManager to only use DeepInfra
- Remove UI references to other providers
- Phase 2: Asset Management fixes
- Phase 3: Git enhancements
- Phase 4: New features

## Open Questions
- None currently

## Working Set
### Provider Files (to remove):
- `/app/src/main/java/com/codex/stormy/data/ai/GeminiProvider.kt`
- `/app/src/main/java/com/codex/stormy/data/ai/OpenRouterProvider.kt`

### Provider Files (to modify):
- `/app/src/main/java/com/codex/stormy/data/ai/AiProviderManager.kt`
- `/app/src/main/java/com/codex/stormy/data/ai/AiModels.kt`

### Asset Files:
- `/app/src/main/java/com/codex/stormy/ui/screens/editor/assets/IconLibraryDialog.kt`
- `/app/src/main/java/com/codex/stormy/ui/screens/editor/assets/FontManagerDialog.kt`

### Git Files:
- `/app/src/main/java/com/codex/stormy/ui/screens/git/GitDrawer.kt`
- `/app/src/main/java/com/codex/stormy/ui/screens/git/GitViewModel.kt`
- `/app/src/main/java/com/codex/stormy/data/git/GitRepository.kt`

### Chat/Editor Files:
- `/app/src/main/java/com/codex/stormy/ui/components/chat/ModernChatInput.kt`
- `/app/src/main/java/com/codex/stormy/ui/screens/editor/chat/ChatTab.kt`
- `/app/src/main/java/com/codex/stormy/ui/screens/editor/code/SoraCodeEditorView.kt`

### Preview Files:
- `/app/src/main/java/com/codex/stormy/ui/screens/preview/PreviewActivity.kt`

### Project Creation:
- `/app/src/main/java/com/codex/stormy/data/repository/ProjectTemplateGenerator.kt`
