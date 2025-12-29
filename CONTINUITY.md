# Continuity Ledger

## Goal (incl. success criteria)
Major CodeX Stormy IDE Enhancement Project:

1. **Stormy Agent Enhancement** - Production-grade AI coding agent
   - Fix all tool call errors (read, write, etc.)
   - Add advanced new tools
   - Handle all edge cases
   - Make it a perfect coding agent

2. **Preview System Overhaul** - Full framework support
   - Support all templates (React, Vue, Next.js, Svelte)
   - Remove CORS restrictions
   - Enable full JS execution
   - Project-type-aware compilation/building
   - Fix desktop mode
   - Production-grade preview for all templates

3. **UI/UX Improvements**
   - Reduce user message font size by 1-2sp
   - Implement expandable/collapsible text ellipsis for long messages
   - Fix lag when sending prompts
   - Fix Stormy stopping unexpectedly without indication

4. **Performance Optimizations**
   - Address lag issues during agent operations
   - Optimize template system
   - Optimize memory system

**Success criteria**: All features working flawlessly, production-grade quality, no TODOs or incomplete implementations

## Constraints/Assumptions
- Files max 500-1000 lines (modularization required)
- Production-grade code only - highest quality
- No TODOs, placeholders, or incomplete implementations
- Kotlin/Jetpack Compose Android app
- Android apps rely on GitHub CI/workflows (no local building)
- Modular architecture required

## Key decisions
- UNCONFIRMED - Need to explore current tool implementations
- UNCONFIRMED - Need to analyze preview system architecture
- UNCONFIRMED - Need to identify specific tool errors

## State

### Done
- Initial codebase exploration complete
- Identified key file locations

### Now
- Deep analysis of current tool implementations and issues
- Understanding preview system limitations

### Next
1. Fix tool call errors in ToolExecutor.kt
2. Enhance existing tools with better error handling
3. Add advanced new tools
4. Overhaul preview system for framework support
5. Fix UI issues (font size, ellipsis)
6. Fix performance/lag issues
7. Fix agent stopping unexpectedly

## Open questions (UNCONFIRMED if needed)
- What specific tool errors occur? Need to analyze code
- What causes agent to stop unexpectedly?
- What's blocking framework previews?
- What causes the lag during prompt processing?

## Working set (files/ids/commands)
- `app/src/main/java/com/codex/stormy/data/ai/tools/ToolExecutor.kt` - Tool execution
- `app/src/main/java/com/codex/stormy/data/ai/tools/ToolDefinitions.kt` - Tool definitions
- `app/src/main/java/com/codex/stormy/ui/screens/preview/PreviewActivity.kt` - Preview system
- `app/src/main/java/com/codex/stormy/ui/components/message/` - Message UI
- `app/src/main/java/com/codex/stormy/data/repository/AiRepository.kt` - AI streaming
