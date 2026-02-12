# Projects Skill

Autonomous background project management for OpenClaw agents. Enables long-running projects with iterative delivery, parallel sub-agent execution, and structured check-ins.

## Installation

1. Copy the `projects/` skill directory into your OpenClaw skills folder:
   ```
   cp -r projects/ ~/.openclaw/skills/projects/
   ```
   Or if using the shared skills directory:
   ```
   cp -r projects/ /path/to/openclaw/skills/projects/
   ```

2. Install [beads](https://github.com/openclaw/beads) (`bd`) for task tracking:
   ```
   # Follow beads installation instructions
   bd --version
   ```

3. Create the projects directory:
   ```
   mkdir -p ~/projects
   ```

4. Create the registry:
   ```
   cat > ~/projects/registry.md << 'EOF'
   # Projects

   | Slug | Status | Priority | Path |
   |------|--------|----------|------|
   EOF
   ```

5. (Optional) Set up a cron job for autonomous work sessions:
   ```
   # Via OpenClaw cron tool — see SKILL.md for recommended config
   ```

## Requirements

- **OpenClaw** with cron and sessions support
- **beads** (`bd`) CLI for task tracking
- **git** for version control
- **Discord** channel (optional, for check-in notifications)

## Usage

### Create a Project

```bash
mkdir -p ~/projects/my-project
cd ~/projects/my-project
git init
bd init
```

Then write `PROJECT.md`, create `iterations/001/ITERATION.md`, add stories with `bd create`, and register in `~/projects/registry.md`.

See `SKILL.md` for the full workflow.

## License

Part of the OpenClaw skill ecosystem.
