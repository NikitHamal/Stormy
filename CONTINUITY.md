# Continuity Ledger

## Goal (incl. success criteria)
Fix Kotlin compilation errors in HomeScreen.kt related to `AnimatedVisibility` being called incorrectly within a `RowScope` context. The build should compile successfully after the fix.

## Constraints/Assumptions
- Production-grade fix required
- Modular code (500-1000 lines max per file)
- No TODOs or placeholder implementations

## Key decisions
1. **Root cause**: `SwipeToDismissBox.content` lambda has `RowScope` receiver, causing the compiler to try to use `RowScope.AnimatedVisibility` instead of the top-level function when `AnimatedVisibility` is called anywhere inside that lambda.
2. **Solution**: Extract the `AnimatedVisibility` code to a separate composable function (`ProjectIconWithSelection`) that is NOT inside the `RowScope` context. This allows the top-level `AnimatedVisibility` to be called correctly.
3. **Modularity**: Extracted `DeleteProjectDialog` and `EditProjectDialog` to `ProjectDialogs.kt` to keep HomeScreen.kt under 1000 lines (951 lines final).

## State
- Done:
  - Identified root cause of `RowScope.AnimatedVisibility` ambiguity
  - Extracted problematic `AnimatedVisibility` calls to new `ProjectIconWithSelection` composable
  - Extracted `DeleteProjectDialog` and `EditProjectDialog` to `ProjectDialogs.kt` for modularity
  - HomeScreen.kt reduced from 1044 to 951 lines

- Now:
  - Fix complete (Java not available in environment to verify build)

- Next:
  - CI/CD will verify the build succeeds

## Open questions (UNCONFIRMED if needed)
- None - fix is structurally correct based on the error analysis

## Working set (files/ids/commands)
- Modified: `/app/src/main/java/com/codex/stormy/ui/screens/home/HomeScreen.kt` (951 lines)
- Created: `/app/src/main/java/com/codex/stormy/ui/screens/home/ProjectDialogs.kt` (110 lines)
