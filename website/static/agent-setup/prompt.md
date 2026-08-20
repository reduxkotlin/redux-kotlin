# Set up a coding agent for ReduxKotlin

Set up the current repository so its coding agents can work effectively with ReduxKotlin. Follow
only the steps that apply, preserve all existing project instructions, and do not add a ReduxKotlin
dependency or install tooling unless the user separately asked for that.

## 1. Inspect the repository

- Find the repository root and read its existing `AGENTS.md`, `CLAUDE.md`, or other agent guidance.
- Check whether the project already uses `org.reduxkotlin` dependencies and which version it pins.
- Treat repository-local instructions as authoritative when they are more specific than this setup.

## 2. Install the cross-agent project guide

Fetch the canonical guide from:

https://reduxkotlin.org/agent-setup/AGENTS.md

- If the repository has no root `AGENTS.md`, save the fetched guide there.
- If a root `AGENTS.md` already exists, do not replace it. Save the fetched guide as
  `docs/agent/redux-kotlin.md`, then add a short, clearly labeled ReduxKotlin section to the existing
  root guide telling agents to read that file for ReduxKotlin work.
- Preserve the fetched guide's links. They route agents to focused references instead of loading the
  entire documentation set into context.

## 3. Install the optional Agent Skill when supported

Fetch the standalone skill from:

https://reduxkotlin.org/agent-setup/SKILL.md

Install it only if the current coding agent supports project-scoped Agent Skills:

- Agent Skills / Codex convention: `.agents/skills/redux-kotlin/SKILL.md`
- Claude Code convention: `.claude/skills/redux-kotlin/SKILL.md`

Do not invent a skill directory for clients that do not support skills. The `AGENTS.md` setup is the
portable baseline and is sufficient on its own.

## 4. Use the right integration path

- For an existing ReduxKotlin project, preserve its pinned dependency versions unless the user asks
  for an upgrade.
- For a new integration, consult the current installation page and Maven Central before choosing a
  release: https://reduxkotlin.org/introduction/getting-started
- For action/state debugging or golden UI work, use the unified `rk` CLI (`rk devtools` and
  `rk snapshot`). Do not install the superseded standalone `rk-devtools` command.
- Read only the per-concern guide that matches the task before changing application code.

## 5. Verify and report

- Confirm every created file is reachable from the repository's root agent instructions.
- Do not run package-manager installs or change application code when the user asked only for agent
  onboarding.
- Summarize the files created or updated, the detected ReduxKotlin version (if any), and the next
  relevant command or guide.
