---
description: Takes an idea all the way to code - story-writer creates the story, planner breaks it into tasks, then implementation follows with TDD and review. Pauses at each stage for user approval.
argument-hint: "<idea in natural language>"
---

# /idea — from idea to code

Idea: $ARGUMENTS

Run the flow below **in order**. At every ⏸️, **stop and wait for explicit user approval** before continuing. If the user requests changes, redo the current stage before moving on. Talk to the user in the language they used for the idea.

## Stage 1 — Story (PO)
1. Invoke the **story-writer** agent with the idea above.
2. If it returns **Blocking questions**, show them to the user, collect answers, and invoke `story-writer` again with idea + answers.
3. Show the user: one-line story, acceptance criteria, out of scope, and **assumptions**.

⏸️ **Ask:** "Is the story correct? Can I send it to the planner?"

If it is an epic (multiple stories), ask which story to plan first and proceed one at a time.

## Stage 2 — Plan (tech lead)
1. Invoke the **planner** agent with the contents of `docs/stories/<slug>.md`. Instruct: "Every acceptance criterion must be covered by at least one step and at least one test in the Testing Strategy. Add nothing listed under Out of Scope."
2. Save the plan to `docs/plans/<slug>.md`.
3. Verify yourself: does every acceptance criterion appear in the plan? If not, ask the planner to fix it.

⏸️ **Ask:** "Plan approved? Can I implement?"

## Stage 3 — Implementation
Follow the plan phases one at a time:
1. **Tests first** — use the **tdd-guide** agent: acceptance criteria (Given/When/Then) become failing tests.
2. **Implement** until tests pass.
3. If the build breaks, use the project's stack-specific `*-build-resolver`.
4. Run the test suite at the end of each phase.

## Stage 4 — Review
1. **code-reviewer** + the project's language reviewer (e.g. `typescript-reviewer`, `python-reviewer`).
2. **security-reviewer** if the change touches user input, auth, APIs, or sensitive data.
3. Fix CRITICAL/HIGH findings.
4. Set the story `Status` to `done` and tick the Definition of Done.

## Stage 5 — Wrap-up
Show the user a short summary: acceptance criteria met (✅/❌), files changed, tests added.

⏸️ **Ask** whether to create the commit (via `commit-reviewer`).
